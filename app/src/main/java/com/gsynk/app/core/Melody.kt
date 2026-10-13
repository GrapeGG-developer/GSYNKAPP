package com.gsynk.app.core

import android.content.Context
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Музыка из частот.
 *
 * Ноты здесь — это просто частоты: любой набор частот можно сохранить, проиграть
 * подряд (как мелодию), транспонировать, передать по NFC или послать другу.
 * Кодировка строчкой выбрана специально: «C4:400,D#4:400» весит десятки байт и
 * влезает даже в обычную NFC-метку на 492 байта.
 */
object Melody {

    data class Note(val hz: Double, val ms: Long) {
        val name: String get() = nameOf(hz)
    }

    private val NAMES = listOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )

    /** Полутоны относительно C0 = 16.35 Гц. */
    fun indexOf(name: String): Int? {
        val clean = name.trim()
        if (clean.length < 2) return null
        val letter = clean[0].uppercaseChar()
        val base = when (letter) {
            'C' -> 0; 'D' -> 2; 'E' -> 4; 'F' -> 5; 'G' -> 7; 'A' -> 9; 'B' -> 11
            else -> return null
        }
        var cursor = 1
        var shift = base
        if (cursor < clean.length && (clean[cursor] == '#' || clean[cursor] == 'b')) {
            shift += if (clean[cursor] == '#') 1 else -1
            cursor++
        }
        val octave = clean.substring(cursor).trim().toIntOrNull() ?: return null
        return octave * 12 + shift
    }

    fun hzOf(name: String): Double {
        val index = indexOf(name) ?: return 440.0
        return hzOfIndex(index)
    }

    fun hzOfIndex(index: Int): Double = 16.3516 * 2.0.pow(index / 12.0)

    fun nameOf(hz: Double): String {
        val index = (12.0 * ln(ToneSynth.clampHz(hz) / 16.3516) / ln(2.0)).roundToInt()
        return nameOfIndex(index)
    }

    fun nameOfIndex(index: Int): String {
        val octave = index / 12
        val step = ((index % 12) + 12) % 12
        return NAMES[step] + octave
    }

    /** Сколько центов нота не попадает в свой полутон — для индикатора точности. */
    fun centsOff(hz: Double): Double {
        val exact = hzOfIndex(hzOfExactIndex(hz))
        if (exact <= 0.0) return 0.0
        return 1200.0 * ln(hz / exact) / ln(2.0)
    }

    private fun hzOfExactIndex(hz: Double): Int =
        (12.0 * ln(ToneSynth.clampHz(hz) / 16.3516) / ln(2.0)).roundToInt()

    /** «C4:400,E4:400» — просто и компактно. */
    fun encode(notes: List<Note>): String = notes.joinToString(",") { note ->
        nameOf(note.hz) + ":" + note.ms
    }

    fun decode(text: String): List<Note> = text.split(',')
        .mapNotNull { part ->
            val bits = part.trim().split(':')
            if (bits.size != 2) return@mapNotNull null
            val index = indexOf(bits[0]) ?: return@mapNotNull null
            val ms = bits[1].trim().toLongOrNull() ?: return@mapNotNull null
            Note(hzOfIndex(index), ms.coerceIn(20L, 5_000L))
        }

    fun totalMs(notes: List<Note>): Long = notes.fold(0L) { acc, note -> acc + note.ms }

    fun transpose(notes: List<Note>, semitones: Int): List<Note> = notes.map { note ->
        val index = hzOfExactIndex(note.hz) + semitones
        Note(hzOfIndex(index.coerceIn(-12, 120)), note.ms)
    }

    /** Медленнее/быстрее: меняет длительности, но не высоту. */
    fun tempo(notes: List<Note>, percent: Int): List<Note> {
        val k = percent.coerceIn(25, 400) / 100.0
        return notes.map { Note(it.hz, (it.ms * k).toLong().coerceIn(20L, 5_000L)) }
    }

    /** Собирает мелодию из произвольных частот — «музыка из этих частот». */
    fun fromFrequencies(frequencies: List<Double>, stepMs: Long = 320L): List<Note> =
        frequencies.map { Note(ToneSynth.clampHz(it), stepMs.coerceIn(20L, 5_000L)) }

    /** Гамма из [count] нот от [root] — чтобы играть «в тональности», а не наугад. */
    fun scale(root: String, kind: String = "мажор", count: Int = 12): List<Double> {
        val steps = when (kind) {
            "минор" -> listOf(0, 2, 3, 5, 7, 8, 10)
            "пентатоника" -> listOf(0, 3, 5, 7, 10)
            "целотон" -> listOf(0, 2, 4, 6, 8, 10)
            else -> listOf(0, 2, 4, 5, 7, 9, 11)
        }
        val rootIndex = indexOf(root) ?: 48
        return (0 until count).map { step ->
            val octave = step / steps.size
            val note = steps[step % steps.size] + octave * 12
            hzOfIndex(rootIndex + note)
        }
    }

    /** Готовые мотивы: нажал — телефон заиграл частоты. */
    val PRESETS: Map<String, String> = linkedMapOf(
        "Пятая" to "G4:220,G4:220,G4:220,D#4:660,C4:320,G4:220,G4:220,G4:220,D#4:440,C4:440",
        "Кузнечик" to "A3:220,B3:220,C4:220,B3:220,A3:220,B3:220,C4:440,A3:220,B3:220,C4:220",
        "Радио" to "A4:120,A4:120,A4:120,A4:120,F4:120,C5:120,A4:240,F4:120,C5:240,A4:600",
        "Сигнал" to "C5:120,E5:120,G5:240,C5:120,E5:120,G5:240,C5:600",
        // подъём по октавам: слышно, как динамик проходит низы и верхи
        "Катушка" to "C1:400,C2:400,C3:400,C4:400,C5:400,C6:400,C7:400,C8:400,C9:400"
    )

    fun preset(name: String): List<Note> = decode(PRESETS[name].orEmpty())

    fun clampNoteMs(ms: Long): Long = ms.coerceIn(20L, 5_000L)

    fun isLongEnough(notes: List<Note>): Boolean = totalMs(notes) >= 80L

    fun durationLabel(notes: List<Note>): String {
        val total = totalMs(notes)
        val seconds = total / 1000.0
        return if (seconds < 10) "%.1f с".format(seconds) else "%.0f с".format(seconds)
    }

    /** Итог проверки мотива для интерфейса. */
    fun summary(notes: List<Note>): String =
        notes.size.toString() + " нот • " + durationLabel(notes)

    fun semitoneDistance(a: Double, b: Double): Int = abs(hzOfExactIndex(a) - hzOfExactIndex(b))
}

/** Мотивы пользователя: SharedPreferences, до 40 штук, ключ — название. */
object MelodyStore {

    private const val PREFS = "gsynk_melodies"
    private const val LIMIT = 40

    fun load(context: Context): Map<String, String> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.all.entries
            .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
            .sortedBy { it.first.lowercase() }
            .toMap()
    }.getOrDefault(emptyMap())

    fun save(context: Context, name: String, encoded: String): Boolean = runCatching {
        val clean = name.trim().take(28).ifBlank { "Мотив" }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(clean) && prefs.all.size >= LIMIT) return@runCatching false
        prefs.edit().putString(clean, encoded).apply()
        true
    }.getOrDefault(false)

    fun remove(context: Context, name: String): Boolean = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(name).apply()
        true
    }.getOrDefault(false)

    fun rename(context: Context, from: String, to: String): Boolean = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(from, null) ?: return@runCatching false
        val clean = to.trim().take(28).ifBlank { return@runCatching false }
        prefs.edit().remove(from).putString(clean, value).apply()
        true
    }.getOrDefault(false)
}
