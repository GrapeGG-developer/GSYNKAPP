package com.gsynk.app.core

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

/**
 * Запись на обычную NFC-метку (наклейку, брелок, карточку домофона).
 *
 * Раньше GSYNK только читала чужие метки и отдавала себя как метку. Теперь наоборот:
 * можно взять пустую наклейку и записать на неё что угодно из GSYNK — текст, ссылку,
 * приглашение в GTBOI или сценарий «покажи салют». Дальше любое касание этой наклейки
 * кто угодно прочитает обычным телефоном, приложение для этого не нужно.
 */
object NfcTagWriter {

    sealed interface Result {
        data class Ok(val bytes: Int) : Result
        data class Failed(val reason: String) : Result
    }

    /** Максимум, который реально живёт на недорогих метках NTAG213/215. */
    const val TYPICAL_CAPACITY = 492

    fun write(tag: Tag, message: NdefMessage): Result {
        val bytes = runCatching { message.toByteArray().size }.getOrDefault(0)
        val formatted = format(tag, message)
        if (formatted != null) return formatted

        val ndef = runCatching { Ndef.get(tag) }.getOrNull()
            ?: return Result.Failed("Метка не поддерживает NDEF (только чтение)")
        return try {
            ndef.connect()
            if (!ndef.isWritable) {
                ndef.close()
                return Result.Failed("Метка защищена от записи")
            }
            if (ndef.maxSize < bytes) {
                ndef.close()
                return Result.Failed("Метка мала: нужно $bytes байт, доступно ${ndef.maxSize}")
            }
            ndef.writeNdefMessage(message)
            ndef.close()
            Result.Ok(bytes)
        } catch (error: Throwable) {
            runCatching { ndef.close() }
            Result.Failed(error.message ?: "Запись прервана — держите телефон на метке")
        }
    }

    /** Пустая наклейка: сначала форматируем её под NDEF, потом пишем. */
    private fun format(tag: Tag, message: NdefMessage): Result? {
        val formatable = runCatching { NdefFormatable.get(tag) }.getOrNull() ?: return null
        return try {
            formatable.connect()
            formatable.format(message)
            formatable.close()
            Result.Ok(runCatching { message.toByteArray().size }.getOrDefault(0))
        } catch (error: Throwable) {
            runCatching { formatable.close() }
            Result.Failed(error.message ?: "Пустую метку не удалось разметить")
        }
    }

    /** Описание метки для экрана — что за железо и сколько места. */
    fun describe(tag: Tag): String {
        val ndef = runCatching { Ndef.get(tag) }.getOrNull()
        val tech = NfcReader.describe(tag)
        if (ndef == null) {
            val formatable = runCatching { NdefFormatable.get(tag) }.getOrNull()
            return if (formatable != null) "$tech • пустая, можно разметить" else "$tech • только чтение"
        }
        val free = (ndef.maxSize - (ndef.cachedNdefMessage?.toByteArray()?.size ?: 0)).coerceAtLeast(0)
        val state = if (ndef.isWritable) "доступна запись" else "защищена"
        return "$tech • $state, свободно ≈ $free байт"
    }
}
