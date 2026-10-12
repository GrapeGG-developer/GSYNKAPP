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
    /** Положение мини-айленда на экране. */
    val islandPosition: Int = IslandPosition.TOP,
    /** Постоянный остров: висит на экране всегда (как системный Dynamic Island). */
    val islandPersistent: Boolean = false,
    /** После перезагрузки применить правило, чьё время было последним по графику. */
    val syncAfterBoot: Boolean = true,
    /** MAIN-режим: авто / принудительно полный / базовый. */
    val mainMode: Int = MainMode.AUTO,
    /** Репозиторий для OTA-обновлений («владелец/репозиторий»). */
    val updateRepo: String = "GrapeGG-developer/GSYNKAPP"
)

object MainMode {
    const val AUTO = 0
    const val ON = 1
    const val OFF = 2
}

object IslandPosition {
    const val TOP = 0
    const val CENTER = 1
    const val BOTTOM = 2
}
