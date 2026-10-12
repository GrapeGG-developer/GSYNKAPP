package com.gsynk.app.core

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.os.Bundle
import java.nio.charset.StandardCharsets

/**
 * Обмен по NFC («Grape Link»).
 *
 * Важно и честно: NFC — это канал на считанные килобайты и пару сантиметров.
 * Он идеален для мгновенного «касания-рукопожатия» и передачи небольших данных
 * (текст, ссылка, визитка, вызов в игру). Большие файлы им передать нельзя физически —
 * для этого нужен Bluetooth/Wi-Fi Direct (следующий шаг развития).
 */
object NfcLink {

    const val MIME_TEXT = "text/plain"
    const val MIME_URL = "text/uri-list"
    const val MIME_CONTACT = "text/vcard"
    const val MIME_GAME = "application/vnd.gsynk.game"

    /** Типы полезной нагрузки приложения. */
    const val KIND_TEXT = "text"
    const val KIND_URL = "url"
    const val KIND_CONTACT = "contact"
    const val KIND_GAME = "game"

    data class Payload(
        val kind: String,
        val title: String,
        val body: String
    )

    data class GameInvite(
        val game: String,
        val from: String,
        val seed: Long
    )

    fun isSupported(adapter: NfcAdapter?): Boolean = adapter != null

    fun isEnabled(adapter: NfcAdapter?): Boolean = runCatching {
        adapter?.isEnabled == true
    }.getOrDefault(false)

    fun openNfcSettingsIntent(): android.content.Intent =
        android.content.Intent(android.provider.Settings.ACTION_NFC_SETTINGS)

    // ————— сборка сообщений —————

    fun buildMessage(payload: Payload, appPackage: String): NdefMessage {
        val record = when (payload.kind) {
            KIND_URL -> NdefRecord.createUri(payload.body)
            KIND_CONTACT -> NdefRecord(
                NdefRecord.TNF_MIME_MEDIA,
                MIME_CONTACT.toByteArray(StandardCharsets.US_ASCII),
                ByteArray(0),
                payload.body.toByteArray(StandardCharsets.UTF_8)
            )

            KIND_GAME -> NdefRecord(
                NdefRecord.TNF_MIME_MEDIA,
                MIME_GAME.toByteArray(StandardCharsets.US_ASCII),
                ByteArray(0),
                payload.body.toByteArray(StandardCharsets.UTF_8)
            )

            else -> NdefRecord.createTextRecord("ru", "${payload.title}|${payload.body}".take(700))
        }
        val appRecord = NdefRecord.createApplicationRecord(appPackage)
        return NdefMessage(arrayOf(record, appRecord))
    }

    fun buildGameInvite(invite: GameInvite, appPackage: String): NdefMessage {
        val body = "${invite.game};${invite.from};${invite.seed}"
        val payload = Payload(KIND_GAME, "GSYNK-игра", body)
        return buildMessage(payload, appPackage)
    }

    // ————— разбор сообщений —————

    fun parse(message: NdefMessage?): Payload? {
        if (message == null) return null
        for (record in message.records) {
            val parsed = parseRecord(record) ?: continue
            return parsed
        }
        return null
    }

    private fun parseRecord(record: NdefRecord): Payload? = runCatching {
        when {
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI) -> {
                val uri = record.toUri()?.toString() ?: return null
                Payload(KIND_URL, "Ссылка", uri)
            }

            record.tnf == NdefRecord.TNF_MIME_MEDIA &&
                record.type.contentEquals(MIME_CONTACT.toByteArray(StandardCharsets.US_ASCII)) -> {
                Payload(KIND_CONTACT, "Визитка", String(record.payload, StandardCharsets.UTF_8))
            }

            record.tnf == NdefRecord.TNF_MIME_MEDIA &&
                record.type.contentEquals(MIME_GAME.toByteArray(StandardCharsets.US_ASCII)) -> {
                Payload(KIND_GAME, "Вызов в игру", String(record.payload, StandardCharsets.UTF_8))
            }

            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                val status = record.payload.firstOrNull()?.toInt() ?: 0
                val langLen = status and 0x3F
                val text = if (record.payload.size > langLen + 1) {
                    String(record.payload, langLen + 1, record.payload.size - langLen - 1, StandardCharsets.UTF_8)
                } else {
                    ""
                }
                val parts = text.split("|", limit = 2)
                Payload(KIND_TEXT, parts.firstOrNull().orEmpty(), parts.getOrNull(1).orEmpty())
            }

            else -> null
        }
    }.getOrNull()

    /** Разбор вызова в игру из текстовой нагрузки. */
    fun parseGameInvite(body: String): GameInvite? = runCatching {
        val parts = body.split(";")
        GameInvite(
            game = parts.getOrNull(0).orEmpty(),
            from = parts.getOrNull(1).orEmpty(),
            seed = parts.getOrNull(2)?.toLongOrNull() ?: System.currentTimeMillis()
        )
    }.getOrNull()

    /** Фильтры для reader-mode: слушаем все теги, пока экран открыт. */
    fun readerFilter(): Array<android.content.IntentFilter> = arrayOf(
        android.content.IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
        android.content.IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
        android.content.IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
    )
}
