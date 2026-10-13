package com.gsynk.app.core

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

/**
 * Правило расписания: в указанное время выставить яркость экрана и громкость медиа.
 *
 * Поддерживаются три способа задать дни:
 *  - [days] — дни недели по ISO (1 = Пн … 7 = Вс), повторяются каждую неделю;
 *  - [dates] — точные даты (ГГГГ-ММ-ДД): правило срабатывает только в эти дни (разовые дни);
 *  - [useInterval] — повтор внутри дня каждые N часов и минут, начиная со [intervalStartHour].
 * Если выбраны точные даты — правило считается «разовым»: после срабатывания оно
 * автоматически выключается, чтобы не мешать.
 */
@Serializable
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Новое правило",
    val hour: Int = 7,
    val minute: Int = 30,
    val days: List<Int> = listOf(1, 2, 3, 4, 5),
    val dates: List<String> = emptyList(),
    val useInterval: Boolean = false,
    val intervalHours: Int = 12,
    val intervalMinutes: Int = 0,
    val intervalStartHour: Int = 8,
    val intervalStartMinute: Int = 0,
    val brightness: Int = 40,
    val volume: Int = 30,
    val useBrightness: Boolean = true,
    val useVolume: Boolean = true,
    val enabled: Boolean = true
) {
    val timeLabel: String
        get() = "%02d:%02d".format(hour, minute)

    /** Точные даты, отфильтрованные от мусора. */
    val validDates: List<LocalDate>
        get() = dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()

    val hasDates: Boolean get() = validDates.isNotEmpty()

    /** Разовое правило: выбраны конкретные даты — после срабатывания выключится само. */
    val isOneTime: Boolean get() = hasDates

    /** Дни недели с учётом точных дат (дата «добавляет» свой день недели). */
    fun effectiveDays(): Set<Int> = days.toSet() + validDates.map { it.dayOfWeek.value }

    /** Время начала интервала. */
    val intervalStartLabel: String
        get() = "%02d:%02d".format(intervalStartHour, intervalStartMinute)

    val intervalLabel: String
        get() = if (!useInterval) "" else buildString {
            append("каждые ")
            if (intervalHours > 0) append("$intervalHours ч ")
            if (intervalMinutes > 0) append("$intervalMinutes мин")
        }.trim()

    fun daysLabel(): String = when {
        days.size >= 7 -> "Каждый день"
        days.toSet() == setOf(1, 2, 3, 4, 5) -> "По будням"
        days.toSet() == setOf(6, 7) -> "По выходным"
        days.isEmpty() -> "Дни не выбраны"
        else -> days.sorted().joinToString(" ") { DAY_SHORT[it - 1] }
    }

    /** Строка выбранных дат: 14.10, 21.10 … */
    fun datesLabel(max: Int = 3): String {
        val list = validDates
        return when {
            list.isEmpty() -> ""
            list.size <= max -> list.joinToString(", ") { "%02d.%02d".format(it.dayOfMonth, it.monthValue) }
            else -> "${list.take(max).joinToString(", ") { "%02d.%02d".format(it.dayOfMonth, it.monthValue) }} +${list.size - max}"
        }
    }

    fun withDay(day: Int, on: Boolean): Rule {
        val next = if (on) (days + day).distinct().sorted() else days.filter { it != day }
        return copy(days = next)
    }

    fun withDate(date: LocalDate, on: Boolean): Rule {
        val iso = date.toString()
        val next = if (on) (dates + iso).distinct().sorted() else dates.filterNot { it == iso }
        return copy(dates = next)
    }

    fun withDates(newDates: List<LocalDate>): Rule =
        copy(dates = newDates.map { it.toString() }.distinct().sorted())

    companion object {
        val DAY_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        val DAY_FULL = listOf(
            "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
        )
    }
}

@Serializable
data class AppSettings(
    /** Автопилот: если выключен, правила не применяются и будильники сняты. */
    val autopilot: Boolean = true,
    /** Показывать короткое уведомление в момент применения правила. */
    val notifications: Boolean = true,
    /** Показывать мини-айленд при срабатывании правила. */
    val islandEnabled: Boolean = true,
    /** Положение острова на экране (см. IslandPosition). */
    val islandPosition: Int = IslandPosition.TOP_CENTER,
    /** Постоянный остров: висит на экране всегда. */
    val islandPersistent: Boolean = false,
    /** Что показывать на постоянном острове (см. IslandWidget). */
    val islandWidgets: List<String> = IslandWidget.DEFAULTS,
    /** Компактный размер капсулы. */
    val islandCompact: Boolean = true,
    /** Смещение острова по вертикали, dp (можно поднять/опустить под свой вырез). */
    val islandOffsetY: Int = 0,
    /** После перезагрузки применить правило, чьё время было последним по графику. */
    val syncAfterBoot: Boolean = true,
    /** Порядок правил на главном экране (см. RuleSort). */
    val ruleSort: Int = RuleSort.BY_TIME,
    /** Репозиторий для OTA-обновлений («владелец/репозиторий»). */
    val updateRepo: String = "GrapeGG-developer/GSYNKAPP",
    /** Проверять обновления автоматически при запуске. */
    val updateAuto: Boolean = true,
    /** Когда последний раз проверяли обновления (ms). */
    val lastUpdateCheck: Long = 0L,
    /** Версия, о которой пользователь уже знает (чтобы не показывать баннер повторно). */
    val seenVersion: String = "",
    /** Путь к локальной ИИ-модели (Gemma .task/.bin), если импортирована. */
    val modelPath: String = "",
    /** Имя импортированной модели — для отображения. */
    val modelName: String = "",
    /** Сколько раз телефон касался другого по NFC (счётчик «рукопожатий»). */
    val nfcTouches: Int = 0,

    // ————— отчёты и диагностика —————
    /** Собирать отчёты о сбоях на телефоне. */
    val reportEnabled: Boolean = true,
    /** Отправлять отчёты в GitHub сразу после сбоя (нужны токен и репозиторий). */
    val reportAutoUpload: Boolean = false,
    /** Репозиторий для отчётов — по умолчанию тот же, где лежат релизы. */
    val reportRepo: String = "GrapeGG-developer/GSYNKAPP",
    /** Токен пользователя: нужен только для загрузки отчётов, хранится на устройстве. */
    val reportToken: String = "",
    val reportLastUpload: Long = 0L
)

object IslandPosition {
    const val TOP_LEFT = 0
    const val TOP_CENTER = 1
    const val TOP_RIGHT = 2
    const val MID_LEFT = 3
    const val MID_RIGHT = 4
    const val BOTTOM_LEFT = 5
    const val BOTTOM_CENTER = 6
    const val BOTTOM_RIGHT = 7

    val ALL = listOf(TOP_LEFT, TOP_CENTER, TOP_RIGHT, MID_LEFT, MID_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT)

    fun label(value: Int): String = when (value) {
        TOP_LEFT -> "Сверху слева"
        TOP_CENTER -> "Сверху по центру"
        TOP_RIGHT -> "Сверху справа"
        MID_LEFT -> "По центру слева"
        MID_RIGHT -> "По центру справа"
        BOTTOM_LEFT -> "Снизу слева"
        BOTTOM_CENTER -> "Снизу по центру"
        else -> "Снизу справа"
    }
}

/** Что выводить на постоянном острове. */
object IslandWidget {
    const val CLOCK = "clock"
    const val BATTERY = "battery"
    const val NEXT_RULE = "next_rule"
    const val NFC_COUNT = "nfc"
    const val RECORD = "record"

    val ALL = listOf(CLOCK, BATTERY, NEXT_RULE, NFC_COUNT, RECORD)
    val DEFAULTS = listOf(CLOCK, BATTERY, NEXT_RULE)

    fun label(id: String): String = when (id) {
        CLOCK -> "Часы"
        BATTERY -> "Заряд батареи"
        NEXT_RULE -> "Ближайшее правило"
        NFC_COUNT -> "Счётчик NFC-касаний"
        else -> "Рекорд игр"
    }
}

object RuleSort {
    const val BY_TIME = 0
    const val BY_NAME = 1
    const val ACTIVE_FIRST = 2

    val ALL = listOf(BY_TIME, BY_NAME, ACTIVE_FIRST)

    fun label(value: Int): String = when (value) {
        BY_TIME -> "По времени"
        BY_NAME -> "По названию"
        else -> "Активные сверху"
    }

    /** Сортировка списка правил выбранным способом. */
    fun apply(rules: List<Rule>, mode: Int): List<Rule> = when (mode) {
        BY_NAME -> rules.sortedBy { it.name.lowercase() }
        ACTIVE_FIRST -> rules.sortedWith(compareByDescending<Rule> { it.enabled }.thenBy { it.hour * 60 + it.minute })
        else -> rules.sortedBy { it.hour * 60 + it.minute }
    }
}
