package com.gsynk.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gsynk.app.core.AppLog
import com.gsynk.app.core.AppSettings
import com.gsynk.app.core.Permissions
import com.gsynk.app.core.Rule
import com.gsynk.app.core.RuleEngine
import com.gsynk.app.core.AgentMail
import com.gsynk.app.core.Keys
import com.gsynk.app.core.LifeAutomations
import com.gsynk.app.core.UpdateJobs
import com.gsynk.app.core.RuleRepository
import com.gsynk.app.core.ReportUploader
import com.gsynk.app.core.RuleScheduler
import com.gsynk.app.core.TurboEngine
import com.gsynk.app.core.TurboState
import kotlinx.coroutines.flow.combine
import com.gsynk.app.core.UpdateChecker
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
    /** Непрочитанные ответы агента: показываются баннером и живой плиткой на главном экране. */
    private val _agentUnseen = MutableStateFlow(0)
    val agentUnseen: kotlinx.coroutines.flow.StateFlow<Int> = _agentUnseen.asStateFlow()
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
            if (result.silent) add(if (rule.silentMode == 1) "не беспокоить вкл." else "не беспокоить выкл.")
        }
        _message.value = when {
            parts.isNotEmpty() -> "Применено: " + parts.joinToString(", ")
            rule.useBrightness || rule.useVolume -> "Нет нужных доступов"
            else -> "В правиле отключены яркость и громкость"
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

    /** Включить готовый набор повседневной автоматизации одним сохранением. */
    fun installLifeAutomation(packId: String) = viewModelScope.launch {
        guarded {
            val pack = LifeAutomations.byId(packId) ?: return@guarded
            val generated = LifeAutomations.rules(pack)
            val merged = repository.currentRules().associateBy { it.id }.toMutableMap()
            generated.forEach { merged[it.id] = it }
            persist(merged.values.toList())
            val settings = repository.currentSettings()
            if (!settings.autopilot) {
                repository.saveSettings(settings.copy(autopilot = true))
            }
            rescheduleAll()
            AppLog.event("Автоматизация", "включён набор «" + pack.title + "»")
            _message.value = "«" + pack.title + "»: включено правил — " + generated.size
        }
    }

    /** Выключить только правила конкретного набора, не трогая ручные правила. */
    fun setLifeAutomationEnabled(packId: String, enabled: Boolean) = viewModelScope.launch {
        guarded {
            val pack = LifeAutomations.byId(packId) ?: return@guarded
            val ids = LifeAutomations.rules(pack).map { it.id }.toSet()
            val updated = repository.currentRules().map { rule ->
                if (rule.id in ids) rule.copy(enabled = enabled) else rule
            }
            persist(updated)
            if (enabled) {
                val settings = repository.currentSettings()
                if (!settings.autopilot) repository.saveSettings(settings.copy(autopilot = true))
                rescheduleAll()
            } else {
                updated.filter { it.id in ids }.forEach { rule -> RuleScheduler.cancel(app(), rule.id) }
            }
            _message.value = "«" + pack.title + "» " + if (enabled) "включён" else "выключен"
        }
    }

    /** Сразу применить последнюю уже наступившую сцену набора, не дожидаясь следующего будильника. */
    fun applyLifeAutomationNow(packId: String) = viewModelScope.launch {
        guarded {
            val pack = LifeAutomations.byId(packId) ?: return@guarded
            val ids = LifeAutomations.rules(pack).map { it.id }.toSet()
            val installed = repository.currentRules().filter { it.id in ids }
            val candidates = installed.ifEmpty { LifeAutomations.rules(pack) }
            val current = LifeAutomations.currentRule(candidates, pack)
            if (current == null) {
                _message.value = "Для «" + pack.title + "» пока нет наступившей сцены"
                return@guarded
            }
            val result = RuleEngine.apply(app(), current)
            _message.value = if (result.brightness || result.volume || result.silent) {
                "Применено сейчас: «" + current.name + "»"
            } else {
                "Не удалось применить «" + current.name + "» — проверьте доступы"
            }
            AppLog.event("Автоматизация", "вручную применена сцена «" + current.name + "»")
        }
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

    // ————— обновления (OTA) —————

    private companion object {
        /**
         * Круг автопроверки: раз в минуту.
         *
         * Так стало можно после V4p2: запрос идёт с вшитым токеном (лимит GitHub —
         * 5000 обращений в час вместо 60) и с меткой ETag, поэтому ответ
         * «ничего не изменилось» лимит не тратит вообще. Раньше без токена запросы
         * тихо отбивались кодом 403 — из-за этого обновления и «приходили неохотно».
         */
        const val REFRESH_INTERVAL_MS = 60 * 1000L

        /** Возвращение в приложение: проверяем, если прошло больше полуминуты. */
        const val STALE_INTERVAL_MS = 30 * 1000L

        /** Пока обновление не установлено, напоминаем сообщением раз в 15 минут. */
        const val REMIND_NOTICE_MS = 15 * 60 * 1000L

        /** И раз в 20 минут зовём панель обновления — сами, без нажатий. */
        const val REMIND_PANEL_MS = 20 * 60 * 1000L

        /** «Напомнить позже» в панели: откладываем автопоказ на 10 минут. */
        const val POSTPONE_MS = 10 * 60 * 1000L
    }

    /**
     * Политика напоминаний — чистые правила, их проверяют тесты.
     *
     * Смысл: обновление должно попадаться на глаза, но не превращаться в спам.
     * Пока версия не установлена и не отмечена «пропустить» — панель зовёт себя сама.
     */
    object UpdatePolicy {

        /** Сторож обновлений просыпается каждые 200 мс и проверяет, не пора ли идти в сеть. */
        const val POLL_INTERVAL_MS = 200L

        /** После неудачной/отменённой автоустановки не дёргаем системный установщик 20 минут. */
        const val AUTO_INSTALL_RETRY_MS = 20 * 60 * 1000L

        /** Пора ли делать сетевую проверку по расписанию сторожа. */
        fun isDue(now: Long, nextCheckAt: Long): Boolean = nextCheckAt <= 0L || now >= nextCheckAt

        /** Пора ли показать панель обновления прямо сейчас. */
        fun shouldPrompt(
            available: Boolean,
            acked: Boolean,
            postponedUntil: Long,
            lastPromptAt: Long,
            now: Long
        ): Boolean {
            if (!available || acked) return false
            if (now < postponedUntil) return false
            return now - lastPromptAt >= REMIND_PANEL_MS
        }

        /** Пора ли напомнить сообщением внутри приложения. */
        fun shouldRemind(available: Boolean, acked: Boolean, lastNoticeAt: Long, now: Long): Boolean {
            if (!available || acked) return false
            return now - lastNoticeAt >= REMIND_NOTICE_MS
        }

        /**
         * Можно ли прямо сейчас без нажатия пользователя начать скачивание и установку.
         * Системный Android всё равно покажет свой экран подтверждения установки, но
         * повторять его каждую минуту после отмены нельзя — поэтому есть пауза по тегу.
         */
        fun shouldAutoInstall(
            available: Boolean,
            autoEnabled: Boolean,
            acked: Boolean,
            webOnly: Boolean,
            hasApk: Boolean,
            downloading: Boolean,
            foreground: Boolean,
            tag: String,
            lastTag: String,
            lastAttemptAt: Long,
            postponedUntil: Long,
            now: Long
        ): Boolean {
            if (!available || !autoEnabled || acked || webOnly || !hasApk || downloading || !foreground) return false
            if (tag.isBlank() || now < postponedUntil) return false
            if (tag != lastTag) return true
            return now - lastAttemptAt >= AUTO_INSTALL_RETRY_MS
        }

        /**
         * Через сколько проверять снова. Если GitHub попросил подождать — уважаем
         * его срок и не тратим попытки впустую; при обрывах связи отступаем понемногу.
         */
        fun nextDelayMs(failures: Int, rateLimitedUntil: Long, now: Long): Long = when {
            rateLimitedUntil > now -> (rateLimitedUntil - now).coerceAtMost(30 * 60 * 1000L) + 5_000L
            failures <= 0 -> REFRESH_INTERVAL_MS
            failures == 1 -> 2 * 60 * 1000L
            failures == 2 -> 5 * 60 * 1000L
            else -> 10 * 60 * 1000L
        }

        /** Человеческая подпись для панели: когда проверяли и когда проверим снова. */
        fun describe(lastCheckAt: Long, nextCheckAt: Long, now: Long): String {
            if (lastCheckAt <= 0L) return "ещё не проверял"
            val ago = ((now - lastCheckAt) / 1000L).coerceAtLeast(0L)
            val ahead = ((nextCheckAt - now) / 1000L).coerceAtLeast(0L)
            fun span(seconds: Long): String = when {
                seconds < 60 -> seconds.toString() + " с"
                seconds < 3600 -> (seconds / 60).toString() + " мин"
                else -> (seconds / 3600).toString() + " ч"
            }
            return "проверено " + span(ago) + " назад · следующая через " + span(ahead)
        }
    }

    private val _update = MutableStateFlow<UpdateChecker.Result?>(null)
    val update: StateFlow<UpdateChecker.Result?> = _update.asStateFlow()

    private val _updateDownloading = MutableStateFlow(false)
    val updateDownloading: StateFlow<Boolean> = _updateDownloading.asStateFlow()

    private val _updateProgress = MutableStateFlow(0)
    val updateProgress: StateFlow<Int> = _updateProgress.asStateFlow()

    /** Баннер обновления показываем только когда оно реально есть. */
    val updateAvailable: StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(_update, settings) { result, current ->
            result?.updateAvailable == true && result.latestTag != current.seenVersion
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Счётчик «пора показать панель обновления». Растёт, когда версия найдена впервые
     * и потом каждые 20 минут, пока её не установили. Экран следит за числом: одно и то же
     * значение второй раз панель не откроет, а новое — откроет, даже если человек её закрыл.
     */
    private val _updatePrompt = MutableStateFlow(0)
    val updatePrompt: StateFlow<Int> = _updatePrompt.asStateFlow()

    /** Когда последний раз показывали панель и когда отложили напоминание. */
    private var lastPromptAt = 0L
    private var lastNoticeAt = 0L
    private var postponedUntil = 0L

    /** Проверка идёт прямо сейчас: панель показывает это честно. */
    private val _updateChecking = MutableStateFlow(false)
    val updateChecking: StateFlow<Boolean> = _updateChecking.asStateFlow()

    /** Что рассказать про саму проверку: когда была, когда следующая, что мешало. */
    private val _updateStatus = MutableStateFlow("")
    val updateStatus: StateFlow<String> = _updateStatus.asStateFlow()

    private var updateFailures = 0
    private var nextCheckAt = 0L
    private var awaitingInstallPermission = false

    /**
     * GSYNK сейчас на переднем плане. Скачивать в фоне можно отдельной системной
     * задачей, а вот системный установщик поверх другого приложения сам не открываем.
     */
    @Volatile
    private var isUpdateForeground: Boolean = true

    fun setUpdateForeground(foreground: Boolean) {
        isUpdateForeground = foreground
    }

    init {
        initUpdateCheck()
    }

    /**
     * Автообновление: сторож просыпается каждые 200 мс, сразу замечает наступивший срок,
     * возвращение из настроек установки и найденную версию. Сам сетевой запрос при этом
     * делается не чаще безопасного ETag-расписания [UpdatePolicy.nextDelayMs] (обычно раз
     * в минуту): пять HTTP-запросов в секунду быстро упёрлись бы в лимит GitHub, держали
     * бы радио включённым и разряжали телефон, не ускоряя появление релиза на сервере.
     */
    private fun initUpdateCheck() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { UpdateChecker.cleanupDownloadedUpdates(app()) }
            val current = repository.currentSettings()
            // Первая проверка — сразу: человек открыл приложение, значит хочет знать.
            if (current.updateAuto) checkUpdate(silent = true)
            var networkCircles = 0
            while (true) {
                kotlinx.coroutines.delay(UpdatePolicy.POLL_INTERVAL_MS)
                refreshUpdateStatus()
                remindAboutUpdate()
                val now = System.currentTimeMillis()
                if (!settings.value.updateAuto || _updateChecking.value || _updateDownloading.value) {
                    continue
                }
                if (!UpdatePolicy.isDue(now, nextCheckAt)) continue
                nextCheckAt = now + UpdatePolicy.nextDelayMs(
                    updateFailures,
                    _update.value?.rateLimitedUntil ?: 0L,
                    now
                )
                checkUpdate(silent = true)
                // Переписку проверяем реже обновлений: там мгновенности и не обещали.
                networkCircles++
                if (networkCircles % 4 == 0) checkAgentMail(silent = true)
            }
        }
    }

    /** Подпись для панели: «проверено 12 с назад · следующая через 48 с». */
    private fun refreshUpdateStatus() {
        _updateStatus.value = UpdatePolicy.describe(
            lastCheckAt = settings.value.lastUpdateCheck,
            nextCheckAt = nextCheckAt,
            now = System.currentTimeMillis()
        )
    }

    /**
     * Напоминания об обновлении. Живут в том же круге: пока версия не установлена и не
     * отмечена «пропустить», панель зовёт себя сама, а обычное сообщение тихо напоминает.
     */
    private fun remindAboutUpdate() {
        val now = System.currentTimeMillis()
        val result = _update.value ?: return
        val acked = result.latestTag == settings.value.seenVersion
        val available = result.updateAvailable
        if (UpdatePolicy.shouldPrompt(available, acked, postponedUntil, lastPromptAt, now)) {
            lastPromptAt = now
            _updatePrompt.value = _updatePrompt.value + 1
        }
        if (UpdatePolicy.shouldRemind(available, acked, lastNoticeAt, now)) {
            lastNoticeAt = now
            surfaceNotice("GSYNK " + result.latestTag, "обновление ждёт установки")
        }
    }

    /** Панель открыли — отметим время, чтобы не звать её слишком часто. */
    fun noteUpdatePanelShown() {
        lastPromptAt = System.currentTimeMillis()
    }

    /** «Напомнить позже»: десять минут панель не открывается сама. */
    fun postponeUpdate() {
        postponedUntil = System.currentTimeMillis() + POSTPONE_MS
        _message.value = "Напомню об обновлении через 10 минут"
    }

    /**
     * Проверка ответов агента. Отдельного таймера не заводим: идём тем же кругом,
     * что и обновления, — 30 обращений в час против лимита GitHub в 60.
     */
    fun checkAgentMail(silent: Boolean = false) = viewModelScope.launch {
        if (!Keys.ready()) return@launch
        val result = withContext(Dispatchers.IO) { AgentMail.pull(app()) }
        val fresh = AgentMail.unseen(result.messages, AgentMail.lastSeen(app()))
        val before = _agentUnseen.value
        _agentUnseen.value = fresh
        if (fresh > before && fresh > 0) {
            // Новая весть от агента показывается обычным сообщением и баннером.
            surfaceNotice(
                title = "агент ответил",
                subtitle = "новых сообщений: " + fresh
            )
        }
        if (!silent) _message.value = result.message
    }

    /** Сколько ответов агента ждёт прочтения. */
    fun markAgentSeen() = viewModelScope.launch {
        AgentMail.markSeen(app(), AgentMail.lastId(AgentMail.cached(app())))
        _agentUnseen.value = 0
    }

    /** Возвращение в приложение: если с последней проверки прошло больше 15 минут — проверяем. */
    fun checkUpdateIfStale() = viewModelScope.launch {
        // Напоминания работают даже без автопроверки: версию уже нашли раньше.
        remindAboutUpdate()
        if (awaitingInstallPermission && UpdateChecker.canInstall(app())) {
            awaitingInstallPermission = false
            if (_update.value?.updateAvailable == true) {
                downloadAndInstall(automatic = true)
                return@launch
            }
        }
        val current = repository.currentSettings()
        if (!current.updateAuto) return@launch
        // Если версию уже нашли/скачали до ухода приложения в фон, при возвращении
        // не ждём следующую сетевую проверку: сразу продолжаем установку.
        _update.value?.let { maybeAutoInstall(it) }
        if (_updateDownloading.value) return@launch
        if (System.currentTimeMillis() - current.lastUpdateCheck < STALE_INTERVAL_MS) return@launch
        checkUpdate(silent = true)
    }

    /**
     * Проверка обновлений. Идёт с вшитым токеном (иначе GitHub пускает только 60 запросов
     * в час на всех за общим адресом оператора и отвечает 403) и с меткой ETag, поэтому
     * повторные ответы «ничего не изменилось» лимит не тратят вообще.
     */
    fun checkUpdate(silent: Boolean = false, force: Boolean = false) {
        if (_updateChecking.value) return
        _updateChecking.value = true
        viewModelScope.launch {
            try {
        val current = settings.value
        val repo = current.updateRepo.ifBlank { Keys.REPO }
        if (repo.isBlank()) {
            if (!silent) _message.value = "Укажите репозиторий обновлений"
            return@launch
        }
        if (force) postponedUntil = 0L
        _updateChecking.value = true
        val token = current.reportToken.ifBlank { Keys.REPORT_TOKEN }
        val result = withContext(Dispatchers.IO) { UpdateChecker.check(app(), repo, token) }
        _updateChecking.value = false
        _update.value = result
        updateFailures = if (result.ok) 0 else updateFailures + 1
        repository.saveSettings(
            repository.currentSettings().copy(lastUpdateCheck = System.currentTimeMillis())
        )
        nextCheckAt = System.currentTimeMillis() +
            UpdatePolicy.nextDelayMs(updateFailures, result.rateLimitedUntil, System.currentTimeMillis())
        refreshUpdateStatus()
        if (result.ok && !result.updateAvailable) {
            // Если это первый запуск после установки, временный APK уже устарел.
            withContext(Dispatchers.IO) { UpdateChecker.cleanupDownloadedUpdates(app()) }
        }

        if (!result.ok) {
            // Ошибку показываем даже в тихом режиме: молча ждать человек не должен.
            _message.value = result.message
            AppLog.event("Обновление", "проверка не удалась: " + result.message)
            return@launch
        }
        if (!silent) _message.value = result.message

        // Версия нашлась: обычное сообщение + зов панели, даже если её закрывали.
        if (result.updateAvailable && result.latestTag != settings.value.seenVersion) {
            val fresh = result.latestTag != (_updateAnnounce.value ?: "")
            _updateAnnounce.value = result.latestTag
            if (fresh || lastNoticeAt + REMIND_NOTICE_MS < System.currentTimeMillis()) {
                lastNoticeAt = System.currentTimeMillis()
                surfaceNotice(
                    title = "GSYNK " + result.latestTag,
                    subtitle = if (result.webOnly) "есть новая версия" else "обновление готово",
                    progress = -1
                )
            }
            AppLog.event("Обновление", "найден " + result.latestTag)
            // Панель открывается сама — это и есть «обновление пришло».
            _updatePrompt.value = _updatePrompt.value + 1
            lastPromptAt = System.currentTimeMillis()
            if (current.updateAuto) maybeAutoInstall(result)
        } else if (!result.updateAvailable && result.latestTag.isNotBlank()) {
            _updateAnnounce.value = null
        }
            } finally {
                _updateChecking.value = false
            }
        }
    }

    /** Одна авто-попытка на версию и пауза после отмены системного установщика. */
    private fun maybeAutoInstall(result: UpdateChecker.Result) {
        val now = System.currentTimeMillis()
        val should = UpdatePolicy.shouldAutoInstall(
            available = result.updateAvailable,
            autoEnabled = settings.value.updateAuto,
            acked = result.latestTag == settings.value.seenVersion,
            webOnly = result.webOnly,
            hasApk = result.apkUrl.isNotBlank(),
            downloading = _updateDownloading.value,
            foreground = isUpdateForeground,
            tag = result.latestTag,
            lastTag = UpdateChecker.autoInstallTag(app()),
            lastAttemptAt = UpdateChecker.autoInstallAttemptAt(app()),
            postponedUntil = postponedUntil,
            now = now
        )
        if (!should) return
        UpdateChecker.rememberAutoInstallAttempt(app(), result.latestTag, now)
        AppLog.event("Обновление", "автоматически качаю и ставлю " + result.latestTag)
        downloadAndInstall(automatic = true)
    }

    /**
     * Обычное уведомление внутри приложения: обновления, агент и загрузка сообщаются
     * сообщением главного экрана и существующим состоянием прогресса, без overlay-капсул.
     */
    private fun surfaceNotice(title: String, subtitle: String, progress: Int = -1) {
        if (progress in 0..100) {
            _updateProgress.value = progress
            return
        }
        val text = listOf(title.trim(), subtitle.trim()).filter { it.isNotEmpty() }.joinToString(" — ")
        if (text.isNotBlank()) {
            _message.value = text
            AppLog.event("Уведомление", text)
        }
    }

    /** Метка версии, о которой уже сообщили: чтобы не повторяться. */
    private val _updateAnnounce = MutableStateFlow<String?>(null)
    val updateAnnounce: StateFlow<String?> = _updateAnnounce.asStateFlow()

    /**
     * «Пропустить эту версию»: человек сознательно отказался от неё — больше не напоминаем.
     * Просто закрыть панель теперь можно без отказа: кнопка «напомнить позже».
     */
    fun dismissUpdate() = viewModelScope.launch {
        guarded {
            val version = _update.value?.latestTag.orEmpty()
            repository.saveSettings(repository.currentSettings().copy(seenVersion = version))
            // Про фоновое уведомление тоже помним: пропущенную версию не поднимаем.
            UpdateJobs.rememberSkipped(app(), version)
            postponedUntil = 0L
            _message.value = "Версия " + version + " пропущена — напомню о следующей"
        }
    }

    /** Вернуть напоминания о версии, которую раньше пропустили. */
    fun unskipUpdate() = viewModelScope.launch {
        guarded {
            repository.saveSettings(repository.currentSettings().copy(seenVersion = ""))
            UpdateChecker.clearAutoInstallAttempt(app(), _update.value?.latestTag.orEmpty())
            checkUpdate(silent = true, force = true)
        }
    }

    fun setUpdateAuto(enabled: Boolean) = viewModelScope.launch {
        guarded {
            val current = repository.currentSettings()
            repository.saveSettings(current.copy(updateAuto = enabled))
            // Фоновая проверка живёт ровно столько, сколько разрешил человек.
            UpdateJobs.remember(app(), current.updateRepo.ifBlank { Keys.REPO }, enabled)
            if (enabled) nextCheckAt = 0L
        }
    }

    fun setUpdateRepo(repo: String) = viewModelScope.launch {
        guarded { repository.saveSettings(repository.currentSettings().copy(updateRepo = repo.trim())) }
    }

    /** Скачать APK и сразу открыть системную установку; ручной вызов обходит авто-паузу. */
    fun downloadAndInstall(automatic: Boolean = false) {
        if (_updateDownloading.value) return
        val result = _update.value
        val url = result?.apkUrl.orEmpty()
        val tag = result?.latestTag.orEmpty()
        if (url.isBlank()) {
            // Ссылки нет, если версию увидели по странице релиза: вручную ведём в браузер,
            // а автоматика не должна сама выбрасывать человека из приложения.
            val page = result?.pageUrl.orEmpty()
            if (!automatic && page.isNotBlank()) {
                UpdateChecker.open(app(), page)
                _message.value = "Открываю страницу релиза в браузере"
            } else {
                _message.value = "Нет ссылки на APK — попробуйте проверить ещё раз"
            }
            return
        }
        if (automatic && System.currentTimeMillis() < postponedUntil) return

        _updateDownloading.value = true
        viewModelScope.launch {
            try {
                _updateProgress.value = 0
                surfaceNotice("GSYNK " + tag, "качаю 0%", progress = 0)

                var failureMessage = ""
                val ready = withContext(Dispatchers.IO) {
                    UpdateChecker.downloadedUpdate(
                        context = app(),
                        expectedTag = tag,
                        sha256 = result?.sha256.orEmpty()
                    )
                }
                val file = if (ready != null) {
                    _updateProgress.value = 100
                    surfaceNotice("GSYNK " + tag, "уже скачано", progress = 100)
                    ready
                } else {
                    val download = withContext(Dispatchers.IO) {
                        UpdateChecker.download(
                            context = app(),
                            url = url,
                            expectedTag = tag,
                            sha256 = result?.sha256.orEmpty()
                        ) { percent ->
                            _updateProgress.value = percent
                            // Существующее состояние прогресса идёт за настоящей загрузкой.
                            surfaceNotice("GSYNK " + tag, "качаю " + percent.toString() + "%", progress = percent)
                        }
                    }
                    failureMessage = download.message
                    download.file
                }

                if (file == null) {
                    // Причину показываем как есть: «не скачалось» без объяснения бесполезно.
                    _message.value = "Обновление не скачалось: " + failureMessage
                    surfaceNotice("GSYNK " + tag, "не скачалось")
                    AppLog.event("Обновление", "скачивание не удалось: " + failureMessage)
                    return@launch
                }

                if (automatic && (!settings.value.updateAuto || tag == settings.value.seenVersion)) {
                    UpdateChecker.clearAutoInstallAttempt(app(), tag)
                    _message.value = "Автообновление остановлено настройкой; APK останется во временном кэше для ручной установки"
                    return@launch
                }
                if (automatic && !isUpdateForeground) {
                    UpdateChecker.clearAutoInstallAttempt(app(), tag)
                    _message.value = "Обновление скачано — установщик откроется при возвращении в GSYNK"
                    return@launch
                }
                if (automatic && System.currentTimeMillis() < postponedUntil) {
                    UpdateChecker.clearAutoInstallAttempt(app(), tag)
                    _message.value = "Обновление скачано — напомню об установке позже"
                    return@launch
                }

                surfaceNotice("GSYNK " + tag, "скачано, ставлю")
                if (!UpdateChecker.canInstall(app())) {
                    awaitingInstallPermission = true
                    _message.value = "Разрешите установку из GSYNK — сейчас открою настройки"
                    UpdateChecker.openInstallSettings(app())
                    return@launch
                }
                awaitingInstallPermission = false
                if (UpdateChecker.install(app(), file)) {
                    _message.value = "Запускаю установку… после обновления временный APK удалится сам"
                } else {
                    _message.value = "Не удалось запустить установку — нажмите «Скачать и обновить» ещё раз"
                }
            } finally {
                _updateDownloading.value = false
            }
        }
    }

    // ————— отчёты и диагностика —————

    private val _reports = MutableStateFlow<List<AppLog.ReportItem>>(emptyList())
    val reports: StateFlow<List<AppLog.ReportItem>> = _reports.asStateFlow()

    private val _lastCrash = MutableStateFlow(AppLog.unseenCrash(application))
    val lastCrash: StateFlow<Boolean> = _lastCrash.asStateFlow()

    private val _journal = MutableStateFlow("")
    val journal: StateFlow<String> = _journal.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    init {
        viewModelScope.launch { AppLog.setEnabled(repository.currentSettings().reportEnabled) }
        viewModelScope.launch { refreshReports() }
        viewModelScope.launch { maybeAutoUpload() }
        // держим снимок состояния готовым заранее: в момент сбоя читать хранилище поздно
        viewModelScope.launch {
            combine(settings, rules) { current, list -> current to list }
                .collect { (current, list) ->
                    AppLog.rememberState(
                        AppLog.describeState(current, list, Permissions.grantedTitles(app()))
                    )
                }
        }
    }

    // ————— нагрузка: процессор, видеоускоритель и нейросеть —————
    private val turbo = TurboEngine(app())

    /** Включить или выключить максимальную нагрузку. */
    fun toggleTurbo() {
        if (TurboState.active.value) turbo.stop() else turbo.start()
    }

    val turboActive: StateFlow<Boolean> = TurboState.active.asStateFlow()

    fun stopTurbo() {
        turbo.stop()
    }

    fun refreshReports() = viewModelScope.launch {
        _reports.value = withContext(Dispatchers.IO) { AppLog.reports(app()) }
        _journal.value = withContext(Dispatchers.IO) { AppLog.journalTail(app()) }
    }

    /** Собрать отчёт по кнопке — например, когда что-то работает неправильно. */
    fun collectReport(reason: String = "Отчёт по кнопке") = viewModelScope.launch {
        val file = withContext(Dispatchers.IO) { AppLog.buildReport(app(), reason) }
        refreshReports()
        _message.value = if (file == null) "Не удалось собрать отчёт" else "Отчёт собран: ${file.name}"
    }

    fun deleteReport(item: AppLog.ReportItem) = viewModelScope.launch {
        withContext(Dispatchers.IO) { AppLog.delete(item.file) }
        refreshReports()
    }

    fun clearReports() = viewModelScope.launch {
        withContext(Dispatchers.IO) { AppLog.clearAll(app()) }
        refreshReports()
        _message.value = "Отчёты удалены"
    }

    /** Сколько сбоев зафиксировано за всё время (для плитки «Диагностика»). */
    fun crashCountNow(): Int = AppLog.crashTotal(app())

    fun markCrashSeen() {
        AppLog.markCrashSeen(app())
        _lastCrash.value = false
    }

    /**
     * Репозиторий для отчётов: вшитый в приложение, а если человек вписал свой — его.
     * Так отчёты уходят без настройки, но своя настройка всё ещё уважается.
     */
    fun reportRepo(): String = Keys.REPO

    /** Токен для отчётов: вшитый в приложение или вписанный вручную. */
    fun reportToken(current: AppSettings): String =
        current.reportToken.ifBlank { Keys.REPORT_TOKEN }

    fun setReportEnabled(enabled: Boolean) = viewModelScope.launch {
        guarded {
            repository.saveSettings(repository.currentSettings().copy(reportEnabled = enabled))
            AppLog.setEnabled(enabled)
        }
    }

    fun setReportAutoUpload(enabled: Boolean) = viewModelScope.launch {
        guarded {
            repository.saveSettings(repository.currentSettings().copy(reportAutoUpload = enabled))
        }
    }

    fun setReportRepo(repo: String) = viewModelScope.launch {
        guarded { repository.saveSettings(repository.currentSettings().copy(reportRepo = repo.trim())) }
    }

    fun setReportToken(token: String) = viewModelScope.launch {
        guarded { repository.saveSettings(repository.currentSettings().copy(reportToken = token.trim())) }
    }

    /** Отправить все отчёты в GitHub (токен и репозиторий берём из настроек). */
    fun uploadReports() = viewModelScope.launch {
        val current = repository.currentSettings()
        if (reportRepo().isBlank() || reportToken(current).isBlank()) {
            _message.value = "Укажите репозиторий и токен для отправки"
            return@launch
        }
        _uploading.value = true
        val result = withContext(Dispatchers.IO) {
            ReportUploader.upload(app(), reportRepo(), reportToken(current))
        }
        _uploading.value = false
        repository.saveSettings(repository.currentSettings().copy(reportLastUpload = System.currentTimeMillis()))
        _message.value = result.message
        refreshReports()
    }

    /** Отправить один отчёт. */
    fun uploadReport(item: AppLog.ReportItem) = viewModelScope.launch {
        val current = repository.currentSettings()
        if (reportRepo().isBlank() || reportToken(current).isBlank()) {
            _message.value = "Укажите репозиторий и токен для отправки"
            return@launch
        }
        _uploading.value = true
        val result = withContext(Dispatchers.IO) {
            ReportUploader.uploadOne(app(), reportRepo(), reportToken(current), item.file)
        }
        _uploading.value = false
        _message.value = result.message
        refreshReports()
    }

    /** После сбоя: если пользователь разрешил — отправляем отчёт сами. */
    private fun maybeAutoUpload() = viewModelScope.launch {
        val current = repository.currentSettings()
        if (!current.reportAutoUpload || reportToken(current).isBlank()) return@launch
        val hasCrash = withContext(Dispatchers.IO) {
            AppLog.reports(app()).any { it.isCrash && !ReportUploader.isUploaded(it.file) }
        }
        if (!hasCrash) return@launch
        val result = withContext(Dispatchers.IO) {
            ReportUploader.upload(app(), reportRepo(), reportToken(current))
        }
        AppLog.event("Reports", "автоотправка: ${result.message}")
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

    /** Порядок правил на главном экране. */
    // ————— кастомизация —————

    fun setAccent(value: Int) = viewModelScope.launch {
        guarded { repository.saveSettings(repository.currentSettings().copy(accent = value.coerceIn(0, 4))) }
    }

    fun setBackgroundSpeed(value: Int) = viewModelScope.launch {
        guarded { repository.saveSettings(repository.currentSettings().copy(backgroundSpeed = value.coerceIn(0, 2))) }
    }

    fun setHaptics(enabled: Boolean) = viewModelScope.launch {
        guarded { repository.saveSettings(repository.currentSettings().copy(haptics = enabled)) }
    }

    fun setClickSound(enabled: Boolean) = viewModelScope.launch {
        guarded { repository.saveSettings(repository.currentSettings().copy(clickSound = enabled)) }
    }

    fun setTileScale(value: Int) = viewModelScope.launch {
        guarded { repository.saveSettings(repository.currentSettings().copy(tileScale = value.coerceIn(1, 3))) }
    }

    fun setTileCounters(enabled: Boolean) = viewModelScope.launch {
        guarded { repository.saveSettings(repository.currentSettings().copy(tileCounters = enabled)) }
    }

    fun setCompactList(enabled: Boolean) = viewModelScope.launch {
        guarded { repository.saveSettings(repository.currentSettings().copy(compactList = enabled)) }
    }

    fun setRuleSort(mode: Int) = viewModelScope.launch {
        guarded {
            repository.saveSettings(repository.currentSettings().copy(ruleSort = mode))
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

    /** Показать сообщение в главном экране (например, от мультитула). */
    fun showMessage(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }
    override fun onCleared() {
        runCatching { turbo.release() }
        super.onCleared()
    }

}
