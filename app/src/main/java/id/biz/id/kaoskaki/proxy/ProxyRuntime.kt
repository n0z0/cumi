package id.biz.id.kaoskaki.proxy

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

sealed interface ProxyStatus {
    data object Stopped : ProxyStatus
    data object Starting : ProxyStatus
    data class Running(val port: Int, val authEnabled: Boolean, val startedAt: Long) : ProxyStatus
    data class Error(val message: String) : ProxyStatus
}

data class TrafficStats(
    val activeConnections: Int = 0,
    val totalConnections: Long = 0,
    val bytesUp: Long = 0,
    val bytesDown: Long = 0,
    val speedUp: Long = 0,
    val speedDown: Long = 0,
)

enum class LogLevel { INFO, SUCCESS, WARNING, ERROR }

data class LogEntry(val id: Long, val timestamp: Long, val level: LogLevel, val message: String)

/**
 * Process-wide state shared between [id.biz.id.kaoskaki.service.ProxyService] and the UI.
 * Hot counters are plain atomics; [publish] snapshots them into [stats] once per tick.
 */
object ProxyRuntime : Socks5Server.Events {
    private const val MAX_LOGS = 500

    private val _status = MutableStateFlow<ProxyStatus>(ProxyStatus.Stopped)
    val status: StateFlow<ProxyStatus> = _status.asStateFlow()

    private val _stats = MutableStateFlow(TrafficStats())
    val stats: StateFlow<TrafficStats> = _stats.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val active = AtomicInteger()
    private val total = AtomicLong()
    private val up = AtomicLong()
    private val down = AtomicLong()
    private val logIds = AtomicLong()

    private var lastUp = 0L
    private var lastDown = 0L
    private var lastTick = 0L

    fun setStatus(status: ProxyStatus) {
        _status.value = status
    }

    @Synchronized
    fun resetCounters() {
        active.set(0)
        total.set(0)
        up.set(0)
        down.set(0)
        lastUp = 0
        lastDown = 0
        lastTick = SystemClock.elapsedRealtime()
        _stats.value = TrafficStats()
    }

    /** Snapshots counters and computes per-second throughput since the previous call. */
    @Synchronized
    fun publish(idle: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val u = up.get()
        val d = down.get()
        val elapsed = (now - lastTick).coerceAtLeast(1)
        _stats.value = TrafficStats(
            activeConnections = active.get(),
            totalConnections = total.get(),
            bytesUp = u,
            bytesDown = d,
            speedUp = if (idle) 0 else (u - lastUp) * 1000 / elapsed,
            speedDown = if (idle) 0 else (d - lastDown) * 1000 / elapsed,
        )
        lastUp = u
        lastDown = d
        lastTick = now
    }

    fun log(level: LogLevel, message: String) {
        val entry = LogEntry(logIds.incrementAndGet(), System.currentTimeMillis(), level, message)
        synchronized(_logs) {
            val current = _logs.value
            _logs.value = if (current.size >= MAX_LOGS) current.drop(1) + entry else current + entry
        }
    }

    fun clearLogs() {
        synchronized(_logs) { _logs.value = emptyList() }
    }

    override fun onConnectionOpened() {
        active.incrementAndGet()
        total.incrementAndGet()
    }

    override fun onConnectionClosed(client: String, target: String, bytesUp: Long, bytesDown: Long, durationMs: Long) {
        active.decrementAndGet()
        log(
            LogLevel.SUCCESS,
            "$client → $target  ↑${Formatters.bytes(bytesUp)} ↓${Formatters.bytes(bytesDown)}  ${Formatters.duration(durationMs)}",
        )
    }

    override fun onConnectionFailed(client: String, target: String?, reason: String) {
        val where = target?.let { "$client → $it" } ?: client
        log(LogLevel.WARNING, "$where  $reason")
    }

    override fun onTraffic(bytesUp: Long, bytesDown: Long) {
        if (bytesUp != 0L) up.addAndGet(bytesUp)
        if (bytesDown != 0L) down.addAndGet(bytesDown)
    }
}
