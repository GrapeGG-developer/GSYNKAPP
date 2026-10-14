package com.gsynk.app.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * События NFC «здесь и сейчас».
 *
 * Раньше анимация и надпись «подключено» могли появиться просто при открытии экрана
 * или при включении эмуляции метки — то есть когда рядом никакого NFC нет. Теперь
 * единственный источник событий — настоящее железо:
 *
 *  * [readerCame] — второй телефон реально читает нашу метку (из [NdefHceService]);
 *  * [tagFound] — мы сами прочитали чужую метку (из читающего режима);
 *  * [written] — записали данные на метку.
 */
object NfcPulse {

    enum class Kind(val title: String) {
        WAITING("жду касания"),
        READER_CAME("второй телефон читает"),
        TAG_FOUND("метка прочитана"),
        WRITTEN("записано на метку")
    }

    data class Event(val kind: Kind, val detail: String, val at: Long = System.currentTimeMillis())

    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event.asStateFlow()

    private val _count = MutableStateFlow(0)

    /** Сколько настоящих касаний случилось с момента открытия экрана. */
    val count: StateFlow<Int> = _count.asStateFlow()

    private var context: Context? = null

    /** Экран Grape Link сообщает о себе, чтобы события шли в журнал. */
    fun attach(context: Context) {
        this.context = context.applicationContext
    }

    fun detach() {
        context = null
    }

    fun readerCame(source: Context) {
        publish(Event(Kind.READER_CAME, "второй телефон прочитал наши данные"), source)
    }

    fun tagFound(detail: String) {
        publish(Event(Kind.TAG_FOUND, detail), context)
    }

    fun written(detail: String) {
        publish(Event(Kind.WRITTEN, detail), context)
    }

    fun clear() {
        _event.value = null
    }

    private fun publish(event: Event, source: Context?) {
        _event.value = event
        _count.value = _count.value + 1
        AppLog.event("Grape Link", event.kind.title + ": " + event.detail)
        (source ?: context)?.let { Haptics.play(it, longArrayOf(0, 40, 60, 40), Haptics.Use.NOTIFICATION) }
    }
}
