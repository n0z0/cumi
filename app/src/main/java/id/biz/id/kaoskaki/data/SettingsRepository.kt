package id.biz.id.kaoskaki.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ProxySettings(
    val port: Int = DEFAULT_PORT,
    val authEnabled: Boolean = false,
    val username: String = "",
    val password: String = "",
    val keepAwake: Boolean = true,
) {
    val authValid: Boolean get() = !authEnabled || (username.isNotBlank() && password.isNotEmpty())

    companion object {
        const val DEFAULT_PORT = 1080
        val PORT_RANGE = 1024..65535
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<ProxySettings> = store.data.map { it.toSettings() }

    suspend fun update(transform: (ProxySettings) -> ProxySettings) {
        store.edit { p ->
            val next = transform(p.toSettings())
            p[PORT] = next.port
            p[AUTH_ENABLED] = next.authEnabled
            p[USERNAME] = next.username
            p[PASSWORD] = next.password
            p[KEEP_AWAKE] = next.keepAwake
        }
    }

    private fun Preferences.toSettings() = ProxySettings(
        port = this[PORT] ?: ProxySettings.DEFAULT_PORT,
        authEnabled = this[AUTH_ENABLED] ?: false,
        username = this[USERNAME].orEmpty(),
        password = this[PASSWORD].orEmpty(),
        keepAwake = this[KEEP_AWAKE] ?: true,
    )

    private companion object {
        val PORT = intPreferencesKey("port")
        val AUTH_ENABLED = booleanPreferencesKey("auth_enabled")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val KEEP_AWAKE = booleanPreferencesKey("keep_awake")
    }
}
