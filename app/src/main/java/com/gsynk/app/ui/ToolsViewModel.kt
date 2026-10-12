package com.gsynk.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gsynk.app.core.AppSettings
import com.gsynk.app.core.AutomationActions
import com.gsynk.app.core.DeviceProfile
import com.gsynk.app.core.GameScores
import com.gsynk.app.core.IslandOverlay
import com.gsynk.app.core.IslandService
import com.gsynk.app.core.MainMode
import com.gsynk.app.core.RuleEngine
import com.gsynk.app.core.RuleRepository
import com.gsynk.app.core.ScheduledTask
import com.gsynk.app.core.TaskRepository
import com.gsynk.app.core.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Сообщение в чате с ИИ-ассистентом (роль: user | bot). */
data class ChatMessage(val fromUser: Boolean, val text: String)

/**
 * Мультитул-модули: фонарик, «Не беспокоить», задачи со звонком и озвучкой,
 * мини-игры (рекорды), ИИ-чат и OTA-обновление.
 */
class ToolsViewModel(application: Application) : AndroidViewModel(application) {

    private val rulesRepo = RuleRepository(application)
    private val taskRepo = TaskRepository(application)
    private val scores = GameScores(application)

    val settings: StateFlow<AppSettings> =
        rulesRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _device = MutableStateFlow(DeviceProfile.detect(application, AppSettings()))
    val device: StateFlow<DeviceProfile.Info> = _device.asStateFlow()

    private val _torch = MutableStateFlow(false)
    val torch: StateFlow<Boolean> = _torch.asStateFlow()

    private val _dnd = MutableStateFlow(AutomationActions.isDndOn(application))
    val dnd: StateFlow<Boolean> = _dnd.asStateFlow()

    val tasks: StateFlow<List<ScheduledTask>> =
        taskRepo.tasks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bestReaction: StateFlow<Int> = scores.reaction.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val bestSnake: StateFlow<Int> = scores.snake.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _chat = MutableStateFlow(
        listOf(
            ChatMessage(
                fromUser = false,
                text = "Привет! Я GrapeBot — локальный помощник GSYNK.\n" +
                    "Могу управлять приложением текстом: «яркость 40», «громкость 0», " +
                    "«фонарик», «не беспокоить», «включи автопилот»."
            )
        )
    )
    val chat: StateFlow<List<ChatMessage>> = _chat.asStateFlow()

    private val _update = MutableStateFlow<UpdateChecker.Result?>(null)
    val update: StateFlow<UpdateChecker.Result?> = _update.asStateFlow()

    private val _updateBusy = MutableStateFlow(false)
    val updateBusy: StateFlow<Boolean> = _updateBusy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            settings.collect { _device.value = DeviceProfile.detect(getApplication(), it) }
        }
        // состояние фонарика: в Android 16 нет getTorchMode, слушаем системный колбэк
        runCatching {
            val cm = getApplication<Application>()
                .getSystemService(android.hardware.camera2.CameraManager::class.java)
            cm?.registerTorchCallback(
                object : android.hardware.camera2.CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        _torch.value = enabled
                    }
                },
                android.os.Handler(android.os.Looper.getMainLooper())
            )
        }
        viewModelScope.launch {
            // если постоянный остров был включён — поднимаем службу после запуска приложения
            if (rulesRepo.currentSettings().islandPersistent) {
                IslandService.start(getApplication())
            }
        }
    }

    private fun app(): Application = getApplication()

    // ————— фонарик и DND —————

    fun toggleTorch() {
        val next = !_torch.value
        val ok = AutomationActions.setTorch(app(), next)
        _torch.value = ok && next
        _message.value = when {
            ok && next -> "Фонарик включён"
            ok -> "Фонарик выключен"
            else -> "Не удалось переключить фонарик"
        }
    }

    fun toggleDnd() {
        val next = !_dnd.value
        val ok = AutomationActions.setDnd(app(), next)
        if (ok) {
            _dnd.value = next
            _message.value = if (next) "Режим «Не беспокоить» включён" else "Режим «Не беспокоить» выключен"
        } else {
            _message.value = "Нужен доступ к режиму «Не беспокоить»"
            AutomationActions.openDndSettings(app())
        }
    }

    fun refreshDnd() {
        _dnd.value = AutomationActions.isDndOn(app())
    }

    // ————— задачи —————

    fun addTask(
        title: String,
        kind: String,
        number: String,
        phrase: String,
        delaySeconds: Long,
        speakDelayMs: Long = 6_000L
    ) = viewModelScope.launch {
        try {
            val task = ScheduledTask(
                title = title.ifBlank { ScheduledTask.KIND_TITLES[kind] ?: "Задача" },
                triggerAt = System.currentTimeMillis() + delaySeconds * 1000L,
                kind = kind,
                number = number,
                contactName = title,
                phrase = phrase,
                speakDelayMs = speakDelayMs
            )
            taskRepo.add(task)
            val whenText = if (delaySeconds < 60) "через ${delaySeconds} с" else "через ${delaySeconds / 60} мин"
            _message.value = "Задача создана: $whenText"
        } catch (_: Throwable) {
            _message.value = "Не удалось создать задачу"
        }
    }

    fun removeTask(task: ScheduledTask) = viewModelScope.launch {
        try {
            taskRepo.remove(task)
            _message.value = "Задача удалена"
        } catch (_: Throwable) {
            _message.value = "Не удалось удалить задачу"
        }
    }

    fun speak(phrase: String) {
        AutomationActions.speak(app(), phrase)
        _message.value = "Озвучиваю фразу"
    }

    fun stopSpeaking() = AutomationActions.stopSpeaking()

    fun callNow(number: String) {
        if (AutomationActions.call(app(), number) || AutomationActions.dial(app(), number)) {
            _message.value = "Звоним…"
        } else {
            _message.value = "Проверьте номер"
        }
    }

    // ————— игры —————

    fun saveSnakeScore(score: Int) = viewModelScope.launch { scores.saveSnake(score) }
    fun saveReaction(ms: Int) = viewModelScope.launch { scores.saveReaction(ms) }

    // ————— ИИ-чат (локальный разбор команд) —————

    fun sendChat(text: String) = viewModelScope.launch {
        val clean = text.trim()
        if (clean.isEmpty()) return@launch
        _chat.value = _chat.value + ChatMessage(true, clean)
        val reply = withContext(Dispatchers.Default) { respond(clean) }
        _chat.value = _chat.value + ChatMessage(false, reply)
    }

    private suspend fun respond(text: String): String {
        val lower = text.lowercase()
        val number = Regex("(\\d{1,3})").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()

        return when {
            "яркость" in lower && number != null -> {
                val ok = RuleEngine.setBrightnessPercent(app(), number)
                if (ok) "Поставил яркость $number%." else "Нужен доступ «Изменение системных настроек»."
            }

            "громкость" in lower && number != null -> {
                val ok = RuleEngine.setVolumePercent(app(), number)
                if (ok) "Поставил громкость $number%." else "Не удалось изменить громкость."
            }

            "фонарик" in lower -> {
                toggleTorch()
                if (_torch.value) "Фонарик включён." else "Фонарик выключен или недоступен."
            }

            "не беспокоить" in lower || "dnd" in lower -> {
                toggleDnd()
                "Переключил режим «Не беспокоить»."
            }

            "автопилот" in lower -> {
                val current = settings.value.autopilot
                viewModelScope.launch {
                    rulesRepo.saveSettings(rulesRepo.currentSettings().copy(autopilot = !current))
                    com.gsynk.app.core.RuleScheduler.scheduleAll(
                        app(),
                        rulesRepo.currentRules()
                    )
                }
                if (current) "Выключаю автопилот." else "Включаю автопилот."
            }

            "правил" in lower || "правила" in lower -> {
                val count = rulesRepo.currentRules().size
                "Сейчас в расписании $count правил(а)."
            }

            "время" in lower || "час" in lower -> {
                "Сейчас " + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + "."
            }

            "остров" in lower -> "Постоянный остров включается в Настройках — он живёт рядом с фронтальной камерой."

            "обнов" in lower || "версия" in lower ->
                "Обновления проверяются в разделе «Обновление»: укажите репозиторий GitHub, и GSYNK сам скачает новую версию."

            "игра" in lower -> "Мини-игры — в разделе «Игры»: змейка и проверка реакции. NFC-дуэль появится в следующем обновлении."

            "привет" in lower || "здоров" in lower -> "Привет! Чем помочь? Могу менять яркость, громкость, включать фонарик."

            else -> "Пока я понимаю команды: «яркость 40», «громкость 0», «фонарик», " +
                "«не беспокоить», «включи автопилот», «сколько правил», «время». " +
                "Полноценная ИИ-модель (Gemma) подключается отдельным модулем."
        }
    }

    // ————— Main-режим, постоянный остров —————

    fun setMainMode(mode: Int) = viewModelScope.launch {
        try {
            rulesRepo.saveSettings(rulesRepo.currentSettings().copy(mainMode = mode))
            _device.value = DeviceProfile.detect(app(), rulesRepo.currentSettings())
            _message.value = when (mode) {
                MainMode.ON -> "MAIN-режим включён принудительно"
                MainMode.OFF -> "Включён базовый набор функций"
                else -> "MAIN-режим определяется автоматически"
            }
        } catch (_: Throwable) {
            _message.value = "Не удалось изменить режим"
        }
    }

    fun setPersistentIsland(enabled: Boolean) = viewModelScope.launch {
        try {
            rulesRepo.saveSettings(rulesRepo.currentSettings().copy(islandPersistent = enabled))
            if (enabled) {
                if (!IslandOverlay.isAvailable(app())) {
                    _message.value = "Нужен доступ «Поверх других приложений»"
                } else {
                    IslandService.start(app())
                    _message.value = "Постоянный остров включён"
                }
            } else {
                IslandService.stop(app())
                _message.value = "Постоянный остров выключен"
            }
        } catch (_: Throwable) {
            _message.value = "Не удалось переключить остров"
        }
    }

    fun setUpdateRepo(repo: String) = viewModelScope.launch {
        runCatching { rulesRepo.saveSettings(rulesRepo.currentSettings().copy(updateRepo = repo.trim())) }
    }

    // ————— OTA —————

    fun checkUpdate() = viewModelScope.launch {
        _updateBusy.value = true
        val repo = settings.value.updateRepo
        val result = withContext(Dispatchers.IO) { UpdateChecker.check(getApplication(), repo) }
        _update.value = result
        _updateBusy.value = false
    }

    fun downloadAndInstall() = viewModelScope.launch {
        val url = _update.value?.apkUrl.orEmpty()
        if (url.isBlank()) {
            _message.value = "Нет ссылки на APK"
            return@launch
        }
        _updateBusy.value = true
        _message.value = "Скачиваю обновление…"
        val file = withContext(Dispatchers.IO) { UpdateChecker.download(app(), url) }
        _updateBusy.value = false
        if (file == null) {
            _message.value = "Не удалось скачать обновление"
            return@launch
        }
        if (!UpdateChecker.canInstall(app())) {
            _message.value = "Разрешите установку из GSYNK и повторите"
            UpdateChecker.openInstallSettings(app())
            return@launch
        }
        if (UpdateChecker.install(app(), file)) {
            _message.value = "Запускаю установку…"
        } else {
            _message.value = "Не удалось запустить установку"
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
