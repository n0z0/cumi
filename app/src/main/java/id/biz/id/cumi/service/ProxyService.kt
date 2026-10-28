package id.biz.id.cumi.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import id.biz.id.cumi.KaosKakiApplication
import id.biz.id.cumi.MainActivity
import id.biz.id.cumi.R
import id.biz.id.cumi.data.SettingsRepository
import id.biz.id.cumi.proxy.LogLevel
import id.biz.id.cumi.proxy.NetworkInfo
import id.biz.id.cumi.proxy.ProxyRuntime
import id.biz.id.cumi.proxy.ProxyStatus
import id.biz.id.cumi.proxy.Socks5Server
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.concurrent.thread

class ProxyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var server: Socks5Server? = null
    private var startJob: Job? = null
    private var tickerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastNotificationText: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        // A null intent means the system restarted us after a kill: resume serving.
        startProxy()
        return START_STICKY
    }

    override fun onDestroy() {
        releaseResources()
        if (ProxyRuntime.status.value !is ProxyStatus.Error) ProxyRuntime.setStatus(ProxyStatus.Stopped)
        scope.cancel()
        super.onDestroy()
    }

    private fun startProxy() {
        if (server != null || startJob?.isActive == true) return
        promoteToForeground(buildNotification(getString(R.string.notification_starting)))
        ProxyRuntime.setStatus(ProxyStatus.Starting)

        startJob = scope.launch {
            val settings = SettingsRepository(applicationContext).settings.first()
            if (!settings.authValid) {
                fail(getString(R.string.error_auth_incomplete))
                return@launch
            }
            val credentials = if (settings.authEnabled) {
                Socks5Server.Credentials(settings.username, settings.password)
            } else {
                null
            }
            val instance = Socks5Server(settings.port, credentials, ProxyRuntime)
            try {
                withContext(Dispatchers.IO) { instance.start() }
            } catch (e: IOException) {
                fail(getString(R.string.error_bind_failed, settings.port, e.message.orEmpty()))
                return@launch
            } catch (e: CancellationException) {
                // Stopped while binding: don't leave the port open behind us.
                thread(name = "socks-shutdown") { instance.stop() }
                throw e
            }

            server = instance
            if (settings.keepAwake) acquireLocks()
            ProxyRuntime.resetCounters()
            ProxyRuntime.setStatus(
                ProxyStatus.Running(settings.port, settings.authEnabled, SystemClock.elapsedRealtime())
            )
            ProxyRuntime.log(
                LogLevel.INFO,
                getString(
                    R.string.log_started,
                    settings.port,
                    getString(if (settings.authEnabled) R.string.auth_on else R.string.auth_off),
                ),
            )
            tickerJob = scope.launch { tick(settings.port) }
        }
    }

    private suspend fun tick(port: Int) {
        while (scope.isActive) {
            ProxyRuntime.publish()
            val address = withContext(Dispatchers.IO) { NetworkInfo.lanAddresses().firstOrNull()?.address }
            val active = ProxyRuntime.stats.value.activeConnections
            val endpoint = address?.let { "$it:$port" } ?: getString(R.string.notification_no_network, port)
            val text = resources.getQuantityString(R.plurals.notification_running, active, endpoint, active)
            if (text != lastNotificationText) {
                lastNotificationText = text
                notifyIfAllowed(buildNotification(text))
            }
            delay(1000)
        }
    }

    private fun fail(message: String) {
        ProxyRuntime.log(LogLevel.ERROR, message)
        ProxyRuntime.setStatus(ProxyStatus.Error(message))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun shutdown() {
        val wasRunning = server != null
        releaseResources()
        ProxyRuntime.publish(idle = true)
        ProxyRuntime.setStatus(ProxyStatus.Stopped)
        if (wasRunning) ProxyRuntime.log(LogLevel.INFO, getString(R.string.log_stopped))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseResources() {
        startJob?.cancel()
        tickerJob?.cancel()
        startJob = null
        tickerJob = null
        // Closing sockets can block briefly, keep it off the main thread.
        server?.let { s -> thread(name = "socks-shutdown") { s.stop() } }
        server = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        wifiLock = null
        lastNotificationText = null
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        wakeLock = getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KaosKaki:proxy")
            ?.apply { acquire() }
        @Suppress("DEPRECATION")
        wifiLock = applicationContext.getSystemService<WifiManager>()
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "KaosKaki:proxy")
            ?.apply { acquire() }
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyIfAllowed(notification: Notification) {
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, ProxyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, KaosKakiApplication.CHANNEL_PROXY)
            .setSmallIcon(R.drawable.ic_stat_proxy)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(ContextCompat.getColor(this, R.color.brand_primary))
            .addAction(R.drawable.ic_stop, getString(R.string.action_stop), stop)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1080
        private const val ACTION_START = "id.biz.id.cumi.action.START"
        private const val ACTION_STOP = "id.biz.id.cumi.action.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, ProxyService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ProxyService::class.java).setAction(ACTION_STOP))
        }
    }
}
