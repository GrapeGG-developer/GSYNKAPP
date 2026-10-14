package com.gsynk.app.core

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Почта агенту — переписка с разработчиком прямо из приложения.
 *
 * Как это работает и почему именно так.
 *
 * Агент (тот, кто ведёт работу над проектом) не имеет публичного адреса в интернете:
 * он живёт в чате на своей стороне. Зато у нас есть общий репозиторий GitHub и вшитый
 * токен. Поэтому переписка идёт через **обсуждение (issue)** в репозитории: приложение
 * пишет туда сообщения, агент читает их, когда открывается работа, и отвечает там же —
 * а приложение подтягивает ответ и показывает его на экране.
 *
 * Это асинхронная связь, не мессенджер: ответ приходит не мгновенно, а когда агент
 * включится. Зато она работает без серверов, без чужих сервисов и без ввода токена:
 * человек просто пишет и жмёт «отправить».
 *
 * Важное про приватность: репозиторий публичный, значит вся переписка видна всем.
 * Поэтому перед отправкой текст чистится тем же санитайзером, что и отчёты
 * ([ReportSanitizer]): токены, почта и длинные числа вырезаются. И об этом прямо
 * написано в интерфейсе — человек должен знать, куда уходят слова.
 */
object AgentMail {

    /** Заголовок обсуждения, в котором живёт переписка. */
    const val THREAD_TITLE = "GSYNK ↔ агент: переписка"

    /** Метка приложения в сообщении: по ней отличаем свои сообщения от ответов агента. */
    const val APP_MARK = "[app]"

    private const val PREFS = "gsynk_agent"
    private const val KEY_ISSUE = "issue"
    private const val KEY_LAST = "last_seen"
    private const val KEY_CACHE = "cache"
    private const val MAX_TEXT = 4_000

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ————————————————————— части, которые проверяют тесты —————————————————————

    /** Одно сообщение переписки. */
    @Serializable
    data class Msg(
        val id: Long = 0,
        val body: String = "",
        @SerialName("created_at") val createdAt: String = "",
        /** Кто написал: приложение или агент. */
        val mine: Boolean = false
    ) {
        /** Текст без служебной метки — то, что показываем человеку. */
        val text: String get() = stripMark(body)
    }

    /** Ответ API GitHub по одному комментарию. */
    @Serializable
    private data class ApiComment(
        val id: Long = 0,
        val body: String = "",
        @SerialName("created_at") val createdAt: String = ""
    )

    @Serializable
    private data class ApiIssue(
        val number: Int = 0,
        val title: String = ""
    )

    /** Убирает служебную метку из текста сообщения. */
    fun stripMark(body: String): String = body.trim().removePrefix(APP_MARK).trim()

    /** Чьё это сообщение: приложение помечает свои меткой, всё остальное — ответ агента. */
    fun isMine(body: String): Boolean = body.trimStart().startsWith(APP_MARK)

    /** Готовит текст к отправке: чистит секреты, метит и ограничивает длину. */
    fun pack(text: String): String {
        val clean = ReportSanitizer.scrub(text.trim().replace("\r", ""))
        val cut = if (clean.length > MAX_TEXT) clean.take(MAX_TEXT - 1) + "…" else clean
        return APP_MARK + " " + cut
    }

    /** Разбирает ответ GitHub в список сообщений, от старых к новым. */
    fun parseComments(text: String): List<Msg> = runCatching {
        json.decodeFromString<List<ApiComment>>(text).map { comment ->
            Msg(
                id = comment.id,
                body = comment.body,
                createdAt = comment.createdAt,
                mine = isMine(comment.body)
            )
        }.sortedBy { it.id }
    }.getOrDefault(emptyList())

    /** Номер обсуждения из ответа поиска: берём то, у которого совпал заголовок. */
    fun parseIssueNumber(text: String, title: String = THREAD_TITLE): Int = runCatching {
        json.decodeFromString<List<ApiIssue>>(text)
            .firstOrNull { it.title.trim() == title }?.number ?: 0
    }.getOrDefault(0)

    /** Сколько ответов агента появилось после последнего просмотра. */
    fun unseen(messages: List<Msg>, lastSeen: Long): Int =
        messages.count { !it.mine && it.id > lastSeen }

    /** Последний идентификатор сообщения — его сохраняем как «просмотрено до». */
    fun lastId(messages: List<Msg>): Long = messages.maxOfOrNull { it.id } ?: 0L

    /**
     * Дата из формата GitHub («2026-10-14T18:41:12Z») в короткую человеческую
     * («14.10 21:41», время местное).
     */
    fun prettyStamp(iso: String, offsetMinutes: Int = 180): String = runCatching {
        val date = iso.substring(0, 10)
        val time = iso.substring(11, 16)
        val day = date.substring(8, 10)
        val month = date.substring(5, 7)
        val hour = (time.substring(0, 2).toInt() + offsetMinutes / 60) % 24
        val minute = (time.substring(3, 5).toInt() + offsetMinutes % 60) % 60
        day + "." + month + " " + "%02d".format(hour) + ":" + "%02d".format(minute)
    }.getOrDefault(iso.take(16))

    /** Отбирает хвост переписки: держим в памяти только последние сообщения. */
    fun tail(messages: List<Msg>, limit: Int = 200): List<Msg> =
        if (messages.size <= limit) messages else messages.takeLast(limit)

    /** Сколько знаков в тексте — предупреждаем, если человек пишет целый рассказ. */
    fun tooLong(text: String): Boolean = text.trim().length > MAX_TEXT

    // ————————————————————— сеть —————————————————————

    /** Номер обсуждения, сохранённый на телефоне (0 — ещё не создавали). */
    fun threadNumber(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ISSUE, 0)

    private fun rememberThread(context: Context, number: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_ISSUE, number).apply()
    }

    fun lastSeen(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST, 0L)

    fun markSeen(context: Context, id: Long) {
        if (id <= 0L) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST, id).apply()
    }

    /** Копия переписки на телефоне: показывает её даже без интернета. */
    fun cached(context: Context): List<Msg> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CACHE, "").orEmpty()
        if (raw.isBlank()) emptyList() else json.decodeFromString<List<Msg>>(raw)
    }.getOrDefault(emptyList())

    private fun cache(context: Context, messages: List<Msg>) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHE, json.encodeToString(tail(messages)))
                .apply()
        }
    }

    /** Итог обращения к сети: сообщения, номер обсуждения и что сказать человеку. */
    data class Pull(val messages: List<Msg>, val issue: Int, val message: String, val ok: Boolean)

    /** Забрать переписку. Если обсуждения ещё нет — создаём его. */
    fun pull(context: Context, repo: String = Keys.REPO, token: String = Keys.REPORT_TOKEN): Pull {
        if (repo.isBlank() || !repo.contains("/")) {
            return Pull(cached(context), threadNumber(context), "репозиторий не задан", false)
        }
        var number = threadNumber(context)
        if (number <= 0) {
            val found = findThread(repo, token)
            when {
                found.first > 0 -> {
                    number = found.first
                    rememberThread(context, number)
                }
                else -> {
                    val created = createThread(repo, token)
                    if (created.first <= 0) {
                        return Pull(cached(context), 0, "не удалось открыть обсуждение: " + created.second, false)
                    }
                    number = created.first
                    rememberThread(context, number)
                }
            }
        }

        val comments = get(repo, token, "/issues/$number/comments?per_page=100")
        if (!comments.first) {
            // Связь пропала — показываем то, что уже лежит на телефоне.
            return Pull(cached(context), number, comments.second, false)
        }
        val messages = parseComments(comments.second)
        if (messages.isEmpty()) {
            return Pull(cached(context), number, "переписки пока нет — напиши первым", true)
        }
        cache(context, messages)
        return Pull(messages, number, "получено сообщений: " + messages.size, true)
    }

    /** Отправить сообщение агенту. Возвращает текст для человека: ушло или почему нет. */
    fun send(
        context: Context,
        text: String,
        repo: String = Keys.REPO,
        token: String = Keys.REPORT_TOKEN
    ): Pair<Boolean, String> {
        val body = pack(text)
        if (stripMark(body).isBlank()) return false to "пустое сообщение"
        var number = threadNumber(context)
        if (number <= 0) {
            val pull = pull(context, repo, token)
            number = pull.issue
            if (number <= 0) return false to pull.message
        }
        val posted = post(repo, token, "/issues/$number/comments", body)
        return if (posted.first) {
            pull(context, repo, token)
            true to "отправлено · агент ответит, когда откроет работу"
        } else {
            false to posted.second
        }
    }

    // ————————————————————— обращения к GitHub —————————————————————

    private fun findThread(repo: String, token: String): Pair<Int, String> {
        // Ищем только открытые и закрытые задачи — обсуждение может быть любым.
        val response = get(repo, token, "/issues?state=all&per_page=100")
        if (!response.first) return 0 to response.second
        return parseIssueNumber(response.second) to "ok"
    }

    private fun createThread(repo: String, token: String): Pair<Int, String> {
        val body = buildString {
            append("Здесь GSYNK общается с агентом.\n\n")
            append("Что можно писать: чего не хватает, что сломалось, какие идеи. ")
            append("Сообщения приложения помечены меткой `[app]`, ответы агента — без неё.\n\n")
            append("⚠️ Обсуждение открытое: не пиши сюда пароли, номера карт и личные данные.")
        }
        val payload = """{"title":"$THREAD_TITLE","body":${quote(body)}}"""
        val posted = request(
            method = "POST",
            url = "https://api.github.com/repos/$repo/issues",
            token = token,
            body = payload
        )
        if (!posted.first) return 0 to posted.second
        val number = runCatching {
            json.decodeFromString<ApiIssue>(posted.second.trim()).number
        }.getOrDefault(0)
        return number to if (number > 0) "ok" else "обсуждение создано без номера"
    }

    private fun get(repo: String, token: String, path: String): Pair<Boolean, String> =
        request("GET", "https://api.github.com/repos/$repo$path", token, null)

    private fun post(repo: String, token: String, path: String, body: String): Pair<Boolean, String> =
        request("POST", "https://api.github.com/repos/$repo$path", token, """{"body":${quote(body)}}""")

    private fun quote(text: String): String = buildString {
        append('"')
        text.forEach { symbol ->
            when (symbol) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (symbol < ' ') append(' ') else append(symbol)
            }
        }
        append('"')
    }

    private fun request(
        method: String,
        url: String,
        token: String,
        body: String?
    ): Pair<Boolean, String> = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "GSYNK-Android")
            if (token.isNotBlank()) setRequestProperty("Authorization", "token $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        if (body != null) {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
        }
        val code = connection.responseCode
        val text = runCatching {
            (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.getOrDefault("")
        connection.disconnect()
        when {
            code in 200..299 -> true to text
            code == 401 -> false to "токен не принят (401)"
            code == 403 -> false to "упёрлись в лимит обращений GitHub (403)"
            code == 404 -> false to "обсуждение не найдено (404)"
            code == 410 -> false to "обсуждение удалено (410)"
            else -> false to "GitHub вернул $code: " + text.take(120)
        }
    }.getOrElse { error -> false to "нет соединения: " + (error.message ?: "неизвестно") }
}
