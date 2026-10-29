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
    val enabled: Boolean = true,
    /** -1 — не трогать «Не беспокоить», 0 — выключить, 1 — включить. Старые JSON без поля читаются как -1. */
    val silentMode: Int = -1
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
    /** После перезагрузки применить правило, чьё время было последним по графику. */
    val syncAfterBoot: Boolean = true,
    /** Порядок правил на главном экране (см. RuleSort). */
    val ruleSort: Int = RuleSort.BY_TIME,
    /** Репозиторий для OTA-обновлений («владелец/репозиторий») — вшит в приложение. */
    val updateRepo: String = Keys.REPO,
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

    // ————— кастомизация (3.0) —————
    /** Акцент интерфейса: 0 — виноград, 1 — лёд, 2 — янтарь, 3 — изумруд, 4 — гранат. */
    val accent: Int = 0,
    /** Скорость переливания фона: 0 — спокойно, 1 — обычно, 2 — быстро. */
    val backgroundSpeed: Int = 1,
    /** Отклик вибрацией на нажатия. */
    val haptics: Boolean = true,
    /** Короткий щелчок при нажатии плиток. */
    val clickSound: Boolean = false,
    /** Размер плиток: 1 — мелкие, 2 — обычные, 3 — крупные. */
    val tileScale: Int = 2,
    /** Показывать живые счётчики на плитках. */
    val tileCounters: Boolean = true,
    /** Плотный список на главном экране (меньше воздуха между карточками). */
    val compactList: Boolean = false,

    // ————— отчёты и диагностика —————
    /** Собирать отчёты о сбоях на телефоне. */
    val reportEnabled: Boolean = true,
    /**
     * Отправлять отчёты в GitHub сразу после сбоя. Токен и репозиторий вшиты
     * в приложение ([Keys]), поэтому по умолчанию отправка включена.
     */
    val reportAutoUpload: Boolean = true,
    /** Репозиторий для отчётов. Пусто = взять вшитый ([Keys.REPO]). */
    val reportRepo: String = "",
    /** Токен пользователя: нужен только для загрузки отчётов, хранится на устройстве. */
    val reportToken: String = "",
    val reportLastUpload: Long = 0L
)

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
