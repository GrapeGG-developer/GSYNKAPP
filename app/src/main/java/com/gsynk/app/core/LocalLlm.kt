package com.gsynk.app.core

import android.content.Context
import java.io.File

/**
 * Локальная языковая модель (Gemma через MediaPipe LLM Inference).
 *
 * Модель НЕ поставляется вместе с приложением — это гигабайты. Пользователь один раз
 * импортирует файл модели (.task или .bin) с накопителя, после чего чат работает
 * полностью офлайн, без интернета, ключей и лимитов.
 */
object LocalLlm {

    /** Расширения, которые понимает MediaPipe. */
    val SUPPORTED_EXTENSIONS = listOf("task", "bin")

    fun modelFile(context: Context): File? {
        val directory = File(context.filesDir, "models")
        if (!directory.exists()) return null
        return directory.listFiles()
            ?.firstOrNull { file -> SUPPORTED_EXTENSIONS.any { file.name.endsWith(".$it", true) } }
    }

    fun isReady(context: Context): Boolean = modelFile(context)?.exists() == true

    fun sizeGb(context: Context): Double {
        val file = modelFile(context) ?: return 0.0
        return file.length() / 1024.0 / 1024 / 1024
    }

    /** Копирует выбранный файл модели в приватную папку приложения. */
    fun import(context: Context, source: java.io.InputStream, name: String): File? = runCatching {
        val directory = File(context.filesDir, "models").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val target = File(directory, name.substringAfterLast('/').ifBlank { "model.task" })
        source.use { input -> target.outputStream().use { output -> input.copyTo(output, 1 shl 16) } }
        target
    }.getOrNull()

    fun delete(context: Context) {
        runCatching { File(context.filesDir, "models").deleteRecursively() }
    }
}

/**
 * Локальный ассистент команд: работает всегда, даже без импортированной модели.
 * Понимает простые фразы и выполняет действия прямо в приложении.
 */
object LocalAssistant {

    data class Reply(val text: String, val action: Boolean = false)

    suspend fun answer(context: Context, message: String): String {
        val lower = message.lowercase()
        val number = Regex("(\\d{1,3})").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()

        return when {
            "яркость" in lower && number != null -> {
                if (RuleEngine.setBrightnessPercent(context, number)) "Поставил яркость $number%."
                else "Нужен доступ «Изменение системных настроек» — откройте карточку доступов."
            }

            "громкость" in lower && number != null -> {
                if (RuleEngine.setVolumePercent(context, number)) "Поставил громкость $number%."
                else "Не удалось изменить громкость."
            }

            "фонарик" in lower -> {
                val on = lower.contains("вкл") || (!lower.contains("выкл") && !lower.contains("off"))
                if (AutomationActions.setTorch(context, on)) {
                    if (on) "Фонарик включён." else "Фонарик выключен."
                } else {
                    "Не удалось переключить фонарик."
                }
            }

            "не беспокоить" in lower || "тишин" in lower -> {
                val on = !lower.contains("выкл")
                if (AutomationActions.setDnd(context, on)) {
                    if (on) "Режим «Не беспокоить» включён." else "Режим «Не беспокоить» выключен."
                } else {
                    "Нужен доступ к режиму «Не беспокоить»."
                }
            }

            "время" in lower || "час" in lower -> "Сейчас " +
                java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + "."

            "правил" in lower || "расписан" in lower -> {
                val count = RuleRepository(context).currentRules().size
                "В расписании $count правил(а)."
            }

            "пароль" in lower -> "Сгенерировать пароль можно в «Инструменты → Пароль и QR»."

            "nfc" in lower || "касани" in lower -> "Обмен по NFC — раздел «Grape Link»: один телефон передаёт, второй принимает."

            "привет" in lower || "здоров" in lower -> "Привет! Я помогу с яркостью, громкостью, фонариком и расписанием."

            else -> "Пока я понимаю команды: «яркость 40», «громкость 0», «фонарик вкл», «не беспокоить», " +
                "«сколько правил», «время». Для свободных бесед подключите локальную модель Gemma — " +
                "она работает без интернета."
        }
    }
}
