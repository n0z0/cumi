package id.biz.id.kaoskaki.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.biz.id.kaoskaki.data.ProxySettings
import id.biz.id.kaoskaki.data.SettingsRepository
import id.biz.id.kaoskaki.proxy.LanAddress
import id.biz.id.kaoskaki.proxy.LogEntry
import id.biz.id.kaoskaki.proxy.NetworkInfo
import id.biz.id.kaoskaki.proxy.ProxyRuntime
import id.biz.id.kaoskaki.proxy.ProxyStatus
import id.biz.id.kaoskaki.proxy.TrafficStats
import id.biz.id.kaoskaki.service.ProxyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    val status: StateFlow<ProxyStatus> = ProxyRuntime.status
    val stats: StateFlow<TrafficStats> = ProxyRuntime.stats
    val logs: StateFlow<List<LogEntry>> = ProxyRuntime.logs

    /** Null until the first read from disk completes, so editors never show stale defaults. */
    val settings: StateFlow<ProxySettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _addresses = MutableStateFlow<List<LanAddress>>(emptyList())
    val addresses: StateFlow<List<LanAddress>> = _addresses.asStateFlow()

    init {
        // Interfaces come and go (hotspot, USB tethering); a cheap poll keeps the list fresh.
        viewModelScope.launch {
            while (true) {
                refreshAddresses()
                delay(3000)
            }
        }
    }

    fun refreshAddresses() {
        viewModelScope.launch {
            _addresses.value = withContext(Dispatchers.IO) { NetworkInfo.lanAddresses() }
        }
    }

    fun start() = ProxyService.start(getApplication<Application>())

    fun stop() = ProxyService.stop(getApplication<Application>())

    fun updateSettings(transform: (ProxySettings) -> ProxySettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun clearLogs() = ProxyRuntime.clearLogs()
}
