package com.gsynk.app.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.random.Random

/**
 * Передача файлов по касанию.
 *
 * NFC для файлов слишком тесный (пара килобайт), поэтому касание работает
 * как визитка: второй телефон получает адрес, порт и одноразовый код, а сам файл
 * едет по Wi-Fi. Совсем маленькие файлы (до 600 байт) уезжают прямо в метке — без сети.
 *
 * Файл принимается в «Загрузки/GSYNK», поэтому его сразу видно в проводнике.
 */
object FileLink {

    const val PORT = 45_992

    /** Больше этого размера по NFC не отправляем — включаем Wi-Fi. */
    const val INLINE_LIMIT = 600

    sealed interface State {
        data object Idle : State
        data class Offering(val name: String, val size: Long, val viaWifi: Boolean) : State
        data class Receiving(val name: String, val size: Long, val percent: Int) : State
        data class Ready(val name: String, val size: Long, val path: String) : State
        data class Failed(val reason: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var server: ServerSocket? = null
    private var job: Job? = null

    /** Скопировать содержимое в метку (маленький файл уезжает целиком). */
    fun inlineBase64(context: Context, uri: Uri, name: String, size: Long): String? = runCatching {
        if (size <= 0 || size > INLINE_LIMIT) return null
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        Base64.getEncoder().encodeToString(bytes)
    }.getOrNull()

    fun readInline(context: Context, name: String, size: Long, base64: String): Boolean = runCatching {
        val bytes = Base64.getDecoder().decode(base64)
        val saved = save(context, name, size.takeIf { it > 0 } ?: bytes.size.toLong()) { out ->
            out.write(bytes)
        }
        if (saved != null) {
            _state.value = State.Ready(name, bytes.size.toLong(), saved)
        } else {
            _state.value = State.Failed("Не удалось сохранить файл в «Загрузки»")
        }
        saved != null
    }.getOrElse {
        _state.value = State.Failed("Файл из метки повреждён: " + (it.message ?: "ошибка разбора"))
        false
    }

    /** Отправляющая сторона: поднять сервер и отдать ровно один файл по коду. */
    fun offer(context: Context, uri: Uri, file: PickedFile): String {
        stop()
        val token = (100_000 + Random.nextInt(900_000)).toString()
        _state.value = State.Offering(file.name, file.size, viaWifi = true)
        job = scope.launch {
            runCatching {
                val srv = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(PORT))
                }
                server = srv
                val client = srv.accept()
                client.tcpNoDelay = true
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
                val request = runCatching { reader.readLine() }.getOrNull().orEmpty()
                val parts = request.trim().split(' ')
                if (parts.getOrNull(0) != "GSYNK-FILE" || parts.getOrNull(1) != token) {
                    _state.value = State.Failed("Чужой запрос на файл — передача отменена")
                    client.close()
                    return@runCatching
                }
                val out = BufferedOutputStream(client.getOutputStream())
                val input = context.contentResolver.openInputStream(uri)
                if (input == null) {
                    _state.value = State.Failed("Файл больше недоступен")
                    client.close()
                    return@runCatching
                }
                input.use { source ->
                    source.copyTo(out, 64 * 1024)
                }
                out.flush()
                client.close()
                AppLog.event("Grape Link", "файл отправлен: ${file.name} (${file.size} Б)")
                _state.value = State.Ready(file.name, file.size, "отправлено")
            }.onFailure { error ->
                if (_state.value is State.Offering) {
                    _state.value = State.Failed("Файл не ушёл: " + (error.message ?: "сеть разорвана"))
                }
            }
        }
        return token
    }

    /** Принимающая сторона: скачать файл и положить в «Загрузки/GSYNK». */
    fun receive(context: Context, host: String, port: Int, token: String, name: String, size: Long) {
        stop()
        _state.value = State.Receiving(name, size, 0)
        job = scope.launch {
            runCatching {
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), 6_000)
                val out = BufferedOutputStream(socket.getOutputStream())
                out.write("GSYNK-FILE $token $size\n".toByteArray(StandardCharsets.UTF_8))
                out.flush()

                val input = BufferedInputStream(socket.getInputStream())
                var received = 0L
                var lastPercent = -1
                val saved = save(context, name, size) { destination ->
                    val buffer = ByteArray(64 * 1024)
                    while (received < size) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        destination.write(buffer, 0, read)
                        received += read
                        val percent = if (size > 0) ((received * 100) / size).toInt() else 0
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.value = State.Receiving(name, size, percent)
                        }
                    }
                }
                input.close()
                socket.close()
                if (saved == null) {
                    _state.value = State.Failed("Не удалось сохранить файл в «Загрузки»")
                    return@runCatching
                }
                if (size > 0 && received < size) {
                    _state.value = State.Failed("Файл пришёл не целиком: получено $received из $size байт")
                    return@runCatching
                }
                AppLog.event("Grape Link", "файл принят: $name ($received Б)")
                _state.value = State.Ready(name, received, saved)
            }.onFailure { error ->
                if (_state.value is State.Receiving) {
                    _state.value = State.Failed("Файл не принят: " + (error.message ?: "сеть разорвана"))
                }
            }
        }
    }

    fun stop() {
        runCatching { job?.cancel() }
        job = null
        runCatching { server?.close() }
        server = null
    }

    fun clear() {
        stop()
        _state.value = State.Idle
    }

    fun shutdown() {
        stop()
        runCatching { scope.cancel() }
    }

    // ————— файл, выбранный человеком —————

    data class PickedFile(val uri: Uri, val name: String, val size: Long)

    fun describe(context: Context, uri: Uri): PickedFile? = runCatching {
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
            PickedFile(uri, name?.takeIf { it.isNotBlank() } ?: "файл-gsynk", size)
        }
    }.getOrNull()

    /** Кладём принятое в «Загрузки/GSYNK» — файл сразу виден в проводнике. */
    private fun save(
        context: Context,
        name: String,
        size: Long,
        write: (OutputStream) -> Unit
    ): String? = runCatching {
        val safeName = name.replace('/', '_').replace('\\', '_').ifBlank { "файл-gsynk" }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeName)
            put(MediaStore.Downloads.MIME_TYPE, mimeOf(safeName))
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GSYNK")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { stream -> write(stream) } ?: return null
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        "Загрузки/GSYNK/$safeName"
    }.getOrNull()

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "ogg", "opus" -> "audio/ogg"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "apk" -> "application/vnd.android.package-archive"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}
