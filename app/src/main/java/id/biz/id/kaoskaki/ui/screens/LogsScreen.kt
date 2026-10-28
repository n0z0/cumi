package id.biz.id.kaoskaki.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.biz.id.kaoskaki.R
import id.biz.id.kaoskaki.proxy.LogEntry
import id.biz.id.kaoskaki.proxy.LogLevel
import id.biz.id.kaoskaki.ui.theme.MonoStyle
import id.biz.id.kaoskaki.ui.theme.StatusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(logs: List<LogEntry>, modifier: Modifier = Modifier) {
    if (logs.isEmpty()) {
        EmptyLogs(modifier)
        return
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val newestFirst = remember(logs) { logs.asReversed() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(newestFirst, key = { it.id }) { entry ->
            LogRow(entry, timeFormat.format(Date(entry.timestamp)))
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry, time: String) {
    val accent = when (entry.level) {
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.SUCCESS -> StatusColors.online
        LogLevel.WARNING -> StatusColors.warning
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Box(
                Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .background(accent, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(time, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(entry.message, style = MonoStyle, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun EmptyLogs(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Terminal, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.logs_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.logs_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

