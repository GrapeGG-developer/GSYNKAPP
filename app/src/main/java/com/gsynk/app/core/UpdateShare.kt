package com.gsynk.app.core

/**
 * «Передать обновление по NFC».
 *
 * Сам APK — 35 МБ, в метку он не влезет, и по NFC его не передать. Поэтому касание
 * передаёт **описание релиза**: версию, прямую ссылку на APK и контрольную сумму.
 * Второй телефон по этому касанию проверяет обновление и может скачать его сразу —
 * как если бы вы прислали ссылку, только без интернета и разговоров.
 *
 * Плюс есть «сигнал»: короткое «проверь обновление», чтобы друг одним касанием
 * отправил свой телефон проверять новую версию.
 */
object UpdateShare {

    /** Описание релиза, которое едет по NFC или Bluetooth. */
    data class Share(
        val version: String,
        val url: String,
        val sha256: String = "",
        val from: String = ""
    ) {
        /** Помещается в одну NDEF-запись: версия, ссылка и первые байты суммы. */
        val short: Boolean get() = url.length <= 240

        val shortSha: String get() = sha256.take(12)
    }

    const val PING = "1"

    // ——— сборка тела записи (всё строчками: метка обязана быть маленькой) ———

    fun body(share: Share): String = listOf(
        share.version.trim(),
        share.url.trim(),
        share.sha256.trim(),
        share.from.trim()
    ).joinToString(";")

    fun parse(body: String): Share? {
        val parts = body.split(";")
        val version = parts.getOrNull(0)?.trim().orEmpty()
        val url = parts.getOrNull(1)?.trim().orEmpty()
        if (version.isBlank() || !url.startsWith("http")) return null
        return Share(
            version = version,
            url = url,
            sha256 = parts.getOrNull(2)?.trim().orEmpty(),
            from = parts.getOrNull(3)?.trim().orEmpty()
        )
    }

    fun ping(from: String): String = PING + ";" + from.trim()

    fun isPing(body: String): Boolean = body.trim().startsWith(PING)

    fun pingFrom(body: String): String = body.split(";").getOrNull(1)?.trim().orEmpty()

    /** Ожидающее предложение обновиться — приходит касанием и живёт до показа. */
    var pending: Share? = null

    /** Последнее принятое предложение: показывается в панели обновлений, пока её не закроют. */
    var last: Share? = null

    /** Ждёт ли ответа просьба «проверь обновление». */
    var pendingPing: String? = null

    fun put(share: Share) {
        pending = share
        last = share
        pendingPing = null
    }

    fun putPing(from: String) {
        pendingPing = from
        pending = null
    }

    fun take(): Share? = pending.also { pending = null }

    fun clearLast() {
        last = null
    }

    fun takePing(): String? = pendingPing.also { pendingPing = null }

    fun describe(share: Share): String = buildString {
        append("версия ")
        append(share.version)
        if (share.from.isNotBlank()) {
            append(" • от ")
            append(share.from)
        }
        if (share.sha256.isNotBlank()) {
            append(" • сумма ")
            append(share.shortSha)
        }
    }

    /** Версия в ссылке: /download/v3.0.0/GSYNK-v3.0.0-debug.apk → v3.0.0 */
    fun versionFromUrl(url: String): String =
        url.substringAfter("/download/", "").substringBefore('/').trim()

    /** Ссылка на APK для релиза — на случай, когда известен только тег. */
    fun apkUrl(repo: String, tag: String): String =
        "https://github.com/" + repo.trim('/') + "/releases/download/" + tag +
            "/GSYNK-" + tag + "-debug.apk"

    /** Совпадает ли присланная версия с той, что мы уже проверяем. */
    fun isSame(a: Share, version: String?): Boolean =
        version != null && a.version.trim().trimStart('v') == version.trim().trimStart('v')
}
