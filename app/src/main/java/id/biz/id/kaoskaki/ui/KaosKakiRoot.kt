package id.biz.id.kaoskaki.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.biz.id.kaoskaki.R
import id.biz.id.kaoskaki.proxy.ProxyStatus
import id.biz.id.kaoskaki.ui.screens.HomeScreen
import id.biz.id.kaoskaki.ui.screens.LogsScreen
import id.biz.id.kaoskaki.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.tab_home, Icons.Rounded.Dashboard),
    Logs(R.string.tab_logs, Icons.Rounded.Terminal),
    Settings(R.string.tab_settings, Icons.Rounded.Tune),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaosKakiRoot(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val addresses by viewModel.addresses.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    // The proxy works without notifications, so start regardless of the user's answer.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.start() }

    val onToggle: () -> Unit = {
        when (status) {
            is ProxyStatus.Running, ProxyStatus.Starting -> viewModel.stop()
            else -> {
                val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.start()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (tab == Tab.Logs && logs.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearLogs) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.action_clear_logs))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.Home -> HomeScreen(
                status = status,
                stats = stats,
                settings = settings,
                addresses = addresses,
                onToggle = onToggle,
                onMessage = showMessage,
                modifier = modifier,
            )
            Tab.Logs -> LogsScreen(logs = logs, modifier = modifier)
            Tab.Settings -> SettingsScreen(
                settings = settings,
                locked = status is ProxyStatus.Running || status == ProxyStatus.Starting,
                onUpdate = viewModel::updateSettings,
                modifier = modifier,
            )
        }
    }
}
