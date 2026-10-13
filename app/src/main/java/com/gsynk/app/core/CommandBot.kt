package com.gsynk.app.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * GrapeBot без языковой модели: простой помощник, который понимает команды.
 *
 * Раньше тут был импорт Gemma (.task) и нейросеть на телефоне — тяжело, медленно
 * и требовало отдельного файла модели. Теперь бот разбирает фразу по ключевым словам
 * и выполняет её прямо на устройстве: яркость, громкость, фонарик, «не беспокоить»,
 * вибрация, открытие экранов и системных настроек, поиск в браузере, буфер обмена.
 * Всё офлайн, ответ — мгновенный.
 */
object CommandBot {

    data class Reply(val text: String, val intent: Intent? = null, val toggles: String = "")

    private val brightness = Regex("(яркость|ярче|темнее|потемнее|посветлее)")
    private val volume = Regex("(громк|тише|громче|звук)")
    private val percent = Regex("(\\d{1,3})\\s*(%|процент)?")

    /** Что бот умеет — показывается списком на экране чата. */
    val examples = listOf(
        "яркость 40",
        "громкость 70",
        "фонарик вкл / выкл",
        "не беспокоить вкл",
        "вибрация",
        "открой настройки батареи",
        "покажи все датчики",
        "запусти цитату дня",
        "позвони 89001234567",
        "найди в браузере мурзик",
        "кто я",
        "помощь"
    )

    fun ask(context: Context, raw: String): Reply {
        val phrase = raw.trim().lowercase()
        if (phrase.isBlank()) return Reply("Напишите команду — например, «яркость 40».")

        // ————— справка —————
        if (phrase.startsWith("помощь") || phrase == "help" || phrase == "что ты умеешь") {
            return Reply("Умею: " + examples.joinToString("; ") + ".")
        }
        if (phrase.contains("кто я") || phrase.contains("что за телефон")) {
            val profile = DeviceProfile.detect(context)
            return Reply(
                "Это " + profile.model + ". Экран " + profile.width + "x" + profile.height +
                    ", Android " + profile.androidLabel + ", уровень возможностей: " + profile.tierLabel + "."
            )
        }

        // ————— яркость —————
        if (brightness.containsMatchIn(phrase)) {
            val value = percent.find(phrase)?.groupValues?.getOrNull(1)?.toIntOrNull()
            return when {
                value != null -> {
                    val applied = SystemTools.setBrightness(context, value)
                    Reply(if (applied) "Яркость: $value проц." else "Система не дала поменять яркость — включите правило яркости в приложении.")
                }

                phrase.contains("ярче") -> Reply(callSystemTool { SystemTools.brightnessStep(context, 1) })
                phrase.contains("темнее") || phrase.contains("потемнее") -> Reply(callSystemTool { SystemTools.brightnessStep(context, -1) })
                else -> Reply("Скажите число: «яркость 40».")
            }
        }

        // ————— громкость —————
        if (volume.containsMatchIn(phrase)) {
            val value = percent.find(phrase)?.groupValues?.getOrNull(1)?.toIntOrNull()
            return when {
                value != null -> {
                    val applied = SystemTools.setVolume(context, value)
                    Reply(if (applied) "Громкость: $value проц." else "Не получилось поменять громкость — нужен доступ к «Не беспокоить».")
                }

                phrase.contains("громче") -> Reply(callSystemTool { SystemTools.volumeStep(context, 1) })
                phrase.contains("тише") -> Reply(callSystemTool { SystemTools.volumeStep(context, -1) })
                else -> Reply("Скажите число: «громкость 70».")
            }
        }

        // ————— фонарик —————
        if (phrase.contains("фонарик") || phrase.contains("вспышк")) {
            val on = !(phrase.contains("выкл") || phrase.contains("отключ") || phrase.contains("погас"))
            val ok = AutomationActions.setTorch(context, on)
            return Reply(
                if (ok) "Фонарик " + (if (on) "включён" else "выключен") + "."
                else "На этом телефоне нет доступной вспышки."
            )
        }

        // ————— не беспокоить —————
        if (phrase.contains("не беспоко") || phrase.contains("dnd") || phrase.contains("тихий режим")) {
            val on = !(phrase.contains("выкл") || phrase.contains("отключ"))
            val allowed = AutomationActions.dndGranted(context)
            if (!allowed) {
                return Reply(
                    text = "Для «Не беспокоить» нужно разрешение. Открыл настройки.",
                    intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                )
            }
            var applied = true
            repeat(3) { if (!applied) applied = AutomationActions.setDnd(context, on) }
            return Reply(if (applied) "Режим «Не беспокоить»: " + (if (on) "включён" else "выключен") + "."
            else "Система не дала переключить «Не беспокоить».")
        }

        // ————— вибрация —————
        if (phrase.contains("вибр") || phrase.contains("тряхни")) {
            AutomationActions.vibrate(context, 500L)
            return Reply("Тряхнул.")
        }

        // ————— звонок —————
        val phone = Regex("(\\+?\\d[\\d\\-\\s]{7,15})").find(phrase)?.value?.filter { it.isDigit() }
        if (phone != null && (phrase.contains("позвони") || phrase.contains("набери"))) {
            return Reply(
                text = "Набираю $phone.",
                intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone))
            )
        }

        // ————— поиск в браузере —————
        if (phrase.contains("найди") || phrase.contains("поищи") || phrase.contains("загугли")) {
            val query = phrase
                .replace("найди", "")
                .replace("поищи", "")
                .replace("загугли", "")
                .replace("в браузере", "")
                .trim()
            if (query.isNotBlank()) {
                return Reply(
                    text = "Ищу «" + query + "» в браузере.",
                    intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(query)))
                )
            }
        }

        // ————— буфер обмена —————
        if (phrase.contains("скопируй")) {
            return Reply("Откройте Grape Link — там кнопка «Положить в касание» скопирует данные куда нужно.")
        }

        return Reply(
            "Не понял команду. " + examples.take(4).joinToString("; ") + " — или напишите «помощь»."
        )
    }

    private fun callSystemTool(block: () -> Boolean): String =
        if (block()) "Готово." else "Не вышло — проверьте доступы приложения."
}
