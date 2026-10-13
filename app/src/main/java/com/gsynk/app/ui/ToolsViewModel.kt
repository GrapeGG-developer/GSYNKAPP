package com.gsynk.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gsynk.app.core.AutomationActions
import com.gsynk.app.core.DeviceStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Состояние быстрых инструментов: фонарик, «не беспокоить», сирена, вибрация
 * и живой дашборд показателей телефона.
 */
class ToolsViewModel(application: Application) : AndroidViewModel(application) {

    private val _torch = MutableStateFlow(false)
    val torch: StateFlow<Boolean> = _torch.asStateFlow()

    private val _dnd = MutableStateFlow(false)
    val dnd: StateFlow<Boolean> = _dnd.asStateFlow()

    private val _siren = MutableStateFlow(false)
    val siren: StateFlow<Boolean> = _siren.asStateFlow()

    private val _stats = MutableStateFlow<DeviceStats.Snapshot?>(null)
    val stats: StateFlow<DeviceStats.Snapshot?> = _stats.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshDnd()
        viewModelScope.launch {
            while (true) {
                _stats.value = withContext(Dispatchers.IO) { DeviceStats.snapshot(getApplication()) }
                delay(2_000L)
            }
        }
        // если сервис сирены остановлен системой — снимаем флаг
        viewModelScope.launch {
            while (true) {
                delay(1_500L)
                if (_siren.value && !AutomationActions.isSirenRunning()) _siren.value = false
            }
        }
    }

    fun toggleTorch() = viewModelScope.launch {
        val next = !_torch.value
        val ok = withContext(Dispatchers.IO) { AutomationActions.setTorch(getApplication(), next) }
        if (ok) {
            _torch.value = next
        } else {
            _message.value = "Фонарик недоступен (занят другим приложением?)"
        }
    }

    fun toggleDnd() = viewModelScope.launch {
        val next = !_dnd.value
        val ok = withContext(Dispatchers.IO) { AutomationActions.setDnd(getApplication(), next) }
        if (ok) {
            _dnd.value = next
        } else {
            _message.value = "Нужен доступ к режиму «Не беспокоить»"
        }
    }

    fun refreshDnd() {
        _dnd.value = AutomationActions.isDndOn(getApplication())
    }

    fun toggleSiren() = viewModelScope.launch {
        if (_siren.value) {
            AutomationActions.stopSiren()
            _siren.value = false
        } else {
            AutomationActions.startSiren().let { }
            _siren.value = true
            _message.value = "Сирена: нажмите ещё раз, чтобы выключить"
        }
    }

    fun vibrate() = viewModelScope.launch {
        AutomationActions.vibrate(getApplication(), 400L)
    }

    fun openSettings(action: String) = AutomationActions.openSettings(getApplication(), action)

    fun clearMessage() {
        _message.value = null
    }
}
