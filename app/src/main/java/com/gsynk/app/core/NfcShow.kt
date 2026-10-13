package com.gsynk.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * «Показать по касанию»: то, что второй телефон делает сразу после прикосновения.
 *
 * Сюда попадают и весёлые штуки (салют, сердечки, вспышка, надпись во весь экран),
 * и команды (фонарик, вибрация, сирена). Запрос кладётся в [current], а показывает
 * его один общий слой поверх всего приложения — поэтому анимация появляется
 * независимо от того, какой экран открыт.
 */
object NfcShow {

    enum class Kind(val label: String, val hint: String, val isCommand: Boolean) {
        FIREWORKS("Салют", "залпы искр во весь экран", false),
        HEARTS("Сердечки", "летят вверх с подписями", false),
        FLASH("Вспышка", "яркая вспышка цвета", false),
        TEXT("Надпись", "текст во весь экран", false),
        TORCH("Фонарик", "включает вспышку камеры", true),
        VIBRATE("Вибрация", "трясёт телефон", true),
        SIREN("Сирена", "громкий сигнал на 3 секунды", true)
    }

    data class Request(
        val kind: Kind,
        val text: String,
        val from: String,
        val id: Long = System.nanoTime()
    )

    /** Сколько держать картинку на экране. */
    fun durationOf(kind: Kind): Long = when (kind) {
        Kind.FIREWORKS, Kind.HEARTS -> 5_200L
        Kind.FLASH -> 2_600L
        Kind.TEXT -> 4_600L
        Kind.TORCH -> 2_000L
        Kind.VIBRATE -> 1_600L
        Kind.SIREN -> 3_200L
    }

    private val _current = MutableStateFlow<Request?>(null)
    val current: StateFlow<Request?> = _current.asStateFlow()

    fun show(kind: Kind, text: String = "", from: String = "") {
        _current.value = Request(kind = kind, text = text, from = from)
    }

    fun clear() {
        _current.value = null
    }

    // ————— провод: строка уходит по NFC —————

    fun encode(kind: Kind, text: String): String = kind.name.lowercase() + "|" + text.replace("|", "/")

    fun decode(body: String): Pair<Kind, String>? {
        val parts = body.split("|", limit = 2)
        val kind = Kind.entries.firstOrNull { it.name.equals(parts.getOrNull(0), ignoreCase = true) }
            ?: return null
        return kind to parts.getOrNull(1).orEmpty()
    }

    /** Быстрая проверка, что это вызов показа, а не мусор. */
    fun isShow(kind: String): Boolean = kind == NfcLink.KIND_SHOW
}
