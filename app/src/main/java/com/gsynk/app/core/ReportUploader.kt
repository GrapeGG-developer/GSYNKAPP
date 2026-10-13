package com.gsynk.app.core

import android.content.Context
import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Отправка отчётов о сбоях в репозиторий GitHub.
 *
 * Работает только если пользователь сам включил отправку и вставил свой токен
 * с правом записи в репозиторий. Токен хранится на устройстве рядом с настройками,
 * никуда больше не передаётся и используется исключительно для загрузки отчёта
 * в папку `crash-reports/` того же репозитория, откуда приходят обновления.
 */
/**
 * Чистка текста отчёта перед отправкой.
 *
 * Отчёт уходит в интернет, поэтому из него вырезается всё, что похоже на секреты:
 * токены GitHub, пароли из инструментов, номера телефонов и длинные строки цифр.
 * Правило простое: если сомневаешься — не отправляй.
 */
object ReportSanitizer {

    private val tokenPattern = Regex("gh[pousr]_[A-Za-z0-9]{20,}")
    private val longDigits = Regex("\\b\\d{7,}\\b")
    private val emailPattern = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    /** Скрывает возможные секреты, оставляя структуру отчёта читаемой. */
    fun scrub(text: String): String = text
        .replace(tokenPattern, "[токен скрыт]")
        .replace(emailPattern, "[адрес скрыт]")
        .replace(longDigits, "[число скрыто]")

    /** Есть ли в тексте что-то, что нельзя отправлять. */
    fun looksSecret(text: String): Boolean =
        tokenPattern.containsMatchIn(text) || emailPattern.containsMatchIn(text)
}

object ReportUploader {

    private const val FOLDER = "crash-reports"
    private const val MARKER = ".uploaded"

    data class Result(val uploaded: Int, val failed: Int, val message: String)

    /** Отправляет все ещё не отправленные отчёты. */
    fun upload(context: Context, repo: String, token: String): Result {
        if (repo.isBlank() || !repo.contains("/")) {
            return Result(0, 0, "Укажите репозиторий вида владелец/репозиторий")
        }
        if (token.isBlank()) {
            return Result(0, 0, "Нужен токен GitHub с правом записи")
        }

        val reports = AppLog.reports(context).filterNot { it.file.name == MARKER }
        if (reports.isEmpty()) return Result(0, 0, "Отчётов нет")

        var uploaded = 0
        var failed = 0
        val errors = mutableListOf<String>()

        reports.forEach { item ->
            val body = ReportSanitizer.scrub(AppLog.read(item.file))
            val response = put(repo, token, "${FOLDER}/${item.file.name}", body)
            when {
                response.first -> {
                    uploaded++
                    markUploaded(item.file)
                }
                // файл уже лежит в репозитории — значит он отправлен;
                // раньше это считалось ошибкой и сыпало лишние сообщения
                response.second.contains("422") -> {
                    markUploaded(item.file)
                }
                else -> {
                    failed++
                    if (errors.size < 2) errors.add("${item.file.name}: ${response.second}")
                }
            }
        }

        // журнал событий уходит одним файлом на день: имя постоянное,
        // поэтому повторная отправка просто подтверждает, что он уже там
        val journal = ReportSanitizer.scrub(AppLog.journalTail(context, 200))
        if (journal.isNotBlank()) {
            val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                .format(java.util.Date())
            val response = put(repo, token, "$FOLDER/journal-$day.txt", journal)
            if (!response.first && !response.second.contains("422")) {
                if (errors.size < 2) errors.add("журнал: ${response.second}")
            }
        }

        val message = when {
            uploaded == 0 -> "Не удалось отправить: ${errors.firstOrNull() ?: "нет связи"}"
            failed == 0 -> "Отправлено отчётов: $uploaded"
            else -> "Отправлено: $uploaded, ошибок: $failed"
        }
        AppLog.event("Reports", message)
        return Result(uploaded, failed, message)
    }

    /** Один отчёт по кнопке — чтобы не ждать следующего сбоя. */
    fun uploadOne(context: Context, repo: String, token: String, file: File): Result {
        val text = ReportSanitizer.scrub(AppLog.read(file))
        val response = put(repo, token, "$FOLDER/${file.name}", text)
        return if (response.first) {
            markUploaded(file)
            Result(1, 0, "Отчёт отправлен: ${file.name}")
        } else {
            Result(0, 1, "Ошибка отправки: ${response.second}")
        }
    }

    private fun markUploaded(file: File) {
        runCatching {
            val marker = File(file.parentFile, "${file.name}.$MARKER").apply { createNewFile() }
            marker.setLastModified(System.currentTimeMillis())
        }
    }

    /** Помечался ли файл как отправленный. */
    fun isUploaded(file: File): Boolean =
        File(file.parentFile, "${file.name}.$MARKER").exists()

    /**
     * PUT в GitHub Contents API. Возвращает (успех, описание).
     * Никаких исключений наружу — сеть есть сеть.
     */
    private fun put(repo: String, token: String, path: String, content: String): Pair<Boolean, String> =
        runCatching {
            val payload: JsonObject = buildJsonObject {
                put("message", JsonPrimitive("GSYNK: отчёт ${path.substringAfterLast('/')}"))
                put("content", JsonPrimitive(Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)))
            }

            val connection = (URL("https://api.github.com/repos/$repo/contents/$path").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Authorization", "token $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "GSYNK-Android")
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                it.write(Json.encodeToString(JsonObject.serializer(), payload))
            }

            val code = connection.responseCode
            val text = runCatching {
                (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { reader -> reader.readText() }
                    .orEmpty()
            }.getOrDefault("")
            connection.disconnect()

            when {
                code in 200..299 -> true to "ok"
                code == 401 -> false to "токен не принят (401)"
                code == 404 -> false to "репозиторий не найден (404)"
                code == 422 -> false to "файл уже существует (422)"
                else -> false to "GitHub вернул $code: ${text.take(120)}"
            }
        }.getOrElse { error -> false to (error.message ?: "нет соединения") }
}
