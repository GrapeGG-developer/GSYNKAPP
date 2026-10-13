package com.gsynk.app.core

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.gsynk.app.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Журнал приложения и приёмник сбоев.
 *
 * Задача простая: что бы ни случилось на телефоне, у пользователя (и у разработчика)
 * должен остаться понятный отчёт — что происходило перед сбоем, на каком устройстве,
 * с какими настройками. Отчёты лежат в `filesDir/reports` и никогда не уходят
 * в интернет сами по себе: отправка только по явному решению пользователя.
 */
object AppLog {

    private const val MAX_MEMORY_LINES = 400
    private const val MAX_CRASH_FILES = 15
    private const val JOURNAL_NAME = "journal.txt"
    private const val JOURNAL_MAX_BYTES = 96 * 1024L
    private const val PREFS = "gsynk_reports"
    private const val KEY_UNSEEN_CRASH = "unseen_crash"
    private const val KEY_CRASH_TOTAL = "crash_total"

    private val buffer = ArrayDeque<String>()
    private val lock = Any()
    private val stamp = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val fileStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    private val humanStamp = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)

    @Volatile
    private var installed = false

    @Volatile
    private var enabled = true

    /**
     * Последнее известное состояние приложения (настройки, правила, доступы).
     *
     * Читать настройки из хранилища в момент сбоя нельзя — оно асинхронное,
     * поэтому приложение само периодически обновляет эту строку, а приёмник
     * сбоя просто берёт готовую.
     */
    @Volatile
    private var stateSummary: String = "состояние ещё не собрано"

    fun rememberState(text: String) {
        stateSummary = text
    }

    /** Разбор всех отчётов о сбоях: сгруппированные причины, частые — первыми. */
    fun analysis(app: Context): List<CrashAnalyzer.Group> = runCatching {
        val dir = reportsDir(app)
        val crashes = dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("crash-") }
            ?: emptyList()
        CrashAnalyzer.group(crashes.map { it.name to it.readText() })
    }.getOrDefault(emptyList())

    /** Собирает человекочитаемое состояние — чистая функция, удобно тестировать. */
    fun describeState(settings: AppSettings, rules: List<Rule>, granted: List<String>): String = buildString {
        appendLine("Правил: ${rules.size}, активных: ${rules.count { it.enabled }}")
        appendLine(
            "Настройки: автопилот=${settings.autopilot}, остров=${settings.islandPersistent}, " +
                "позиция=${IslandPosition.label(settings.islandPosition)}, компактный=${settings.islandCompact}, " +
                "автопроверка=${settings.updateAuto}"
        )
        appendLine("Счётчик NFC: ${settings.nfcTouches}")
        appendLine("Доступы: " + granted.joinToString(", "))
    }.trimEnd()

    // ————— установка приёмника —————

    /** Включаем приём сбоев. Вызывается один раз при старте приложения. */
    fun install(context: Context) {
        val app = context.applicationContext
        if (!installed) {
            installed = true
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                runCatching { handleCrash(app, thread, error) }
                previous?.uncaughtException(thread, error)
            }
            event("App", "запуск ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }
    }

    /** Пользователь может выключить сбор отчётов — тогда пишем только в память. */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    // ————— запись событий —————

    fun event(tag: String, message: String) {
        write("[$tag] $message")
    }

    fun warn(tag: String, message: String, error: Throwable? = null) {
        val detail = error?.let { " — ${it.javaClass.simpleName}: ${it.message}" }.orEmpty()
        write("[$tag] ! $message$detail")
    }

    /** Ловит исключение там, где оно некритично: приложение продолжает работать. */
    inline fun <T> guard(tag: String, message: String, block: () -> T): T? =
        try {
            block()
        } catch (error: Throwable) {
            warn(tag, message, error)
            null
        }

    private fun write(line: String) {
        val entry = "${stamp.format(Date())} $line"
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_MEMORY_LINES) buffer.removeFirst()
        }
    }

    private fun writeJournal(app: Context, lines: List<String>) {
        if (!enabled) return
        runCatching {
            val file = File(reportsDir(app), JOURNAL_NAME)
            if (file.exists() && file.length() > JOURNAL_MAX_BYTES) {
                // оставляем хвост, чтобы файл не рос бесконечно
                val keep = file.readLines().takeLast(500)
                file.writeText(keep.joinToString("\n") + "\n")
            }
            file.appendText(lines.joinToString("\n") + "\n")
        }
    }

    // ————— сбой —————

    private fun handleCrash(app: Context, thread: Thread, error: Throwable) {
        val now = Date()
        val details = deviceSummary(app)
        val log = synchronized(lock) { buffer.toList() }

        val text = buildString {
            appendLine("GSYNK ${BuildConfig.VERSION_NAME} (versionCode ${BuildConfig.VERSION_CODE})")
            appendLine("Тип: сбой приложения")
            appendLine("Время: ${humanStamp.format(now)}")
            appendLine("Поток: ${thread.name} (главный: ${thread == android.os.Looper.getMainLooper().thread})")
            appendLine()
            appendLine("— Устройство —")
            appendLine(details)
            appendLine()
            appendLine("— Последние события (${log.size}) —")
            appendLine(log.joinToString("\n"))
            appendLine()
            appendLine("— Исключение —")
            appendLine(error.stackTraceToString())
        }

        runCatching {
            val dir = reportsDir(app)
            val file = File(dir, "crash-${fileStamp.format(now)}.txt")
            file.writeText(text)
            prune(dir)
        }
        runCatching {
            writeJournal(app, listOf("${humanStamp.format(now)} СБОЙ ${error.javaClass.simpleName}: ${error.message}"))
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_UNSEEN_CRASH, true)
                .putInt(KEY_CRASH_TOTAL, crashTotal(app) + 1)
                .apply()
        }
    }

    /**
     * Ручной отчёт: собирается по кнопке «Собрать отчёт сейчас» — например,
     * когда что-то работает неправильно, но приложение не падает.
     */
    fun buildReport(app: Context, reason: String = "Отчёт по кнопке"): File? = runCatching {
        val now = Date()
        val log = synchronized(lock) { buffer.toList() }
        val text = buildString {
            appendLine("GSYNK ${BuildConfig.VERSION_NAME} (versionCode ${BuildConfig.VERSION_CODE})")
            appendLine("Тип: отчёт")
            appendLine("Причина: $reason")
            appendLine("Время: ${humanStamp.format(now)}")
            // готовый разбор: по нему сразу видно, что чинить
            runCatching { analysis(app) }.getOrNull()?.let { groups ->
                if (groups.isNotEmpty()) appendLine("Разбор: ${CrashAnalyzer.verdict(groups)}")
            }
            appendLine()
            appendLine("— Устройство —")
            appendLine(deviceSummary(app))
            appendLine()
            appendLine("— Последние события (${log.size}) —")
            appendLine(log.joinToString("\n"))
            appendLine()
            appendLine("— Состояние приложения —")
            appendLine(appState(app))
        }
        val dir = reportsDir(app)
        val file = File(dir, "report-${fileStamp.format(now)}.txt")
        file.writeText(text)
        prune(dir)
        writeJournal(app, listOf("${humanStamp.format(now)} отчёт: $reason"))
        file
    }.getOrNull()

    // ————— чтение —————

    data class ReportItem(
        val file: File,
        val title: String,
        val sizeKb: Long,
        val time: String,
        val isCrash: Boolean
    )

    fun reports(app: Context): List<ReportItem> = runCatching {
        val dir = reportsDir(app)
        dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") && it.name != JOURNAL_NAME }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                ReportItem(
                    file = file,
                    title = file.name,
                    sizeKb = (file.length() / 1024).coerceAtLeast(1),
                    time = humanStamp.format(Date(file.lastModified())),
                    isCrash = file.name.startsWith("crash")
                )
            }
            .orEmpty()
    }.getOrDefault(emptyList())

    fun read(file: File): String = runCatching { file.readText() }.getOrDefault("Не удалось прочитать файл")

    fun journalTail(app: Context, lines: Int = 120): String = runCatching {
        val file = File(reportsDir(app), JOURNAL_NAME)
        if (!file.exists()) "" else file.readLines().takeLast(lines).joinToString("\n")
    }.getOrDefault("")

    fun delete(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

    fun clearAll(app: Context) {
        runCatching {
            reportsDir(app).listFiles()?.forEach { it.delete() }
        }
    }

    // ————— состояние —————

    fun unseenCrash(app: Context): Boolean =
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_UNSEEN_CRASH, false)

    fun markCrashSeen(app: Context) {
        runCatching {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_UNSEEN_CRASH, false)
                .apply()
        }
    }

    fun crashTotal(app: Context): Int =
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CRASH_TOTAL, 0)

    // ————— вспомогательное —————

    private fun reportsDir(app: Context): File =
        File(app.filesDir, "reports").apply { if (!exists()) mkdirs() }

    private fun prune(dir: File) {
        val crashes = dir.listFiles()?.filter { it.name.startsWith("crash-") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        crashes.drop(MAX_CRASH_FILES).forEach { it.delete() }
    }

    /** Короткая сводка об устройстве и настройках — первая половина отчёта. */
    private fun deviceSummary(app: Context): String = buildString {
        val profile = runCatching { DeviceProfile.detect(app) }.getOrNull()
        appendLine("Модель: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), прошивка ${Build.DISPLAY}")
        appendLine("Экран: ${app.resources.displayMetrics.widthPixels}x${app.resources.displayMetrics.heightPixels} @ ${app.resources.displayMetrics.density}")
        append("Профиль: ")
        appendLine(profile?.let { "${it.tier} — ${it.reason}" } ?: "не определён")
        appendLine(appState(app))
    }

    /** Вторая половина: настройки, разрешения, память. */
    private fun appState(app: Context): String = buildString {
        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val heapMax = runtime.maxMemory() / 1024 / 1024
        appendLine("Память процесса: $heapUsed из $heapMax МБ")
        appendLine("Аптайм системы: ${SystemClock.elapsedRealtime() / 60000} мин")
        appendLine(stateSummary)

        runCatching {
            val snapshot = Permissions.snapshot(app)
            appendLine(
                "Сейчас выдано: " + snapshot.items.filter { it.granted }.joinToString(", ") { it.title }
            )
        }

        append("Модель ИИ: ")
        appendLine(runCatching { LocalLlm.modelFile(app)?.name }.getOrNull() ?: "не импортирована")
    }
}
