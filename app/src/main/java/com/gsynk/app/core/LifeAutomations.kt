package com.gsynk.app.core

import java.time.ZonedDateTime

/**
 * Готовые повседневные автоматизации GSYNK 5.2.
 *
 * Набор — это обычные правила расписания с детерминированными ID. Повторное
 * включение не плодит копии: те же правила обновляются на месте. У правил нет
 * «времени окончания» — следующее правило просто берёт управление на себя.
 */
object LifeAutomations {

    data class Spec(
        val name: String,
        val hour: Int,
        val minute: Int,
        val days: List<Int>,
        val brightness: Int,
        val volume: Int,
        /** -1 — не трогать, 0 — выключить «не беспокоить», 1 — включить. */
        val silentMode: Int = -1
    )

    data class Pack(
        val id: String,
        val title: String,
        val subtitle: String,
        val emoji: String,
        val specs: List<Spec>
    )

    private val weekdays = listOf(1, 2, 3, 4, 5)
    private val weekend = listOf(6, 7)
    private val everyDay = listOf(1, 2, 3, 4, 5, 6, 7)

    val ALL: List<Pack> = listOf(
        Pack(
            id = "school-home",
            title = "Школа → дом и YouTube",
            subtitle = "в 07:00 тихо и приглушённо, дома ярко и громко",
            emoji = "🎓",
            specs = listOf(
                Spec("Подъём перед школой", 6, 40, weekdays, 55, 25, 0),
                Spec("Школа: тихо и не на виду", 7, 0, weekdays, 28, 0, 1),
                Spec("Уроки: полная тишина", 8, 0, weekdays, 25, 0, 1),
                Spec("Дорога домой", 13, 30, weekdays, 75, 35, 0),
                Spec("Дома: YouTube и видео", 15, 0, weekdays, 85, 80, 0),
                Spec("Домашнее задание", 18, 30, weekdays, 50, 10, 1),
                Spec("Вечер дома", 20, 30, weekdays, 45, 45, 0),
                Spec("Ночь перед школой", 22, 30, weekdays, 8, 0, 1)
            )
        ),
        Pack(
            id = "school-focus",
            title = "Школьный фокус",
            subtitle = "уроки, обед, домашка и отдых без лишнего шума",
            emoji = "📚",
            specs = listOf(
                Spec("Школа без звука", 7, 0, weekdays, 25, 0, 1),
                Spec("Обед и перемена", 12, 30, weekdays, 45, 15, 0),
                Spec("После школы", 14, 0, weekdays, 70, 50, 0),
                Spec("Фокус на домашке", 17, 0, weekdays, 45, 0, 1),
                Spec("Отдых после домашки", 19, 0, weekdays, 75, 65, 0),
                Spec("Сон и тишина", 22, 0, weekdays, 10, 0, 1)
            )
        ),
        Pack(
            id = "early-school",
            title = "Ранняя школа",
            subtitle = "для подъёма до шести и дороги затемно",
            emoji = "🌅",
            specs = listOf(
                Spec("Ранний подъём", 5, 50, weekdays, 45, 15, 0),
                Spec("Дорога в школу", 6, 30, weekdays, 80, 25, 0),
                Spec("Школа: тихо", 7, 0, weekdays, 25, 0, 1),
                Spec("Дорога домой", 13, 45, weekdays, 75, 40, 0),
                Spec("Дом и отдых", 15, 0, weekdays, 80, 70, 0),
                Spec("Ранний сон", 21, 0, weekdays, 8, 0, 1)
            )
        ),
        Pack(
            id = "home-study",
            title = "Учёба дома волнами",
            subtitle = "фокус — перерыв — фокус, как Pomodoro",
            emoji = "⏱️",
            specs = listOf(
                Spec("Фокус 1", 16, 0, everyDay, 50, 0, 1),
                Spec("Перерыв 1", 16, 50, everyDay, 65, 30, 0),
                Spec("Фокус 2", 17, 0, everyDay, 50, 0, 1),
                Spec("Перерыв 2", 17, 50, everyDay, 65, 30, 0),
                Spec("Фокус 3", 18, 0, everyDay, 48, 0, 1),
                Spec("Свободный вечер", 19, 0, everyDay, 75, 65, 0)
            )
        ),
        Pack(
            id = "home-cinema",
            title = "Домашний кинотеатр",
            subtitle = "видео ярко и громко, к ночи экран мягко гаснет",
            emoji = "🎬",
            specs = listOf(
                Spec("Дневное видео", 16, 0, everyDay, 90, 85, 0),
                Spec("Вечернее видео", 19, 0, everyDay, 75, 80, 0),
                Spec("Кино без лишнего света", 21, 0, everyDay, 45, 65, 1),
                Spec("После фильма", 23, 0, everyDay, 12, 10, 1)
            )
        ),
        Pack(
            id = "street-transport",
            title = "Улица и транспорт",
            subtitle = "на солнце максимум, в транспорте не громко",
            emoji = "🚌",
            specs = listOf(
                Spec("Улица утром", 7, 30, weekdays, 100, 45, 0),
                Spec("Транспорт", 8, 0, weekdays, 60, 25, 1),
                Spec("День в городе", 12, 0, weekdays, 85, 40, 0),
                Spec("Улица вечером", 17, 30, weekdays, 100, 50, 0),
                Spec("Дома после дороги", 18, 30, weekdays, 70, 60, 0)
            )
        ),
        Pack(
            id = "games",
            title = "Игры без ночного перебора",
            subtitle = "яркая игровая сессия и перерыв для глаз",
            emoji = "🎮",
            specs = listOf(
                Spec("Игровой режим", 17, 0, everyDay, 90, 75, 0),
                Spec("Перерыв для глаз", 20, 30, everyDay, 45, 35, 0),
                Spec("Тихий вечер после игр", 22, 0, everyDay, 22, 10, 1),
                Spec("Ночь после игр", 23, 0, everyDay, 8, 0, 1)
            )
        ),
        Pack(
            id = "reading-night",
            title = "Чтение и сон",
            subtitle = "экран постепенно темнеет, звук не мешает",
            emoji = "📖",
            specs = listOf(
                Spec("Чтение вечером", 20, 30, everyDay, 28, 0, 1),
                Spec("Чтение ещё темнее", 21, 30, everyDay, 15, 0, 1),
                Spec("Сон", 22, 30, everyDay, 5, 0, 1),
                Spec("Мягкое утро", 7, 0, everyDay, 55, 30, 0)
            )
        ),
        Pack(
            id = "weekend",
            title = "Выходные",
            subtitle = "позднее утро, дневной свет и вечерний отдых",
            emoji = "🌤️",
            specs = listOf(
                Spec("Выходное утро", 9, 30, weekend, 65, 45, 0),
                Spec("Выходной день", 12, 0, weekend, 85, 70, 0),
                Spec("Видео и игры", 18, 0, weekend, 85, 80, 0),
                Spec("Поздний вечер", 22, 30, weekend, 35, 35, 0),
                Spec("Ночь выходного", 23, 30, weekend, 8, 0, 1)
            )
        ),
        Pack(
            id = "exam-library",
            title = "Экзамены и библиотека",
            subtitle = "длинная тишина, перерыв и повторение",
            emoji = "📝",
            specs = listOf(
                Spec("Экзамен: тишина", 9, 0, weekdays, 35, 0, 1),
                Spec("Перерыв после экзамена", 13, 0, weekdays, 55, 15, 0),
                Spec("Повторение в библиотеке", 14, 0, weekdays, 40, 0, 1),
                Spec("Отдых после учёбы", 18, 0, weekdays, 75, 60, 0),
                Spec("Ранний сон перед экзаменом", 21, 30, weekdays, 10, 0, 1)
            )
        ),
        Pack(
            id = "eyes-battery",
            title = "Глаза и батарея",
            subtitle = "спокойная яркость весь день и тёмный вечер",
            emoji = "🔋",
            specs = listOf(
                Spec("Спокойное утро", 8, 0, everyDay, 45, 25, 0),
                Spec("Дневной баланс", 13, 0, everyDay, 60, 35, 0),
                Spec("Мягкий вечер", 19, 0, everyDay, 35, 20, 0),
                Spec("Беречь глаза", 21, 0, everyDay, 18, 5, 1),
                Spec("Ночная экономия", 23, 0, everyDay, 6, 0, 1)
            )
        ),
        Pack(
            id = "sport-walk",
            title = "Спорт и прогулка",
            subtitle = "максимум на улице и спокойный дом после",
            emoji = "🏃",
            specs = listOf(
                Spec("Прогулка и спорт", 17, 0, everyDay, 100, 85, 0),
                Spec("Дорога домой после спорта", 18, 30, everyDay, 75, 50, 0),
                Spec("Восстановление дома", 20, 0, everyDay, 50, 40, 0),
                Spec("Сон после спорта", 22, 30, everyDay, 8, 0, 1)
            )
        )
    )

    fun byId(id: String): Pack? = ALL.firstOrNull { it.id == id }

    fun ruleId(pack: Pack, index: Int): String = "life-" + pack.id + "-" + index

    fun rules(pack: Pack): List<Rule> = pack.specs.mapIndexed { index, spec ->
        Rule(
            id = ruleId(pack, index),
            name = spec.name,
            hour = spec.hour.coerceIn(0, 23),
            minute = spec.minute.coerceIn(0, 59),
            days = spec.days.filter { it in 1..7 }.distinct().sorted(),
            brightness = spec.brightness.coerceIn(0, 100),
            volume = spec.volume.coerceIn(0, 100),
            useBrightness = true,
            useVolume = true,
            enabled = true,
            silentMode = spec.silentMode.coerceIn(-1, 1)
        )
    }

    fun isInstalled(existing: List<Rule>, pack: Pack): Boolean {
        val ids = existing.map { it.id }.toSet()
        return rules(pack).all { it.id in ids }
    }

    /** Правило набора, чьё время уже наступило последним к моменту [now]. */
    fun currentRule(existing: List<Rule>, pack: Pack, now: ZonedDateTime = ZonedDateTime.now()): Rule? {
        val ids = rules(pack).map { it.id }.toSet()
        return existing
            .filter { it.id in ids }
            .mapNotNull { rule -> RuleScheduler.lastOccurrence(rule, now)?.let { it to rule } }
            .maxByOrNull { it.first }
            ?.second
    }

    fun isEnabled(existing: List<Rule>, pack: Pack): Boolean {
        val generated = rules(pack).map { it.id }.toSet()
        val found = existing.filter { it.id in generated }
        return found.size == generated.size && found.all { it.enabled }
    }
}
