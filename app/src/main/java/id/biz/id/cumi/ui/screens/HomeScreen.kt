package id.biz.id.cumi.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.biz.id.cumi.R
import id.biz.id.cumi.data.ProxySettings
import id.biz.id.cumi.proxy.Formatters
import id.biz.id.cumi.proxy.InterfaceKind
import id.biz.id.cumi.proxy.LanAddress
import id.biz.id.cumi.proxy.ProxyStatus
import id.biz.id.cumi.proxy.TrafficStats
import id.biz.id.cumi.ui.components.PowerButton
import id.biz.id.cumi.ui.components.SectionCard
import id.biz.id.cumi.ui.components.StatTile
import id.biz.id.cumi.ui.components.StatusPill
import id.biz.id.cumi.ui.theme.MonoStyle
import id.biz.id.cumi.ui.theme.StatusColors
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    status: ProxyStatus,
    stats: TrafficStats,
    settings: ProxySettings?,
    addresses: List<LanAddress>,
    onToggle: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = status is ProxyStatus.Running
    val port = (status as? ProxyStatus.Running)?.port ?: settings?.port ?: ProxySettings.DEFAULT_PORT

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatusHero(status = status, onToggle = onToggle)

        if (status is ProxyStatus.Error) ErrorBanner(status.message)

        EndpointsCard(
            addresses = addresses,
            port = port,
            running = running,
            onMessage = onMessage,
        )

        if (settings?.authEnabled == true) CredentialsCard(settings, onMessage)

        StatsGrid(status = status, stats = stats)

        HowToCard()

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatusHero(status: ProxyStatus, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (label, color) = when (status) {
        is ProxyStatus.Running -> stringResource(R.string.status_running) to StatusColors.online
        ProxyStatus.Starting -> stringResource(R.string.status_starting) to StatusColors.warning
        is ProxyStatus.Error -> stringResource(R.string.status_error) to colors.error
        ProxyStatus.Stopped -> stringResource(R.string.status_stopped) to colors.outline
    }
    val hint = when (status) {
        is ProxyStatus.Running -> stringResource(R.string.hint_running)
        ProxyStatus.Starting -> stringResource(R.string.hint_starting)
        else -> stringResource(R.string.hint_stopped)
    }

    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PowerButton(
            running = status is ProxyStatus.Running,
            busy = status == ProxyStatus.Starting,
            contentDescription = stringResource(
                if (status is ProxyStatus.Running) R.string.action_stop else R.string.action_start
            ),
            onClick = onToggle,
        )
        StatusPill(label, color)
        Spacer(Modifier.height(8.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EndpointsCard(
    addresses: List<LanAddress>,
    port: Int,
    running: Boolean,
    onMessage: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val copied = stringResource(R.string.copied)

    SectionCard(title = stringResource(R.string.section_endpoints)) {
        if (addresses.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.no_network),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        addresses.forEachIndexed { index, lan ->
            if (index > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            val endpoint = "${lan.address}:$port"
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(
                            if (running) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.shapes.small,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        lan.kind.icon(), contentDescription = null,
                        tint = if (running) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${stringResource(lan.kind.label())} · ${lan.interfaceName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(endpoint, style = MonoStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize))
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(endpoint))
                    onMessage(copied)
                }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                }
            }
        }
    }
}

@Composable
private fun CredentialsCard(settings: ProxySettings, onMessage: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val copied = stringResource(R.string.copied)
    var reveal by remember { mutableStateOf(false) }

    SectionCard(
        title = stringResource(R.string.section_credentials),
        trailing = {
            IconButton(onClick = { reveal = !reveal }) {
                Icon(
                    if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(R.string.action_toggle_password),
                )
            }
        },
    ) {
        CredentialRow(Icons.Rounded.Lock, stringResource(R.string.label_username), settings.username) {
            clipboard.setText(AnnotatedString(settings.username)); onMessage(copied)
        }
        CredentialRow(
            Icons.Rounded.VpnKey,
            stringResource(R.string.label_password),
            if (reveal) settings.password else "•".repeat(settings.password.length.coerceAtMost(12)),
        ) {
            clipboard.setText(AnnotatedString(settings.password)); onMessage(copied)
        }
    }
}

@Composable
private fun CredentialRow(icon: ImageVector, label: String, value: String, onCopy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MonoStyle.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize))
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.action_copy))
        }
    }
}

@Composable
private fun StatsGrid(status: ProxyStatus, stats: TrafficStats) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(status) {
        while (status is ProxyStatus.Running) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val uptime = (status as? ProxyStatus.Running)?.let { Formatters.uptime(now - it.startedAt) } ?: "--:--:--"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                icon = Icons.Rounded.Hub,
                label = stringResource(R.string.stat_active),
                value = stats.activeConnections.toString(),
                caption = stringResource(R.string.stat_total, stats.totalConnections),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                icon = Icons.Rounded.Timer,
                label = stringResource(R.string.stat_uptime),
                value = uptime,
                caption = stringResource(R.string.stat_uptime_caption),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                icon = Icons.Rounded.ArrowUpward,
                label = stringResource(R.string.stat_upload),
                value = Formatters.bytes(stats.bytesUp),
                caption = Formatters.speed(stats.speedUp),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                icon = Icons.Rounded.ArrowDownward,
                label = stringResource(R.string.stat_download),
                value = Formatters.bytes(stats.bytesDown),
                caption = Formatters.speed(stats.speedDown),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HowToCard() {
    SectionCard(title = stringResource(R.string.section_howto)) {
        listOf(R.string.howto_1, R.string.howto_2, R.string.howto_3).forEachIndexed { i, res ->
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraLarge),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun InterfaceKind.icon(): ImageVector = when (this) {
    InterfaceKind.WIFI -> Icons.Rounded.Wifi
    InterfaceKind.HOTSPOT -> Icons.Rounded.WifiTethering
    InterfaceKind.USB -> Icons.Rounded.Usb
    InterfaceKind.BLUETOOTH -> Icons.Rounded.Bluetooth
    InterfaceKind.ETHERNET -> Icons.Rounded.Lan
    InterfaceKind.CELLULAR -> Icons.Rounded.SignalCellularAlt
    InterfaceKind.VPN -> Icons.Rounded.VpnKey
    InterfaceKind.OTHER -> Icons.Rounded.Router
}

private fun InterfaceKind.label(): Int = when (this) {
    InterfaceKind.WIFI -> R.string.iface_wifi
    InterfaceKind.HOTSPOT -> R.string.iface_hotspot
    InterfaceKind.USB -> R.string.iface_usb
    InterfaceKind.BLUETOOTH -> R.string.iface_bluetooth
    InterfaceKind.ETHERNET -> R.string.iface_ethernet
    InterfaceKind.CELLULAR -> R.string.iface_cellular
    InterfaceKind.VPN -> R.string.iface_vpn
    InterfaceKind.OTHER -> R.string.iface_other
}
