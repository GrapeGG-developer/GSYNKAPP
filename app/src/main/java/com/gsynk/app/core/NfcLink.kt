package com.gsynk.app.core

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.nio.charset.StandardCharsets

/**
 * Обмен по NFC («Grape Link»).
 *
 * Что можно передать: текст, ссылку, визитку, пароль, координаты, профиль GSYNK
 * (правила расписания) и вызов в игру. NFC — канал на пару килобайт, поэтому
 * передаём короткие данные, а телефон-метку обслуживает [NdefHceService].
 */
object NfcLink {

    const val MIME_TEXT = "text/plain"
    const val MIME_URL = "text/uri-list"
    const val MIME_CONTACT = "text/vcard"
    const val MIME_GAME = "application/vnd.gsynk.game"
    const val MIME_PROFILE = "application/vnd.gsynk.profile"
    const val MIME_FILE = "application/vnd.gsynk.file"

    const val KIND_TEXT = "text"
    const val KIND_URL = "url"
    const val KIND_CONTACT = "contact"
    const val KIND_PASSWORD = "password"
    const val KIND_GEO = "geo"
    const val KIND_PROFILE = "profile"
    const val KIND_GAME = "game"

    /** Файл: касание — визитка, сам файл едет по Wi-Fi (или целиком, если он крошечный). */
    const val KIND_FILE = "file"

    /** Показ по касанию: салют, сердечки, вспышка, надпись или команда второму телефону. */
    const val KIND_SHOW = "show"

    /** Пользовательские типы: кодируем как текст с префиксом вида gsynk:password|… */
    private const val PREFIX = "gsynk"

    data class Payload(
        val kind: String,
        val title: String,
        val body: String
    ) {
        val isProfile: Boolean get() = kind == KIND_PROFILE
    }

    /**
     * Приглашение в подземелье.
     *
     * Если заполнены [host], [port] и [code] — это сетевой вызов: NFC поработал
     * знакомством, а игра пойдёт по Wi-Fi, каждый на своём телефоне.
     */
    data class GameInvite(
        val game: String,
        val from: String,
        val seed: Long,
        val host: String = "",
        val port: Int = 0,
        val code: String = ""
    ) {
        val isWifi: Boolean get() = host.isNotBlank() && port > 0
    }

    /**
     * Приглашение на файл: адрес, порт, одноразовый код и имя файла.
     * Если [inline] не пуст — файл маленький и уже лежит в самой метке.
     */
    data class FileInvite(
        val host: String,
        val port: Int,
        val token: String,
        val name: String,
        val size: Long,
        val inline: String = ""
    ) {
        val isInline: Boolean get() = inline.isNotBlank()
    }

    private fun clean(value: String): String = value.replace(';', '_').replace('|', '_')

    fun fileInviteBody(invite: FileInvite): String = listOf(
        clean(invite.host),
        invite.port.toString(),
        clean(invite.token),
        clean(invite.name),
        invite.size.toString(),
        invite.inline
    ).joinToString(";")

    fun parseFileInvite(body: String): FileInvite? = runCatching {
        val parts = body.split(";")
        val host = parts.getOrNull(0).orEmpty()
        val inline = parts.getOrNull(5).orEmpty()
        // без адреса приглашение бессмысленно, но крошечный файл может жить прямо в метке
        if (host.isBlank() && inline.isBlank()) return null
        FileInvite(
            host = host,
            port = parts.getOrNull(1)?.toIntOrNull() ?: FileLink.PORT,
            token = parts.getOrNull(2).orEmpty(),
            name = parts.getOrNull(3).orEmpty().ifBlank { "файл-gsynk" },
            size = parts.getOrNull(4)?.toLongOrNull() ?: 0L,
            inline = parts.getOrNull(5).orEmpty()
        )
    }.getOrNull()

    /** Доступные типы для экрана передачи. */
    val TRANSFER_KINDS = listOf(
        TransferOption(KIND_TEXT, "Текст", "Любое сообщение до ~600 символов"),
        TransferOption(KIND_URL, "Ссылка", "Откроется на втором телефоне"),
        TransferOption(KIND_CONTACT, "Визитка", "Имя и номер телефона"),
        TransferOption(KIND_PASSWORD, "Пароль", "Случайный пароль или ключ"),
        TransferOption(KIND_GEO, "Координаты", "Точка на карте"),
        TransferOption(KIND_PROFILE, "Профиль GSYNK", "Правила расписания целиком"),
        TransferOption(KIND_GAME, "Вызов в игру", "Приглашение в дуэль")
    )

    data class TransferOption(val kind: String, val title: String, val hint: String)

    fun option(kind: String): TransferOption =
        TRANSFER_KINDS.firstOrNull { it.kind == kind } ?: TRANSFER_KINDS.first()

    fun isSupported(): Boolean = true

    fun openNfcSettingsIntent(): android.content.Intent =
        android.content.Intent(android.provider.Settings.ACTION_NFC_SETTINGS)

    // ————— сборка —————

    fun buildMessage(payload: Payload, appPackage: String): NdefMessage {
        val record = when (payload.kind) {
            KIND_URL -> NdefRecord.createUri(payload.body)
            KIND_CONTACT -> mime(MIME_CONTACT, payload.body)
            KIND_GAME -> mime(MIME_GAME, payload.body)
            KIND_PROFILE -> mime(MIME_PROFILE, payload.body)
            else -> NdefRecord.createTextRecord(
                "ru",
                "$PREFIX:${payload.kind}|${payload.title}|${payload.body}".take(900)
            )
        }
        return NdefMessage(arrayOf(record, NdefRecord.createApplicationRecord(appPackage)))
    }

    fun buildGameInvite(invite: GameInvite, appPackage: String): NdefMessage =
        buildMessage(Payload(KIND_GAME, "GSYNK-игра", gameInviteBody(invite)), appPackage)

    /** Тело вызова: игра;отправитель;ключ;адрес;порт;код комнаты. */
    fun gameInviteBody(invite: GameInvite): String =
        listOf(
            invite.game,
            invite.from,
            invite.seed.toString(),
            invite.host,
            invite.port.toString(),
            invite.code
        ).joinToString(";")

    private fun mime(type: String, body: String): NdefRecord =
        NdefRecord(
            NdefRecord.TNF_MIME_MEDIA,
            type.toByteArray(StandardCharsets.US_ASCII),
            ByteArray(0),
            body.toByteArray(StandardCharsets.UTF_8)
        )

    // ————— разбор —————

    fun parse(message: NdefMessage?): Payload? {
        if (message == null) return null
        for (record in message.records) {
            parseRecord(record)?.let { return it }
        }
        return null
    }

    private fun parseRecord(record: NdefRecord): Payload? = runCatching {
        val type = String(record.type, StandardCharsets.US_ASCII)
        when {
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI) -> {
                Payload(KIND_URL, "Ссылка", record.toUri()?.toString().orEmpty())
            }

            record.tnf == NdefRecord.TNF_MIME_MEDIA && type == MIME_CONTACT ->
                Payload(KIND_CONTACT, "Визитка", String(record.payload, StandardCharsets.UTF_8))

            record.tnf == NdefRecord.TNF_MIME_MEDIA && type == MIME_GAME ->
                Payload(KIND_GAME, "Вызов в игру", String(record.payload, StandardCharsets.UTF_8))

            record.tnf == NdefRecord.TNF_MIME_MEDIA && type == MIME_PROFILE ->
                Payload(KIND_PROFILE, "Профиль GSYNK", String(record.payload, StandardCharsets.UTF_8))

            record.tnf == NdefRecord.TNF_MIME_MEDIA && type == MIME_FILE ->
                Payload(KIND_FILE, "Файл", String(record.payload, StandardCharsets.UTF_8))

            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                val statusByte = record.payload.firstOrNull()?.toInt() ?: 0
                val langLen = statusByte and 0x3F
                val raw = if (record.payload.size > langLen + 1) {
                    String(record.payload, langLen + 1, record.payload.size - langLen - 1, StandardCharsets.UTF_8)
                } else {
                    ""
                }
                parseTextPayload(raw)
            }

            else -> null
        }
    }.getOrNull()

    /** Текст бывает двух форм: «gsynk:тип|заголовок|тело» и просто текст. */
    private fun parseTextPayload(raw: String): Payload {
        if (!raw.startsWith("$PREFIX:")) return Payload(KIND_TEXT, "Сообщение", raw)
        val rest = raw.removePrefix("$PREFIX:")
        val parts = rest.split("|", limit = 3)
        val kind = parts.getOrNull(0).orEmpty().ifBlank { KIND_TEXT }
        val title = parts.getOrNull(1).orEmpty()
        val body = parts.getOrNull(2).orEmpty()
        return Payload(kind, title.ifBlank { option(kind).title }, body)
    }

    fun parseGameInvite(body: String): GameInvite? = runCatching {
        val parts = body.split(";")
        GameInvite(
            game = parts.getOrNull(0).orEmpty(),
            from = parts.getOrNull(1).orEmpty(),
            seed = parts.getOrNull(2)?.toLongOrNull() ?: System.currentTimeMillis(),
            host = parts.getOrNull(3).orEmpty(),
            port = parts.getOrNull(4)?.toIntOrNull() ?: 0,
            code = parts.getOrNull(5).orEmpty()
        )
    }.getOrNull()

    /** Краткое описание того, что пришло — для карточки «Получено». */
    fun describe(payload: Payload): String = when (payload.kind) {
        KIND_URL -> "Ссылка готова к открытию"
        KIND_CONTACT -> "Контакт: откройте, чтобы добавить в телефонную книгу"
        KIND_PASSWORD -> "Пароль можно скопировать в буфер"
        KIND_GEO -> "Координаты: откроются на карте"
        KIND_PROFILE -> "Профиль GSYNK: импортируйте правила расписания"
        KIND_GAME -> if (parseGameInvite(payload.body)?.isWifi == true)
            "Вызов в GTBOI на два телефона — игра пойдёт по Wi-Fi"
        else "Вызов в игру"

        KIND_FILE -> {
            val invite = parseFileInvite(payload.body)
            when {
                invite?.isInline == true -> "Файл «" + invite.name + "» лежит прямо в метке"
                invite != null -> "Файл «" + invite.name + "» приедет по Wi-Fi, размер " + invite.size + " Б"
                else -> "Приглашение на файл"
            }
        }

        KIND_SHOW -> NfcShow.decode(payload.body)
            ?.let { (kind, text) ->
                if (kind.isCommand) "Команда: " + kind.label.lowercase() + " на втором телефоне"
                else "Показ на втором телефоне: " + kind.label.lowercase() +
                    if (text.isBlank()) "" else " — «$text»"
            }
            ?: "Показ по касанию"
        else -> "Текстовое сообщение"
    }
}
