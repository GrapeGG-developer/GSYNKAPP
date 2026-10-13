package com.gsynk.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gsynk.app.core.AppLog
import com.gsynk.app.core.AutomationActions
import com.gsynk.app.core.DeviceStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private var statsJob: Job? = null

    init {
        AppLog.event("Tools", "открыт раздел «Инструменты»")
        refreshDnd()
    }

    /** Дашборд обновляем только пока экран открыт — фоном батарею не тратим. */
    fun startStats() {
        if (statsJob?.isActive == true) return
        statsJob = viewModelScope.launch {
            while (true) {
                // любая ошибка чтения показателей логируется, но не роняет приложение
                try {
                    _stats.value = withContext(Dispatchers.IO) { DeviceStats.snapshot(getApplication()) }
                } catch (error: Throwable) {
                    AppLog.warn("Stats", "цикл показателей", error)
                }
                delay(2_000L)
            }
        }
    }

    fun stopStats() {
        statsJob?.cancel()
        statsJob = null
    }

    fun toggleTorch() = viewModelScope.launch {
        val next = !_torch.value
        val ok = withContext(Dispatchers.IO) {
            AppLog.guard("Torch", "переключение фонарика") {
                AutomationActions.setTorch(getApplication(), next)
            } ?: false
        }
        if (ok) {
            _torch.value = next
        } else {
            _message.value = "Фонарик недоступен (занят другим приложением?)"
        }
    }

    fun toggleDnd() = viewModelScope.launch {
        val next = !_dnd.value
        val ok = withContext(Dispatchers.IO) {
            AppLog.guard("Dnd", "переключение режима тишины") {
                AutomationActions.setDnd(getApplication(), next)
            } ?: false
        }
        if (ok) {
            _dnd.value = next
        } else {
            _message.value = "Нужен доступ к режиму «Не беспокоить»"
        }
    }

    fun refreshDnd() {
        _dnd.value = AutomationActions.isDndOn(getApplication())
    }

    private var sirenJob: Job? = null

    fun toggleSiren() = viewModelScope.launch {
        if (_siren.value) {
            AutomationActions.stopSiren()
            _siren.value = false
            sirenJob?.cancel()
            sirenJob = null
        } else if (AutomationActions.startSiren()) {
            _siren.value = true
            _message.value = "Сирена: нажмите ещё раз, чтобы выключить"
            // если звук прибила система — честно снимаем флаг
            sirenJob = viewModelScope.launch {
                while (true) {
                    delay(1_500L)
                    if (!AutomationActions.isSirenRunning()) {
                        _siren.value = false
                        break
                    }
                }
            }
        } else {
            _message.value = "Не удалось включить сирену"
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
