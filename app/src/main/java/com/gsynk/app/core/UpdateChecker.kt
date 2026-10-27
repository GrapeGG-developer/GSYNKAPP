package com.gsynk.app.core

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Обновление «по воздуху» (OTA) через GitHub Releases.
 *
 * GIOUI: проверка перестала «лениться» и перестала упираться в лимит GitHub.
 * Что здесь важно и почему:
 *
 *  1. **Токен.** Без него GitHub пускает 60 запросов в час с одного адреса — а телефон
 *     почти всегда сидит за общим адресом оператора. Отсюда и «обновления приходят неохотно»:
 *     запросы тихо отбивались кодом 403. С вшитым токеном лимит — 5000 в час.
 *  2. **ETag.** Приложение запоминает отпечаток последнего ответа и шлёт `If-None-Match`.
 *     Ответ «не изменилось» (304) бесплатный: лимит он не тратит вообще.
 *  3. **Повторы.** Сеть на телефоне мигает: одна попытка ничего не значит. Делаем три.
 *  4. **Запасной путь.** Если API почему-то закрыт, версию всё равно можно увидеть:
 *     запрос к странице `releases/latest` возвращает адрес с тегом последнего релиза.
 *
 * Скачивание тоже стало упрямее: три попытки, продолжение с места обрыва, проверка того,
 * что скачался настоящий APK, и сверка sha256 с тем, что сообщил GitHub.
 */
object UpdateChecker {

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tag: String = "",
        @SerialName("name") val name: String = "",
        @SerialName("body") val body: String = "",
        @SerialName("html_url") val htmlUrl: String = "",
        val assets: List<Asset> = emptyList()
    )

    @Serializable
    private data class Asset(
        val name: String = "",
        @SerialName("browser_download_url") val url: String = "",
        val size: Long = 0,
        /** «sha256:…» — GitHub отдаёт это поле у файлов релиза. */
        val digest: String = ""
    )

    data class Result(
        val ok: Boolean,
        val message: String,
        val latestTag: String = "",
        val currentTag: String = "",
        val updateAvailable: Boolean = false,
        val notes: String = "",
        val apkUrl: String = "",
        val pageUrl: String = "",
        /** sha256 файла APK из GitHub: по нему проверяем, что скачалось то же самое. */
        val sha256: String = "",
        /** Ответ «ничего не изменилось» (304): данные те же, запрос лимит не потратил. */
        val unchanged: Boolean = false,
        /** Версию увидели по странице релиза, без API: скачивать придётся в браузере. */
        val webOnly: Boolean = false,
        /** Лимит обращений исчерпан до этого времени (мс, 0 — не исчерпан). */
        val rateLimitedUntil: Long = 0L,
        /** Сколько попыток понадобилось: для журнала и для человека в диагностике. */
        val attempts: Int = 1
    )

    private val json = Json { ignoreUnknownKeys = true }

    private const val PREFS = "gsynk_update"
    private const val KEY_ETAG = "etag"
    private const val KEY_CACHE = "cache"

    // ————————————————————— проверка —————————————————————

    /**
     * Проверка последней версии. [token] необязателен, но с ним лимит в 80 раз выше,
     * поэтому приложение всегда передаёт вшитый токен.
     */
    fun check(
        context: Context,
        repo: String,
        token: String = ""
    ): Result {
        val current = currentVersion(context)
        if (repo.isBlank() || !repo.contains("/")) {
            return Result(ok = false, message = "Укажите репозиторий в виде владелец/репозиторий", currentTag = current)
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val etag = prefs.getString(KEY_ETAG, "").orEmpty()
        val url = "https://api.github.com/repos/$repo/releases/latest"

        var lastError = "нет соединения"
        var rateLimitedUntil = 0L

        // Три попытки: сеть на телефоне мигает, и одна неудача ничего не значит.
        repeat(3) { attempt ->
            val response = request(url, token, etag)
            when (response.kind) {
                Kind.OK -> {
                    val parsed = parse(response.body, current)
                    prefs.edit().putString(KEY_ETAG, response.etag).putString(KEY_CACHE, response.body).apply()
                    return parsed.copy(attempts = attempt + 1)
                }

                Kind.NOT_MODIFIED -> {
                    // Ничего не изменилось: отвечаем из памяти, лимит не потрачен.
                    val cached = prefs.getString(KEY_CACHE, "").orEmpty()
                    val parsed = if (cached.isBlank()) {
                        Result(ok = true, message = "проверено: изменений нет", currentTag = current, unchanged = true)
                    } else {
                        parse(cached, current).copy(message = "проверено: изменений нет", unchanged = true)
                    }
                    return parsed.copy(attempts = attempt + 1)
                }

                Kind.ERROR -> lastError = response.note
                Kind.LIMITED -> {
                    rateLimitedUntil = response.rateLimitReset
                    lastError = response.note
                }
            }
            if (attempt < 2) Thread.sleep(700L * (attempt + 1))
        }

        // API не ответил — пробуем увидеть версию по странице релиза.
        val web = checkByPage(repo, current)
        return if (web != null) {
            web.copy(attempts = 3, rateLimitedUntil = rateLimitedUntil)
        } else {
            Result(
                ok = false,
                message = lastError,
                currentTag = current,
                rateLimitedUntil = rateLimitedUntil,
                attempts = 3
            )
        }
    }

    private enum class Kind { OK, NOT_MODIFIED, ERROR, LIMITED }

    private data class Response(
        val kind: Kind,
        val body: String = "",
        val etag: String = "",
        val note: String = "",
        val rateLimitReset: Long = 0L
    )

    private fun request(url: String, token: String, etag: String): Response = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "GSYNK-Updater")
            setRequestProperty("Accept", "application/vnd.github+json")
            if (token.isNotBlank()) setRequestProperty("Authorization", "token $token")
            if (etag.isNotBlank()) setRequestProperty("If-None-Match", etag)
            connectTimeout = 12_000
            readTimeout = 12_000
        }
        val code = connection.responseCode
        val freshEtag = connection.getHeaderField("ETag").orEmpty()
        val remaining = connection.getHeaderField("X-RateLimit-Remaining")?.trim()?.toIntOrNull() ?: -1
        val resetSeconds = connection.getHeaderField("X-RateLimit-Reset")?.trim()?.toLongOrNull() ?: 0L
        val text = runCatching {
            (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.getOrDefault("")
        connection.disconnect()

        when {
            code == 304 -> Response(Kind.NOT_MODIFIED, etag = etag)
            code in 200..299 -> Response(Kind.OK, body = text, etag = freshEtag)
            code == 403 || code == 429 || remaining == 0 -> Response(
                Kind.LIMITED,
                note = if (resetSeconds > 0L) {
                    "GitHub притормозил: обращения к нему возобновятся позже"
                } else {
                    "GitHub отказал в обращении (код " + code + ")"
                },
                rateLimitReset = if (resetSeconds > 0L) resetSeconds * 1000L else 0L
            )
            code == 404 -> Response(Kind.ERROR, note = "Релизы не найдены (или репозиторий закрыт)")
            else -> Response(Kind.ERROR, note = "GitHub ответил кодом " + code)
        }
    }.getOrElse { Response(Kind.ERROR, note = "нет соединения: " + (it.message ?: "неизвестно")) }

    /**
     * Запасной путь без API: страница `releases/latest` сама переадресует на последний
     * релиз, и в адресе виден тег. Скачать по ней нельзя — зато видно, что версия есть.
     */
    private fun checkByPage(repo: String, current: String): Result? = runCatching {
        val connection = (URL("https://github.com/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "GSYNK-Updater")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        val code = connection.responseCode
        val finalUrl = connection.url.toString()
        connection.disconnect()
        if (code !in 200..399) return@runCatching null
        val tag = finalUrl.substringAfter("/releases/tag/", "").trim().trimEnd('/')
        if (tag.isBlank()) return@runCatching null
        val newer = compareVersions(tag, current) > 0
        Result(
            ok = true,
            message = if (newer) {
                "В репозитории есть версия $tag — её можно скачать со страницы релиза"
            } else {
                "Установлена последняя версия"
            },
            latestTag = tag,
            currentTag = current,
            updateAvailable = newer,
            pageUrl = "https://github.com/$repo/releases/tag/$tag",
            webOnly = true
        )
    }.getOrNull()

    /**
     * Разбор ответа API в результат для интерфейса.
     * Открыт для тестов внутри модуля: сеть в тестах не поднимаем, а вот разбор
     * настоящего ответа GitHub проверять надо — на нём держится вся панель.
     */
    internal fun parse(text: String, current: String): Result {
        val release = json.decodeFromString(Release.serializer(), text)
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        val newer = compareVersions(release.tag, current) > 0
        return Result(
            ok = true,
            message = when {
                newer && apk != null -> "Доступна версия " + release.tag
                newer && apk == null -> "Версия " + release.tag + " есть, но APK не прикреплён к релизу"
                else -> "Установлена последняя версия"
            },
            latestTag = release.tag,
            currentTag = current,
            updateAvailable = newer && apk != null,
            notes = release.body.take(1600),
            apkUrl = apk?.url.orEmpty(),
            pageUrl = release.htmlUrl,
            sha256 = apk?.digest.orEmpty().substringAfter("sha256:", "").trim()
        )
    }

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    }.getOrDefault("0")

    /** Сравнение версий вида 1.3.0 / v1.3 / 1.3.0-beta. */
    fun compareVersions(a: String, b: String): Int {
        fun parts(raw: String): List<Int> = raw.trim()
            .removePrefix("v")
            .substringBefore('-')
            .split('.')
            .mapNotNull { it.filter { ch -> ch.isDigit() }.toIntOrNull() }

        val pa = parts(a)
        val pb = parts(b)
        val size = maxOf(pa.size, pb.size)
        for (i in 0 until size) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }

    // ————————————————————— скачивание —————————————————————

    /** Итог скачивания: файл, человеческое объяснение и признак успеха. */
    data class Download(val file: File?, val message: String) {
        val ok: Boolean get() = file != null
    }

    /**
     * Скачать APK. Три попытки, продолжение с места обрыва и проверка того, что скачался
     * настоящий APK: первая пара байт у любого zip-архива — «PK».
     */
    fun download(
        context: Context,
        url: String,
        sha256: String = "",
        onProgress: (Int) -> Unit = {}
    ): Download {
        if (url.isBlank()) return Download(null, "нет ссылки на APK")
        val target = File(context.cacheDir, "gsynk-update.apk")
        val partial = File(context.cacheDir, "gsynk-update.part")
        var lastNote = "не удалось скачать"

        repeat(3) { attempt ->
            val started = fetch(url, partial, onProgress)
            if (!started.ok) {
                lastNote = started.message
                return@repeat
            }
            // Проверяем, что это действительно APK, а не страница ошибки.
            val head = runCatching {
                partial.inputStream().use { input ->
                    val buffer = ByteArray(2)
                    input.read(buffer)
                    String(buffer, Charsets.US_ASCII)
                }
            }.getOrDefault("")
            if (head != "PK") {
                partial.delete()
                lastNote = "скачался не APK (сеть подменила содержимое)"
                return@repeat
            }
            if (partial.length() < 1_000_000L) {
                lastNote = "файл неполный: " + (partial.length() / 1024) + " КБ"
                return@repeat
            }
            if (sha256.isNotBlank() && !sha256.equals(digestOf(partial), ignoreCase = true)) {
                partial.delete()
                lastNote = "сумма файла не совпала с GitHub — качаю заново"
                return@repeat
            }
            if (target.exists()) target.delete()
            if (partial.renameTo(target)) {
                onProgress(100)
                return Download(target, "скачано " + (target.length() / 1024 / 1024) + " МБ")
            }
            lastNote = "не удалось сохранить файл"
        }

        partial.delete()
        return Download(null, lastNote)
    }

    /** Одна попытка загрузки: если часть файла уже есть — продолжаем её. */
    private fun fetch(url: String, partial: File, onProgress: (Int) -> Unit): Download = runCatching {
        val have = if (partial.exists()) partial.length() else 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "GSYNK-Updater")
            if (have > 0L) setRequestProperty("Range", "bytes=" + have + "-")
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            return Download(null, "сервер ответил кодом " + code)
        }
        // Сервер может не поддержать продолжение — тогда начинаем с нуля.
        val resuming = code == 206 && have > 0L
        if (!resuming && partial.exists()) partial.delete()
        val already = if (resuming) have else 0L
        val total = connection.contentLengthLong.let { if (it > 0L) it + already else -1L }
        connection.inputStream.use { input ->
            java.io.RandomAccessFile(partial, "rw").use { file ->
                file.seek(already)
                val buffer = ByteArray(64 * 1024)
                var copied = already
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    file.write(buffer, 0, read)
                    copied += read
                    if (total > 0L) onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                }
            }
        }
        connection.disconnect()
        Download(partial, "ok")
    }.getOrElse { Download(null, "обрыв связи: " + (it.message ?: "неизвестно")) }

    /** sha256 файла — для сверки с тем, что сообщил GitHub. */
    fun digestOf(file: File): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }.getOrDefault("")

    // ————————————————————— установка —————————————————————

    /** Открыть страницу репозитория в браузере. */
    fun openRepo(context: Context, repo: String) {
        open(context, "https://github.com/$repo")
    }

    /** Открыть любую ссылку в браузере. */
    fun open(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openInstallSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri()
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Запустить установку скачанного APK. */
    fun install(context: Context, file: File): Boolean = runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)
}
