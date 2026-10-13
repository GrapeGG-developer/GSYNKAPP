package com.gsynk.app.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Непрерывный тон и музыка из частот: то, чем можно «играть» на катушке динамика.
 *
 * [ToneSynth] держит один живой [AudioTrack] и пишет в него синусоиду из отдельного
 * потока. Частота меняется на ходу — поэтому кнопка «держу и веду по шкале» играет
 * без щелчков и без перезапуска звука.
 */
object ToneSynth {

    const val SAMPLE_RATE = 44_100
    private const val BUFFER_FRAMES = 1_024

    private var track: AudioTrack? = null
    private var worker: Thread? = null

    @Volatile private var frequency = 440.0
    @Volatile private var volume = 0.55f
    @Volatile private var running = false

    val isPlaying: Boolean get() = running
    val currentHz: Double get() = frequency

    /** Запускает непрерывный тон. Повторный вызов просто меняет частоту. */
    fun start(hz: Double, volumePercent: Int = 55) {
        frequency = clampHz(hz)
        volume = (volumePercent.coerceIn(1, 100) / 100f) * 0.9f
        if (running) return
        val built = runCatching { buildTrack() }.getOrNull() ?: return
        track = built
        running = true
        worker = Thread({ loop(built) }, "gsynk-tone").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    fun setFrequency(hz: Double) {
        frequency = clampHz(hz)
    }

    /** Плавное скольжение частоты: удобно «вести» по шкале пальцем. */
    fun glideTo(hz: Double, steps: Int = 12, delayMs: Long = 12L) {
        val from = frequency
        val to = clampHz(hz)
        Thread({
            repeat(steps) { step ->
                val k = (step + 1).toDouble() / steps
                frequency = from + (to - from) * k
                runCatching { Thread.sleep(delayMs) }
            }
        }, "gsynk-glide").apply { isDaemon = true }.start()
    }

    fun setVolume(percent: Int) {
        volume = (percent.coerceIn(1, 100) / 100f) * 0.9f
    }

    fun stop() {
        running = false
        val current = track
        track = null
        runCatching { worker?.join(200) }
        worker = null
        runCatching { current?.pause() }
        runCatching { current?.flush() }
        runCatching { current?.stop() }
        runCatching { current?.release() }
    }

    // ————— чистые куски: их проверяют тесты —————

    fun clampHz(hz: Double): Double = when {
        hz.isNaN() -> 440.0
        hz < MIN_HZ -> MIN_HZ
        hz > MAX_HZ -> MAX_HZ
        else -> hz
    }

    /** Играет готовую последовательность нот (музыку из частот). */
    fun playSequence(notes: List<Melody.Note>, onProgress: (Int) -> Unit = {}) {
        Thread({
            notes.forEachIndexed { index, note ->
                onProgress(index)
                val hz = clampHz(note.hz)
                start(hz, 60)
                runCatching { Thread.sleep(note.ms.coerceAtLeast(20L)) }
            }
            stop()
            onProgress(-1)
        }, "gsynk-melody").apply { isDaemon = true }.start()
    }

    private fun buildTrack(): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(BUFFER_FRAMES * 2)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBytes)
            .build()
            .also { runCatching { it.play() } }
    }

    private fun loop(track: AudioTrack) {
        val generator = SineGenerator()
        val buffer = ShortArray(BUFFER_FRAMES)
        while (running && track === this.track) {
            generator.fill(buffer, frequency, volume, SAMPLE_RATE)
            val written = runCatching { track.write(buffer, 0, buffer.size) }.getOrDefault(-1)
            if (written < 0) break
        }
    }

    const val MIN_HZ = 20.0
    const val MAX_HZ = 20_000.0
}

/** Синус с переносом фазы между буферами — без щелчков на стыках. */
class SineGenerator {
    private var phase = 0.0

    fun fill(buffer: ShortArray, hz: Double, volume: Float, sampleRate: Int = ToneSynth.SAMPLE_RATE) {
        val step = 2.0 * PI * ToneSynth.clampHz(hz) / sampleRate
        val amp = (volume.coerceIn(0.01f, 1f) * Short.MAX_VALUE * 0.9).toInt()
        for (index in buffer.indices) {
            buffer[index] = (sin(phase) * amp).toInt().coerceIn(-32_767, 32_767).toShort()
            phase += step
            if (phase > 2.0 * PI) phase -= 2.0 * PI
        }
    }

    /** Сколько миллисекунд нужно на [frames] кадров при [sampleRate]. */
    fun framesToMs(frames: Int, sampleRate: Int = ToneSynth.SAMPLE_RATE): Long =
        (frames * 1000L) / sampleRate

    /** Частота ноты по полутонам от A4 (440 Гц). */
    fun frequencyOf(semitonesFromA4: Int): Double = 440.0 * 2.0.pow(semitonesFromA4 / 12.0)

    /** Ближайший полутон для произвольной частоты — для распознавания записанных нот. */
    fun nearestSemitone(hz: Double): Int = (12.0 * kotlin.math.ln(ToneSynth.clampHz(hz) / 440.0) / kotlin.math.ln(2.0)).toInt()

    fun nearestFrequency(hz: Double): Double = frequencyOf(nearestSemitone(hz))

    fun centsOff(hz: Double): Double {
        val exact = frequencyOf(nearestSemitone(hz))
        if (exact <= 0.0) return 0.0
        return 1200.0 * kotlin.math.ln(hz / exact) / kotlin.math.ln(2.0)
    }

    /** Уровень в «процентах шкалы» — для индикатора удержания. */
    fun levelPercent(hz: Double): Int =
        ((kotlin.math.ln(ToneSynth.clampHz(hz) / ToneSynth.MIN_HZ) /
            kotlin.math.ln(ToneSynth.MAX_HZ / ToneSynth.MIN_HZ)) * 100).toInt().coerceIn(0, 100)

    /** Частота по проценту шкалы — обратная операция для слайдера. */
    fun frequencyAtPercent(percent: Int): Double {
        val k = percent.coerceIn(0, 100) / 100.0
        val ratio = ToneSynth.MAX_HZ / ToneSynth.MIN_HZ
        return ToneSynth.MIN_HZ * ratio.pow(k)
    }

    /** Сколько нот успеет проиграть за [budgetMs]. */
    fun fitsIn(notes: List<Melody.Note>, budgetMs: Long): Int {
        var left = budgetMs
        var count = 0
        for (note in notes) {
            left -= note.ms
            if (left < 0) break
            count++
        }
        return min(count, notes.size)
    }
}
