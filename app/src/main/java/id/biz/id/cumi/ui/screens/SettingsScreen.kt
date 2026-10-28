package id.biz.id.cumi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import id.biz.id.cumi.BuildConfig
import id.biz.id.cumi.R
import id.biz.id.cumi.data.ProxySettings
import id.biz.id.cumi.ui.components.SectionCard

@Composable
fun SettingsScreen(
    settings: ProxySettings?,
    locked: Boolean,
    onUpdate: ((ProxySettings) -> ProxySettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (settings == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // Text fields own their text locally (initialised once from disk) so fast typing
    // is never overwritten by the asynchronous DataStore round-trip.
    var portText by rememberSaveable { mutableStateOf(settings.port.toString()) }
    var username by rememberSaveable { mutableStateOf(settings.username) }
    var password by rememberSaveable { mutableStateOf(settings.password) }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val portValue = portText.toIntOrNull()
    val portError = portValue == null || portValue !in ProxySettings.PORT_RANGE

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (locked) LockedBanner()

        SectionCard(title = stringResource(R.string.section_server)) {
            OutlinedTextField(
                value = portText,
                onValueChange = { input ->
                    val digits = input.filter(Char::isDigit).take(5)
                    portText = digits
                    digits.toIntOrNull()?.takeIf { it in ProxySettings.PORT_RANGE }?.let { p ->
                        onUpdate { it.copy(port = p) }
                    }
                },
                label = { Text(stringResource(R.string.label_port)) },
                supportingText = {
                    Text(stringResource(if (portError) R.string.port_error else R.string.port_hint))
                },
                isError = portError,
                enabled = !locked,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        SectionCard(title = stringResource(R.string.section_auth)) {
            SwitchRow(
                title = stringResource(R.string.auth_title),
                subtitle = stringResource(R.string.auth_subtitle),
                checked = settings.authEnabled,
                enabled = !locked,
                onCheckedChange = { checked -> onUpdate { it.copy(authEnabled = checked) } },
            )
            if (settings.authEnabled) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { v -> username = v.trim(); onUpdate { it.copy(username = v.trim()) } },
                    label = { Text(stringResource(R.string.label_username)) },
                    isError = username.isBlank(),
                    enabled = !locked,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { v -> password = v; onUpdate { it.copy(password = v) } },
                    label = { Text(stringResource(R.string.label_password)) },
                    isError = password.isEmpty(),
                    enabled = !locked,
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(R.string.action_toggle_password),
                            )
                        }
                    },
                    supportingText = {
                        if (username.isBlank() || password.isEmpty()) Text(stringResource(R.string.auth_required_hint))
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }

        SectionCard(title = stringResource(R.string.section_power)) {
            SwitchRow(
                title = stringResource(R.string.keep_awake_title),
                subtitle = stringResource(R.string.keep_awake_subtitle),
                checked = settings.keepAwake,
                enabled = !locked,
                onCheckedChange = { checked -> onUpdate { it.copy(keepAwake = checked) } },
            )
        }

        SectionCard(title = stringResource(R.string.section_about)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_protocol_title)) },
                supportingContent = { Text(stringResource(R.string.about_protocol_body)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun LockedBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.settings_locked), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
