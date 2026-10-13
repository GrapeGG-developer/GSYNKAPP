package com.gsynk.app.core

import android.content.Context
import android.nfc.NdefMessage
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.io.File

/**
 * Телефон как NDEF-метка (Host Card Emulation, Type 4 Tag).
 *
 * Раньше в Android был Beam («касание для передачи»), но в Android 16 публичные
 * методы передачи убрали. Актуальный способ — представить свой телефон обычной
 * NFC-меткой: второй телефон читает её штатным образом, как будто это наклейка.
 *
 * Реализован минимальный апплет NDEF Type 4 Tag: выбор AID, чтение CC-файла
 * и чтение файла NDEF. Всё, что кладёт приложение через [store], читается на
 * другом телефоне через [android.nfc.tech.Ndef] или любым NFC-сканером.
 */
class NdefHceService : HostApduService() {

    private var selected: Int = FILE_NONE

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: return sw(SW_WRONG_LENGTH)
        if (apdu.size < 4) return sw(SW_WRONG_LENGTH)
        return when (apdu[1].toInt() and 0xFF) {
            INS_SELECT -> handleSelect(apdu)
            INS_READ_BINARY -> handleRead(apdu)
            else -> sw(SW_INS_NOT_SUPPORTED)
        }
    }

    override fun onDeactivated(reason: Int) {
        selected = FILE_NONE
    }

    // ————— APDU: выбор приложения и файлов —————

    private fun handleSelect(apdu: ByteArray): ByteArray {
        val lc = if (apdu.size > 4) apdu[4].toInt() and 0xFF else 0
        if (lc == 0 || apdu.size < 5 + lc) return sw(SW_FILE_NOT_FOUND)
        val name = apdu.copyOfRange(5, 5 + lc).toHex()
        selected = when {
            name.startsWith(AID_NDEF) -> FILE_NONE
            name == FILE_ID_CC -> FILE_CC
            name == FILE_ID_NDEF -> FILE_NDEF
            else -> return sw(SW_FILE_NOT_FOUND)
        }
        return sw(SW_OK)
    }

    private fun handleRead(apdu: ByteArray): ByteArray {
        val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
        val le = if (apdu.size > 4) apdu[4].toInt() and 0xFF else 0
        val want = if (le == 0) 256 else le

        val file = when (selected) {
            FILE_CC -> ccFile()
            FILE_NDEF -> ndefFile()
            else -> return sw(SW_FILE_NOT_FOUND)
        }
        if (offset >= file.size) return sw(SW_WRONG_PARAMS)
        val end = minOf(offset + want, file.size)
        return file.copyOfRange(offset, end) + sw(SW_OK)
    }

    /** Capability Container: описание нашего файла NDEF. */
    private fun ccFile(): ByteArray = byteArrayOf(
        0x00, 0x0F,                     // длина CC
        0x20,                           // версия 2.0
        0x00, 0x3B,                     // максимум байт в команде
        0x00, 0x34,                     // максимум байт в ответе
        0x04, 0x06,                     // TLV: NDEF File Control
        0xE1.toByte(), 0x04,            // идентификатор файла NDEF
        MAX_NDEF.toByte(), 0x00,        // максимальный размер файла NDEF
        0x00,                           // чтение — свободно
        0x00                            // запись — запрещена
    )

    /** Файл NDEF: два байта длины + само сообщение. */
    private fun ndefFile(): ByteArray {
        val payload = currentPayload()
        val length = payload.size.coerceAtMost(MAX_NDEF)
        return byteArrayOf(
            ((length shr 8) and 0xFF).toByte(),
            (length and 0xFF).toByte()
        ) + payload.copyOf(length)
    }

    private fun currentPayload(): ByteArray = runCatching {
        val file = payloadFile(this)
        if (file.exists()) file.readBytes() else ByteArray(0)
    }.getOrDefault(ByteArray(0))

    private fun sw(code: Int): ByteArray =
        byteArrayOf(((code shr 8) and 0xFF).toByte(), (code and 0xFF).toByte())

    private fun ByteArray.toHex(): String {
        val builder = StringBuilder(size * 2)
        for (byte in this) builder.append(String.format("%02X", byte.toInt() and 0xFF))
        return builder.toString()
    }

    companion object {
        const val AID_NDEF = "D2760000850101"
        const val FILE_ID_CC = "E103"
        const val FILE_ID_NDEF = "E104"

        /** Больше одного килобайта по NFC не передаём — это канал для коротких данных. */
        const val MAX_NDEF = 0x0400

        private const val FILE_NONE = 0
        private const val FILE_CC = 1
        private const val FILE_NDEF = 2

        private const val INS_SELECT = 0xA4
        private const val INS_READ_BINARY = 0xB0

        private const val SW_OK = 0x9000
        private const val SW_WRONG_LENGTH = 0x6700
        private const val SW_FILE_NOT_FOUND = 0x6A82
        private const val SW_WRONG_PARAMS = 0x6B00
        private const val SW_INS_NOT_SUPPORTED = 0x6D00

        private fun payloadFile(context: Context) = File(context.filesDir, "grape_link_ndef.bin")

        /** Подготовить данные, которые телефон будет отдавать при касании. */
        /** Включить или выключить эмуляцию карты (компонент HCE). */
        fun setEnabled(context: Context, enabled: Boolean) {
            runCatching {
                val component = android.content.ComponentName(context, NdefHceService::class.java)
                val state = if (enabled) {
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                context.packageManager.setComponentEnabledSetting(
                    component,
                    state,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
            }
        }

        fun store(context: Context, message: NdefMessage) {
            runCatching { payloadFile(context).writeBytes(message.toByteArray()) }
        }

        /** Убрать данные: телефон больше не «метка». */
        fun clear(context: Context) {
            runCatching { payloadFile(context).delete() }
        }

        fun hasPayload(context: Context): Boolean = runCatching {
            val file = payloadFile(context)
            file.exists() && file.length() > 0
        }.getOrDefault(false)
    }
}
