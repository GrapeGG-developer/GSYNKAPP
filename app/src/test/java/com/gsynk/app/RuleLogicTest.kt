package com.gsynk.app

import com.gsynk.app.core.Rule
import com.gsynk.app.core.RuleEngine
import com.gsynk.app.core.RuleScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Проверка расписания и математики яркости/громкости.
 * 12.10.2026 — понедельник, 10.10.2026 — суббота, 18.10.2026 — воскресенье.
 */
class RuleLogicTest {

    private val zone = ZoneId.of("Europe/Moscow")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)

    private val morningRule = Rule(
        name = "Утро",
        hour = 7,
        minute = 30,
        days = listOf(1, 2, 3, 4, 5, 6),
        brightness = 25,
        volume = 0
    )

    // ————— расписание —————

    @Test
    fun `срабатывание в тот же день, если время ещё не наступило`() {
        val next = RuleScheduler.nextOccurrence(morningRule, at(2026, 10, 12, 6, 0))
        assertEquals(at(2026, 10, 12, 7, 30), next)
    }

    @Test
    fun `после срабатывания берётся следующий разрешённый день`() {
        val next = RuleScheduler.nextOccurrence(morningRule, at(2026, 10, 12, 8, 0))
        assertEquals(at(2026, 10, 13, 7, 30), next)
    }

    @Test
    fun `суббота входит в правило, воскресенье — нет`() {
        // суббота 17.10 08:00 → следующий понедельник 19.10 07:30
        val next = RuleScheduler.nextOccurrence(morningRule, at(2026, 10, 17, 8, 0))
        assertEquals(at(2026, 10, 19, 7, 30), next)
    }

    @Test
    fun `правило на воскресенье корректно переносится через неделю`() {
        val sundayRule = morningRule.copy(days = listOf(7))
        val next = RuleScheduler.nextOccurrence(sundayRule, at(2026, 10, 12, 6, 0))
        assertEquals(at(2026, 10, 18, 7, 30), next)
    }

    @Test
    fun `выключенное правило и пустые дни не назначаются`() {
        assertNull(RuleScheduler.nextOccurrence(morningRule.copy(enabled = false), at(2026, 10, 12, 6, 0)))
        assertNull(RuleScheduler.nextOccurrence(morningRule.copy(days = emptyList()), at(2026, 10, 12, 6, 0)))
    }

    @Test
    fun `последнее прошедшее срабатывание — сегодня`() {
        val last = RuleScheduler.lastOccurrence(morningRule, at(2026, 10, 12, 8, 0))
        assertEquals(at(2026, 10, 12, 7, 30), last)
    }

    @Test
    fun `последнее прошедшее срабатывание — в прошлую субботу`() {
        val last = RuleScheduler.lastOccurrence(morningRule, at(2026, 10, 12, 6, 0))
        assertEquals(at(2026, 10, 10, 7, 30), last)
    }

    // ————— подписи —————

    @Test
    fun `подписи дней недели по пресетам`() {
        assertEquals("Каждый день", morningRule.copy(days = (1..7).toList()).daysLabel())
        assertEquals("По будням", morningRule.copy(days = listOf(1, 2, 3, 4, 5)).daysLabel())
        assertEquals("По выходным", morningRule.copy(days = listOf(6, 7)).daysLabel())
        assertEquals("Пн Ср Пт", morningRule.copy(days = listOf(5, 1, 3)).daysLabel())
        assertEquals("Дни не выбраны", morningRule.copy(days = emptyList()).daysLabel())
    }

    @Test
    fun `время выводится в формате час-минута`() {
        assertEquals("07:30", morningRule.timeLabel)
        assertEquals("00:05", morningRule.copy(hour = 0, minute = 5).timeLabel)
    }

    @Test
    fun `переключение дня не создаёт дублей`() {
        val rule = morningRule.withDay(1, true)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), rule.days)
        val rule2 = rule.withDay(1, false)
        assertEquals(listOf(2, 3, 4, 5, 6), rule2.days)
    }

    // ————— громкость —————

    @Test
    fun `проценты громкости переводятся в ступени`() {
        assertEquals(0, RuleEngine.percentToVolumeIndex(0, 15))
        assertEquals(7, RuleEngine.percentToVolumeIndex(45, 15))
        assertEquals(15, RuleEngine.percentToVolumeIndex(100, 15))
        assertEquals(25, RuleEngine.percentToVolumeIndex(100, 25))
        // значения вне диапазона подрезаются
        assertEquals(15, RuleEngine.percentToVolumeIndex(150, 15))
        assertEquals(0, RuleEngine.percentToVolumeIndex(-20, 15))
        // некорректный максимум не роняет приложение
        assertEquals(0, RuleEngine.percentToVolumeIndex(50, 0))
    }

    // ————— яркость —————

    @Test
    fun `проценты яркости переводятся в системную шкалу 1-255`() {
        assertEquals(64, RuleEngine.percentToBrightnessRaw(25))
        assertEquals(191, RuleEngine.percentToBrightnessRaw(75))
        assertEquals(255, RuleEngine.percentToBrightnessRaw(100))
        // ноль процентов — минимум 1, иначе экран погаснет полностью
        assertEquals(1, RuleEngine.percentToBrightnessRaw(0))
        assertEquals(255, RuleEngine.percentToBrightnessRaw(999))
    }

    @Test
    fun `индекс громкости переводится обратно в проценты`() {
        assertEquals(0, RuleEngine.volumeIndexesToPercent(0, 15))
        assertEquals(47, RuleEngine.volumeIndexesToPercent(7, 15))
        assertEquals(100, RuleEngine.volumeIndexesToPercent(15, 15))
        assertEquals(100, RuleEngine.volumeIndexesToPercent(25, 25))
        // некорректный максимум не роняет приложение
        assertEquals(0, RuleEngine.volumeIndexesToPercent(5, 0))
    }

    // ————— расширенный выбор дней —————

    @Test
    fun `точные даты добавляют свои дни недели`() {
        // 12.10.2026 — понедельник; дни недели у правила не выбраны вовсе
        val rule = morningRule.copy(days = emptyList(), dates = listOf("2026-10-12"))
        assertEquals(setOf(1), rule.effectiveDays())
        assertEquals(at(2026, 10, 12, 7, 30), RuleScheduler.nextOccurrence(rule, at(2026, 10, 12, 6, 0)))
    }

    @Test
    fun `разовое правило не срабатывает повторно на следующей неделе`() {
        val rule = morningRule.copy(days = emptyList(), dates = listOf("2026-10-12"), brightness = 25)
        assertNull(RuleScheduler.nextOccurrence(rule, at(2026, 10, 12, 9, 0)))
        assertNull(RuleScheduler.nextOccurrence(rule, at(2026, 10, 19, 6, 0)))
    }

    @Test
    fun `подпись дат форматируется и сворачивается`() {
        val rule = morningRule.copy(dates = listOf("2026-10-14", "2026-10-21"))
        assertEquals("14.10, 21.10", rule.datesLabel())
        val many = morningRule.copy(
            dates = listOf("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04")
        )
        assertEquals("01.10, 02.10, 03.10 +1", many.datesLabel(3))
    }

    @Test
    fun `мусорные даты игнорируются и не ломают правило`() {
        val rule = morningRule.copy(dates = listOf("2026-13-99", "abc", "", "2026-10-12"))
        assertEquals(listOf(LocalDate.of(2026, 10, 12)), rule.validDates)
    }

    @Test
    fun `добавление и удаление точной даты`() {
        val date = LocalDate.of(2026, 10, 14)
        val rule = morningRule.withDate(date, true).withDate(date, true)
        assertEquals(listOf(date.toString()), rule.dates)
        assertEquals(emptyList<String>(), rule.withDate(date, false).dates)
    }

    @Test
    fun `подпись интервала повторения`() {
        assertEquals(
            "каждые 3 ч",
            morningRule.copy(useInterval = true, intervalHours = 3, intervalMinutes = 0).intervalLabel
        )
        assertEquals(
            "каждые 1 ч 30 мин",
            morningRule.copy(useInterval = true, intervalHours = 1, intervalMinutes = 30).intervalLabel
        )
        assertEquals(
            "каждые 30 мин",
            morningRule.copy(useInterval = true, intervalHours = 0, intervalMinutes = 30).intervalLabel
        )
        assertEquals("", morningRule.intervalLabel)
    }

    @Test
    fun `время старта интервала выводится корректно`() {
        val rule = morningRule.copy(intervalStartHour = 9, intervalStartMinute = 5)
        assertEquals("09:05", rule.intervalStartLabel)
    }

    @Test
    fun `разовое правило определяется по датам`() {
        assertFalse(morningRule.isOneTime)
        assertTrue(morningRule.copy(dates = listOf("2026-10-12")).isOneTime)
    }

    @Test
    fun `дни из дат и из недели объединяются без повторов`() {
        val rule = morningRule.copy(days = listOf(1, 2), dates = listOf("2026-10-12", "2026-10-14"))
        // 12.10 — понедельник, 14.10 — среда
        assertEquals(setOf(1, 2, 3), rule.effectiveDays())
    }
}
