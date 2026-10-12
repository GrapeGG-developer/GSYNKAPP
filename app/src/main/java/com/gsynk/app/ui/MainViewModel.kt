package com.gsynk.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gsynk.app.R
import com.gsynk.app.core.AppSettings
import com.gsynk.app.core.IslandOverlay
import com.gsynk.app.core.Permissions
import com.gsynk.app.core.Rule
import com.gsynk.app.core.RuleEngine
import com.gsynk.app.core.RuleRepository
import com.gsynk.app.core.RuleScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.math.abs

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RuleRepository(application)

    val rules: StateFlow<List<Rule>> =
        repository.rules.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<AppSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { rescheduleAll() }
    }

    private fun app(): Application = getApplication()

    /** Операции с хранилищем не должны ронять приложение — оборачиваем и сообщаем. */
    private suspend fun guarded(block: suspend () -> Unit) {
        runCatching { block() }.onFailure {
            _message.value = "Не удалось сохранить изменения"
        }
    }

    private suspend fun persist(list: List<Rule>) {
        repository.saveRules(list.sortedWith(compareBy({ it.hour }, { it.minute }, { it.name })))
    }

    private suspend fun rescheduleAll() {
        runCatching {
            val current = repository.currentSettings()
            val list = repository.currentRules()
            if (current.autopilot) {
                RuleScheduler.scheduleAll(app(), list)
            } else {
                list.forEach { RuleScheduler.cancel(app(), it.id) }
            }
        }
    }

    private fun islandPosition() = settings.value.islandPosition

    // ————— действия с правилами —————

    fun upsert(rule: Rule, announce: Boolean = true) = viewModelScope.launch {
        guarded {
            val list = repository.currentRules().toMutableList()
            val index = list.indexOfFirst { it.id == rule.id }
            if (index >= 0) list[index] = rule else list.add(rule)
            persist(list)
            if (repository.currentSettings().autopilot) {
                RuleScheduler.schedule(app(), rule)
            }
            if (announce) {
                _message.value = "Сохранено: «${rule.name}» в ${rule.timeLabel}"
            }
        }
    }

    fun delete(rule: Rule) = viewModelScope.launch {
        guarded {
            persist(repository.currentRules().filterNot { it.id == rule.id })
            RuleScheduler.cancel(app(), rule.id)
            _message.value = "Правило «${rule.name}» удалено"
        }
    }

    fun duplicate(rule: Rule) = viewModelScope.launch {
        guarded {
            val copy = rule.copy(
                id = UUID.randomUUID().toString(),
                name = "${rule.name} (копия)",
                dates = emptyList() // копия разового правила становится обычным недельным
            )
            persist(repository.currentRules() + copy)
            if (repository.currentSettings().autopilot) {
                RuleScheduler.schedule(app(), copy)
            }
            _message.value = "Создана копия правила"
        }
    }

    fun setEnabled(rule: Rule, enabled: Boolean) = viewModelScope.launch {
        guarded {
            val list = repository.currentRules().toMutableList()
            val index = list.indexOfFirst { it.id == rule.id }
            if (index < 0) return@guarded
            list[index] = rule.copy(enabled = enabled)
            persist(list)
            if (enabled && repository.currentSettings().autopilot) {
                RuleScheduler.schedule(app(), list[index])
            } else {
                RuleScheduler.cancel(app(), rule.id)
            }
            _message.value = if (enabled) "«${rule.name}» включено" else "«${rule.name}» выключено"
        }
    }

    /**
     * Применить правило прямо сейчас.
     * Если значения уже стоят — ничего не меняем и честно сообщаем об этом.
     */
    fun applyNow(rule: Rule) = viewModelScope.launch {
        val alreadyMatches = withContext(Dispatchers.Default) { matchesCurrent(rule) }
        if (alreadyMatches) {
            _message.value = "Эти значения уже установлены"
            return@launch
        }

        val result = withContext(Dispatchers.Default) {
            runCatching { RuleEngine.apply(app(), rule) }.getOrNull()
        }
        if (result == null) {
            _message.value = "Не удалось применить правило"
            return@launch
        }

        val parts = buildList {
            if (result.brightness) add("яркость ${rule.brightness}%")
            if (result.volume) add("громкость ${rule.volume}%")
        }
        _message.value = when {
            parts.isNotEmpty() -> "Применено: " + parts.joinToString(", ")
            rule.useBrightness || rule.useVolume -> "Нет нужных доступов"
            else -> "В правиле отключены яркость и громкость"
        }
        if (parts.isNotEmpty() && settings.value.islandEnabled) {
            IslandOverlay.show(
                context = app(),
                iconRes = if (result.brightness) R.drawable.ic_brightness else R.drawable.ic_volume,
                title = rule.name,
                subtitle = parts.joinToString(" • ").replaceFirstChar { it.uppercase() },
                position = islandPosition()
            )
        }

        // если применяли разовое правило вручную — тоже выключаем его
        if (rule.isOneTime && rule.enabled) {
            setEnabled(rule, false)
        }
    }

    /** Совпадают ли текущие яркость и громкость со значениями правила (допуск 2 % на ступени). */
    private fun matchesCurrent(rule: Rule): Boolean {
        val brightnessOk = !rule.useBrightness ||
            abs(RuleEngine.brightnessPercent(app()) - rule.brightness) <= 2
        val volumeOk = !rule.useVolume ||
            abs(RuleEngine.volumePercent(app()) - rule.volume) <= 2
        return brightnessOk && volumeOk
    }

    // ————— готовые сценарии (быстрое создание правил) —————

    /** Разовые дни: правило сработает только в выбранные даты и потом выключится. */
    fun addDateRule(
        dates: List<LocalDate>,
        hour: Int = 8,
        minute: Int = 0,
        brightness: Int = 40,
        volume: Int = 30,
        useBrightness: Boolean = true,
        useVolume: Boolean = true
    ) = viewModelScope.launch {
        guarded {
            if (dates.isEmpty()) {
                _message.value = "Выберите хотя бы одну дату"
                return@guarded
            }
            val iso = dates.map { it.toString() }.sorted()
            val rule = Rule(
                name = "Разовая: " + iso.joinToString(", ") { "%02d.%02d".format(java.time.LocalDate.parse(it).dayOfMonth, java.time.LocalDate.parse(it).monthValue) },
                hour = hour,
                minute = minute,
                days = dates.map { it.dayOfWeek.value }.distinct(),
                dates = iso,
                brightness = brightness,
                volume = volume,
                useBrightness = useBrightness,
                useVolume = useVolume
            )
            persist(repository.currentRules() + rule)
            if (repository.currentSettings().autopilot) RuleScheduler.schedule(app(), rule)
            _message.value = "Разовое правило создано (${iso.size} дн.)"
        }
    }

    /** Правило с повтором в течение дня: каждые N часов и минут от времени старта. */
    fun addIntervalRule(
        name: String = "Интервал",
        startHour: Int = 8,
        startMinute: Int = 0,
        everyHours: Int = 3,
        everyMinutes: Int = 0,
        days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
        brightness: Int = 50,
        volume: Int = 40
    ) = viewModelScope.launch {
        guarded {
            val rule = Rule(
                name = name,
                hour = startHour,
                minute = startMinute,
                days = days,
                useInterval = true,
                intervalHours = everyHours,
                intervalMinutes = everyMinutes,
                intervalStartHour = startHour,
                intervalStartMinute = startMinute,
                brightness = brightness,
                volume = volume
            )
            persist(repository.currentRules() + rule)
            if (repository.currentSettings().autopilot) RuleScheduler.schedule(app(), rule)
            _message.value = "Правило «$name» создано"
        }
    }

    // ————— настройки —————

    fun setAutopilot(enabled: Boolean) = viewModelScope.launch {
        guarded {
            repository.saveSettings(repository.currentSettings().copy(autopilot = enabled))
            rescheduleAll()
            _message.value = if (enabled) "Автопилот включён" else "Автопилот выключен"
        }
    }

    fun setNotifications(enabled: Boolean) = viewModelScope.launch {
        guarded { repository.saveSettings(repository.currentSettings().copy(notifications = enabled)) }
    }

    fun setIsland(enabled: Boolean) = viewModelScope.launch {
        guarded {
            repository.saveSettings(repository.currentSettings().copy(islandEnabled = enabled))
            if (enabled && !Permissions.canDrawOverlays(app())) {
                _message.value = "Нужен доступ «Поверх других приложений»"
            } else {
                _message.value = if (enabled) "Мини-айленд включён" else "Мини-айленд выключен"
            }
        }
    }

    fun setIslandPosition(position: Int) = viewModelScope.launch {
        guarded {
            repository.saveSettings(repository.currentSettings().copy(islandPosition = position))
            val label = when (position) {
                com.gsynk.app.core.IslandPosition.BOTTOM -> "снизу"
                com.gsynk.app.core.IslandPosition.CENTER -> "по центру"
                else -> "сверху"
            }
            _message.value = "Айленд: $label"
            if (settings.value.islandEnabled) {
                fullIslandPreview()
            }
        }
    }

    fun setSyncAfterBoot(enabled: Boolean) = viewModelScope.launch {
        guarded { repository.saveSettings(repository.currentSettings().copy(syncAfterBoot = enabled)) }
    }

    /** Привести телефон к состоянию графика прямо сейчас (последнее по времени правило). */
    fun syncToSchedule() = viewModelScope.launch {
        val list = repository.currentRules()
        val now = ZonedDateTime.now()
        val latest = list.asSequence()
            .filter { it.enabled }
            .mapNotNull { rule -> RuleScheduler.lastOccurrence(rule, now)?.let { it to rule } }
            .maxByOrNull { it.first }
        if (latest == null) {
            _message.value = "Нет активных правил"
            return@launch
        }
        val result = withContext(Dispatchers.Default) {
            runCatching { RuleEngine.apply(app(), latest.second) }.getOrNull()
        }
        _message.value = when {
            result == null -> "Не удалось применить правило"
            result.brightness || result.volume -> "Синхронизировано: «${latest.second.name}»"
            else -> "Нет нужных доступов"
        }
    }

    fun restoreAutoBrightness() = viewModelScope.launch {
        _message.value = if (RuleEngine.setAutoBrightness(app(), true)) {
            "Автояркость включена"
        } else {
            "Нужен доступ «Изменение системных настроек»"
        }
    }

    /** Тестовый показ айленда. */
    fun previewIsland() = viewModelScope.launch {
        if (!Permissions.canDrawOverlays(app())) {
            _message.value = "Сначала дайте доступ «Поверх других приложений»"
            return@launch
        }
        fullIslandPreview()
    }

    fun fullIslandPreview() {
        IslandOverlay.show(
            context = app(),
            iconRes = R.drawable.ic_brightness,
            title = "GSYNK",
            subtitle = "Яркость 25% • Звук 0%",
            position = islandPosition()
        )
    }

    fun clearMessage() {
        _message.value = null
    }
}
