package com.gsynk.app.core

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновление «по воздуху» (OTA) через GitHub Releases.
 *
 * Приложение проверяет релизы своего же репозитория: если там лежит тег новее текущей
 * версии и в релизе прикреплён APK — GSYNK скачает его и предложит установку.
 * Никаких серверов не нужно: достаточно GitHub.
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
        val sha256: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Проверка последней версии. repo в формате «владелец/репозиторий». */
    fun check(context: Context, repo: String): Result {
        val current = currentVersion(context)
        if (repo.isBlank() || !repo.contains("/")) {
            return Result(
                ok = false,
                message = "Укажите репозиторий в виде владелец/репозиторий",
                currentTag = current
            )
        }
        return runCatching {
            val connection = (URL("https://api.github.com/repos/$repo/releases/latest")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "GSYNK-Updater")
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 12_000
                readTimeout = 12_000
            }
            val code = connection.responseCode
            if (code != 200) {
                connection.disconnect()
                return Result(
                    ok = false,
                    message = if (code == 404) "Релизы не найдены (или репозиторий закрыт)" else "GitHub ответил кодом $code",
                    currentTag = current
                )
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val release = json.decodeFromString(Release.serializer(), text)
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            val newer = compareVersions(release.tag, current) > 0
            Result(
                ok = true,
                message = when {
                    newer && apk != null -> "Доступна версия ${release.tag}"
                    newer && apk == null -> "Версия ${release.tag} есть, но APK не прикреплён к релизу"
                    else -> "Установлена последняя версия"
                },
                latestTag = release.tag,
                currentTag = current,
                updateAvailable = newer && apk != null,
                notes = release.body.take(1200),
                apkUrl = apk?.url.orEmpty(),
                pageUrl = release.htmlUrl,
                sha256 = apk?.digest.orEmpty().substringAfter("sha256:", "").trim()
            )
        }.getOrElse { error ->
            Result(
                ok = false,
                message = "Не удалось проверить: ${error.message ?: "нет сети"}",
                currentTag = current
            )
        }
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

    /** Скачать APK во внутренний кэш и вернуть путь. [onProgress] — проценты 0..100. */
    fun download(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit = {}
    ): java.io.File? = runCatching {
        val target = java.io.File(context.cacheDir, "gsynk-update.apk")
        if (target.exists()) target.delete()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "GSYNK-Updater")
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        val total = connection.contentLengthLong
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (total > 0) onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                }
                onProgress(100)
            }
        }
        connection.disconnect()
        if (target.length() > 100_000) target else null
    }.getOrNull()

    /** Можно ли ставить APK (разрешение «неизвестные источники»). */
    /** Открыть страницу репозитория в браузере. */
    fun openRepo(context: Context, repo: String) {
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    "https://github.com/$repo".toUri()
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
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
    fun install(context: Context, file: java.io.File): Boolean = runCatching {
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
