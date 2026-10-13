package com.gsynk.app.core

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV

/**
 * Надёжное чтение NFC-метки и второго телефона GSYNK.
 *
 * Порядок такой:
 *  1. Если система уже разобрала NDEF — берём его.
 *  2. Иначе читаем низкоуровнево по APDU (IsoDep, Type 4 Tag): именно так отдаёт
 *     данные телефон-«метка» с GSYNK. Штатный разбор Android на живых телефонах
 *     часто возвращает пустое сообщение — поэтому этот путь и есть основной.
 */
object NfcReader {

    /** Чем закончилось чтение — чтобы показать понятную причину. */
    sealed class Result {
        data class Ok(val message: NdefMessage) : Result()
        data class Failed(val reason: String) : Result()
    }

    fun read(tag: Tag): Result {
        systemNdef(tag)?.let { return Result.Ok(it) }

        val isoDep = runCatching { IsoDep.get(tag) }.getOrNull()
            ?: return Result.Failed("Метка без поддержки APDU (IsoDep)")

        return try {
            isoDep.connect()
            val payload = readType4(isoDep)
            Result.Ok(NdefMessage(payload))
        } catch (error: Throwable) {
            Result.Failed(error.message ?: "Обмен с меткой прерван")
        } finally {
            runCatching { isoDep.close() }
        }
    }

    /** Человекочитаемое описание тега — показываем на экране приёма. */
    fun describe(tag: Tag): String = buildList {
        runCatching { NfcA.get(tag)?.let { add("NfcA") } }
        runCatching { NfcB.get(tag)?.let { add("NfcB") } }
        runCatching { NfcF.get(tag)?.let { add("NfcF") } }
        runCatching { NfcV.get(tag)?.let { add("NfcV") } }
        runCatching { IsoDep.get(tag)?.let { add("IsoDep") } }
        runCatching { Ndef.get(tag)?.let { add("Ndef") } }
    }.joinToString(", ")

    private fun systemNdef(tag: Tag): NdefMessage? {
        val ndef = runCatching { Ndef.get(tag) }.getOrNull() ?: return null
        runCatching { ndef.cachedNdefMessage }.getOrNull()?.let { return it }
        return runCatching {
            ndef.connect()
            val message = ndef.ndefMessage
            ndef.close()
            message
        }.getOrNull()
    }

    // ————— NDEF Type 4 Tag поверх APDU —————

    private const val AID_NDEF = "D2760000850101"
    private const val SW_OK = 0x9000
    private const val MAX_CHUNK = 0xE0 // читаем по 224 байта, чтобы не упираться в лимиты

    private fun readType4(isoDep: IsoDep): ByteArray {
        isoDep.timeout = 2_500

        // 1) выбираем NDEF-приложение
        val selectApp = transceive(isoDep, apdu(0xA4, 0x04, 0x00, 0x00, hex(AID_NDEF) + byteArrayOf(0x00)))
        requireSw(selectApp, SW_OK, "Метка GSYNK не отвечает на выбор приложения")

        // 2) читаем Capability Container (файл E103)
        val ccSelect = transceive(isoDep, apdu(0xA4, 0x00, 0x0C, 0x00, hex("E103")))
        requireSw(ccSelect, SW_OK, "Не найден файл описания (CC)")
        val cc = transceive(isoDep, apdu(0xB0, 0x00, 0x00, 0x00, byteArrayOf(0x0F)))
        requireSw(cc, SW_OK, "Не удалось прочитать описание метки")

        val ndefFileId = parseNdefFileId(data(cc)) ?: throw IllegalStateException("В метке нет файла данных")

        // 3) читаем файл данных
        val fileSelect = transceive(isoDep, apdu(0xA4, 0x00, 0x0C, 0x00, ndefFileId))
        requireSw(fileSelect, SW_OK, "Не удалось открыть файл данных")

        val lengthResponse = transceive(isoDep, apdu(0xB0, 0x00, 0x00, 0x00, byteArrayOf(0x02)))
        requireSw(lengthResponse, SW_OK, "Не удалось прочитать длину данных")
        val lengthData = data(lengthResponse)
        require(lengthData.size >= 2) { "Пустой заголовок данных" }
        val length = ((lengthData[0].toInt() and 0xFF) shl 8) or (lengthData[1].toInt() and 0xFF)
        require(length in 1..8192) { "Данных в метке нет (длина $length)" }

        val out = java.io.ByteArrayOutputStream()
        var offset = 2
        var left = length
        while (left > 0) {
            val chunk = minOf(left, MAX_CHUNK)
            val response = transceive(
                isoDep,
                apdu(0xB0, ((offset shr 8) and 0xFF), offset and 0xFF, 0x00, byteArrayOf(chunk.toByte()))
            )
            requireSw(response, SW_OK, "Чтение прервано на позиции $offset")
            val body = data(response)
            if (body.isEmpty()) break
            out.write(body)
            offset += body.size
            left -= body.size
        }
        return out.toByteArray()
    }

    /** CC-файл: ищем TLV 0x04, внутри первые два байта — идентификатор файла данных. */
    private fun parseNdefFileId(cc: ByteArray): ByteArray? {
        var index = 0
        while (index + 1 < cc.size) {
            val tag = cc[index].toInt() and 0xFF
            val len = cc[index + 1].toInt() and 0xFF
            if (tag == 0x04 && len >= 6 && index + 2 + len <= cc.size) {
                return byteArrayOf(cc[index + 2], cc[index + 3])
            }
            index += 2 + len
        }
        return null
    }

    private fun apdu(ins: Int, p1: Int, p2: Int, le: Int, data: ByteArray = ByteArray(0)): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(0x00)             // CLA
        out.write(ins)
        out.write(p1)
        out.write(p2)
        if (data.isNotEmpty()) {
            out.write(data.size)
            out.write(data)
        }
        out.write(le)
        return out.toByteArray()
    }

    private fun transceive(isoDep: IsoDep, command: ByteArray): ByteArray =
        isoDep.transceive(command) ?: throw IllegalStateException("Нет ответа от метки")

    private fun requireSw(response: ByteArray, expected: Int, message: String) {
        val sw = sw(response)
        if (sw != expected) throw IllegalStateException("$message (код ${"%04X".format(sw)})")
    }

    private fun sw(response: ByteArray): Int {
        if (response.size < 2) return -1
        return ((response[response.size - 2].toInt() and 0xFF) shl 8) or
            (response[response.size - 1].toInt() and 0xFF)
    }

    private fun data(response: ByteArray): ByteArray =
        if (response.size <= 2) ByteArray(0) else response.copyOfRange(0, response.size - 2)

    fun hex(value: String): ByteArray {
        val clean = value.replace(" ", "")
        return ByteArray(clean.length / 2) { index ->
            ((Character.digit(clean[index * 2], 16) shl 4) or
                Character.digit(clean[index * 2 + 1], 16)).toByte()
        }
    }
}
