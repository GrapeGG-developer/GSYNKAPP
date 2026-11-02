package com.gsynk.app.core

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Сервер моделей GSYNK-AI и локальное хранилище скачанных .gsai.
 * Каталог: AI-models/index.json в репозитории GSYNK, файлы — в релизе AI-models.
 */
object GsaiServer {
    const val REPO = "GrapeGG-developer/GSYNKAPP"
    const val INDEX_URL = "https://raw.githubusercontent.com/$REPO/main/AI-models/index.json"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchIndex(): List<GsaiServerModel> = withContext(Dispatchers.IO) {
        val connection = (URL(INDEX_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "GSYNK-AI-models")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            runCatching { json.decodeFromString(GsaiIndex.serializer(), text).models }.getOrElse { emptyList() }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(url: String, onProgress: (Float) -> Unit = {}): ByteArray = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "GSYNK-AI-models")
        }
        try {
            require(connection.responseCode in 200..299) { "сервер ответил ${connection.responseCode}" }
            val total = connection.contentLengthLong
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var done = 0L
            connection.inputStream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    done += read
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
            out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

class GsaiStore(context: Context) {
    private val dir = File(context.filesDir, "gsai-models").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    private var cachedId: String? = null
    private var cachedModel: GsaiModel? = null

    private fun fileOf(id: String) = File(dir, "$id.gsai")
    private fun sidecarOf(id: String) = File(dir, "$id.json")

    fun installed(): List<GsaiServerModel> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                runCatching { json.decodeFromString(GsaiServerModel.serializer(), f.readText()) }.getOrNull()
            }
            ?.filter { fileOf(it.id).exists() }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun isInstalled(id: String): Boolean = fileOf(id).exists()

    fun save(entry: GsaiServerModel, bytes: ByteArray) {
        if (entry.sha256.isNotEmpty()) {
            require(GsaiServer.sha256(bytes) == entry.sha256) { "файл модели повреждён при скачивании" }
        }
        // проверяем, что пакет вообще открывается, до записи на диск
        GsaiPackage.parseMeta(bytes) ?: error("это не пакет GSYNK-AI")
        fileOf(entry.id).writeBytes(bytes)
        sidecarOf(entry.id).writeText(json.encodeToString(entry))
        if (cachedId == entry.id) {
            cachedId = null
            cachedModel = null
        }
    }

    fun delete(id: String) {
        fileOf(id).delete()
        sidecarOf(id).delete()
        if (cachedId == id) {
            cachedId = null
            cachedModel = null
        }
    }

    suspend fun load(id: String): GsaiModel = withContext(Dispatchers.Default) {
        cachedModel?.let { if (cachedId == id) return@withContext it }
        val model = GsaiPackage.load(fileOf(id).readBytes())
        cachedId = id
        cachedModel = model
        model
    }
}
