package com.gsynk.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gsynk.app.R
import com.gsynk.app.core.AppLog
import com.gsynk.app.core.AppSettings
import com.gsynk.app.core.IslandOverlay
import com.gsynk.app.core.IslandService
import com.gsynk.app.core.Permissions
import com.gsynk.app.core.Rule
import com.gsynk.app.core.RuleEngine
import com.gsynk.app.core.Keys
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
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { rescheduleAll() }
        // Остров 2.0: сразу применяем посадку из настроек, иначе капсула встанет ниже камеры.
        syncIslandHug()
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

    /** Посадка острова приходит из настроек — держим оверлей в курсе. */
    private fun syncIslandHug() {
        IslandOverlay.hugCamera = settings.value.islandHugCamera
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

    // ————— обновления (OTA) —————

    private companion object {
        /**
         * Круг автопроверки: раз в 2 минуты.
         *
         * Раньше было раз в 6 часов, потом 20 минут — и всё равно обновление ждало
         * человека. GitHub без токена пускает 60 запросов в час с одного адреса,
         * поэтому 2 минуты (30 запросов в час) — это «почти постоянная» проверка
         * без риска упереться в лимит.
         */
        const val REFRESH_INTERVAL_MS = 2 * 60 * 1000L

        /** Возвращение в приложение: проверяем, если прошло больше полутора минут. */
        const val STALE_INTERVAL_MS = 90 * 1000L
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

    init {
        initUpdateCheck()
    }

    /**
     * Автопроверка обновлений: сразу при запуске, дальше каждые 2 минуты,
     * плюс отдельный пинок при возвращении в приложение ([checkUpdateIfStale]) —
     * раньше проверка была раз в 6 часов, и обновление ждало ручного нажатия.
     */
    private fun initUpdateCheck() {
        viewModelScope.launch {
            val current = repository.currentSettings()
            if (current.updateAuto) {
                val stale = System.currentTimeMillis() - current.lastUpdateCheck < REFRESH_INTERVAL_MS
                if (!stale) checkUpdate(silent = true)
            }
            // дальше — ровным кругом, пока приложение живёт
            while (true) {
                kotlinx.coroutines.delay(REFRESH_INTERVAL_MS)
                if (repository.currentSettings().updateAuto) checkUpdate(silent = true)
            }
        }
    }

    /** Возвращение в приложение: если с последней проверки прошло больше 15 минут — проверяем. */
    fun checkUpdateIfStale() = viewModelScope.launch {
        val current = repository.currentSettings()
        if (!current.updateAuto) return@launch
        if (System.currentTimeMillis() - current.lastUpdateCheck < STALE_INTERVAL_MS) return@launch
        checkUpdate(silent = true)
    }

    fun checkUpdate(silent: Boolean = false) = viewModelScope.launch {
        val repo = repository.currentSettings().updateRepo
        if (repo.isBlank()) {
            if (!silent) _message.value = "Укажите репозиторий обновлений"
            return@launch
        }
        val result = withContext(Dispatchers.IO) { UpdateChecker.check(app(), repo) }
        _update.value = result
        repository.saveSettings(
            repository.currentSettings().copy(lastUpdateCheck = System.currentTimeMillis())
        )
        if (!silent) {
            _message.value = result.message
        }
        // Фирменная анимация: обновление приходит в островок, а не «где-то в настройках».
        if (result.updateAvailable && result.latestTag != repository.currentSettings().seenVersion) {
            val previous = repository.currentSettings().lastUpdateCheck
            _updateAnnounce.value = result.latestTag
            announceIsland(
                title = "GSYNK " + result.latestTag,
                subtitle = "обновление готово",
                persistent = false,
                progress = -1
            )
            if (previous == 0L) AppLog.event("Обновление", "найден " + result.latestTag)
        }
    }

    /**
     * Остров говорит «GSYNK»: одна капсула на все случаи обновления — нашлась версия,
     * качается APK, готово к установке. Progress меньше нуля — без полосы.
     */
    private fun announceIsland(title: String, subtitle: String, persistent: Boolean, progress: Int = -1) {
        // Настройки уже лежат в потоке состояния — читать хранилище из UI-потока не нужно.
        val current = settings.value
        if (persistent) {
            val text = if (progress in 0..100) progress.toString() + "%" else title
            // Если капсула уже висит — только обновляем подписи: окно не пересоздаётся
            // на каждый процент, иначе система мигает при загрузке.
            if (IslandOverlay.isShownPersistent()) {
                IslandOverlay.updatePersistent(
                    context = app(),
                    title = title,
                    subtitle = subtitle,
                    expandedText = text,
                    position = islandPosition(),
                    compact = current.islandCompact,
                    offsetY = current.islandOffsetY
                )
            } else {
                IslandOverlay.showPersistent(
                    context = app(),
                    title = title,
                    subtitle = subtitle,
                    expandedText = text,
                    position = islandPosition(),
                    compact = current.islandCompact,
                    offsetY = current.islandOffsetY
                )
            }
        } else {
            IslandOverlay.show(
                context = app(),
                iconRes = com.gsynk.app.R.drawable.ic_bolt,
                title = title,
                subtitle = subtitle,
                position = islandPosition(),
                durationMs = 5_000L,
                compact = current.islandCompact,
                offsetY = current.islandOffsetY
            )
        }
    }

    /** Метка версии, о которой уже рассказали в островке: чтобы не повторяться. */
    private val _updateAnnounce = MutableStateFlow<String?>(null)
    val updateAnnounce: StateFlow<String?> = _updateAnnounce.asStateFlow()

    /** Пользователь закрыл панель: больше не показываем баннер для этой версии. */
    fun dismissUpdate() = viewModelScope.launch {
        guarded {
            val version = _update.value?.latestTag.orEmpty()
            repository.saveSettings(repository.currentSettings().copy(seenVersion = version))
        }
    }

    fun setUpdateAuto(enabled: Boolean) = viewModelScope.launch {
        guarded { repository.saveSettings(repository.currentSettings().copy(updateAuto = enabled)) }
    }

    fun setUpdateRepo(repo: String) = viewModelScope.launch {
        guarded { repository.saveSettings(repository.currentSettings().copy(updateRepo = repo.trim())) }
    }

    /** Скачать и установить обновление с показом прогресса. */
    fun downloadAndInstall() = viewModelScope.launch {
        val url = _update.value?.apkUrl.orEmpty()
        if (url.isBlank()) {
            _message.value = "Нет ссылки на APK"
            return@launch
        }
        _updateDownloading.value = true
        _updateProgress.value = 0
        val tag = _update.value?.latestTag.orEmpty()
        announceIsland("GSYNK " + tag, "качаю 0%", persistent = true, progress = 0)
        val file = withContext(Dispatchers.IO) {
            UpdateChecker.download(app(), url) { percent ->
                _updateProgress.value = percent
                // Полоса и подпись в островке идут за настоящей загрузкой.
                announceIsland("GSYNK " + tag, "качаю " + percent.toString() + "%", persistent = true, progress = percent)
            }
        }
        _updateDownloading.value = false
        if (file == null) {
            _message.value = "Не удалось скачать обновление"
            announceIsland("GSYNK " + tag, "не скачалось", persistent = false)
            return@launch
        }
        announceIsland("GSYNK " + tag, "скачано, ставлю", persistent = false)
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

    /** Постоянный остров: включён — висит капсула, выключен — только вспышки. */
    fun setIslandPersistent(enabled: Boolean) = viewModelScope.launch {
        guarded {
            repository.saveSettings(repository.currentSettings().copy(islandPersistent = enabled))
            if (enabled) {
                if (!Permissions.canDrawOverlays(app())) {
                    _message.value = "Нужен доступ «Поверх других приложений»"
                } else if (!Permissions.hasNotifications(app())) {
                    _message.value = "Нужны уведомления: без них остров не удержать"
                } else {
                    IslandService.start(app())
                    _message.value = "Остров включён"
                }
            } else {
                IslandService.stop(app())
                _message.value = "Остров выключен"
            }
        }
    }

    fun setIslandPosition(position: Int) = viewModelScope.launch {
        guarded {
            repository.saveSettings(repository.currentSettings().copy(islandPosition = position))
            _message.value = "Остров: " + com.gsynk.app.core.IslandPosition.label(position).lowercase()
            if (settings.value.islandPersistent) {
                IslandService.start(app())
            } else if (settings.value.islandEnabled) {
                fullIslandPreview()
            }
        }
    }

    /** Размер капсулы: компактный или обычный. */
    override fun onCleared() {
        runCatching { turbo.release() }
        super.onCleared()
    }

    fun setIslandCompact(compact: Boolean) = viewModelScope.launch {
        guarded {
            repository.saveSettings(repository.currentSettings().copy(islandCompact = compact))
            if (settings.value.islandPersistent) IslandService.start(app()) else fullIslandPreview()
        }
    }

    /** Смещение острова по вертикали в dp. */
    fun setIslandOffset(offset: Int) = viewModelScope.launch {
        guarded {
            repository.saveSettings(repository.currentSettings().copy(islandOffsetY = offset))
            if (settings.value.islandPersistent) IslandService.start(app()) else fullIslandPreview()
        }
    }

    /** Остров 2.0: прижаться к камере или встать ниже неё. */
    fun setIslandHugCamera(hug: Boolean) = viewModelScope.launch {
        guarded {
            repository.saveSettings(repository.currentSettings().copy(islandHugCamera = hug))
            IslandOverlay.hugCamera = hug
            if (settings.value.islandPersistent) IslandService.start(app()) else fullIslandPreview()
        }
    }

    /** Что показывать на постоянном острове. */
    fun toggleIslandWidget(widget: String, on: Boolean) = viewModelScope.launch {
        guarded {
            val current = repository.currentSettings().islandWidgets
            val next = if (on) (current + widget).distinct() else current.filterNot { it == widget }
            repository.saveSettings(repository.currentSettings().copy(islandWidgets = next))
            if (settings.value.islandPersistent) IslandService.start(app())
        }
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

    /** Показать сообщение в главном экране (например, от мультитула). */
    fun showMessage(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }
}
