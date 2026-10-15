package com.gsynk.app

import com.gsynk.app.core.DeviceProfile
import com.gsynk.app.core.Echo
import com.gsynk.app.core.Probe
import com.gsynk.app.core.Pulse
import com.gsynk.app.ui.Catalog
import com.gsynk.app.ui.Gsui4PageIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * V4p2: эхо-линейка, проверка глаз и пульс камерой.
 *
 * Проверяем физику и математику, а не «нарисовалось ли»: скорость звука от температуры,
 * поиск эха в записи, автокорреляцию пульса, размеры знаков для проверки остроты и
 * геометрию цветных пластин. Всё это считается без телефона, поэтому и проверяется здесь.
 */
class V4P2Test {

    // ————— эхо-линейка: скорость звука —————

    @Test
    fun soundSpeedFollowsTemperature() {
        assertEquals(331.3f, Echo.soundSpeed(0), 0.05f)
        assertEquals(343.4f, Echo.soundSpeed(20), 0.1f)
        // Тёплый воздух проводит звук быстрее — это не украшение, а закон.
        assertTrue(Echo.soundSpeed(30) > Echo.soundSpeed(10))
        // За пределами разумного температура не разгоняет звук до бесконечности.
        assertEquals(Echo.soundSpeed(60), Echo.soundSpeed(200))
    }

    @Test
    fun centimetresPerMillisecondMatchAir() {
        // 343,4 м/с туда-обратно — это 17,17 см на миллисекунду задержки.
        assertEquals(17.17f, Echo.cmPerMilli(20), 0.05f)
        assertEquals(0f, Echo.cmPerMilli(20) * 0f, 0.001f)
    }

    @Test
    fun distanceIsHalfOfTheRoundTrip() {
        // 12 мс при 20 °C — это 206 см, а не метр: путь делится на два.
        assertEquals(206f, Echo.distanceCm(12f, 20), 1f)
        assertEquals(0f, Echo.distanceCm(0f, 20), 0.001f)
    }

    @Test
    fun warmAirGivesLongerDistanceForTheSameDelay() {
        assertTrue(Echo.distanceCm(10f, 35) > Echo.distanceCm(10f, 0))
    }

    // ————— эхо-линейка: сигнал —————

    @Test
    fun chirpHasTheRequestedLengthAndSoftEdges() {
        val click = Echo.chirp(44_100)
        assertEquals(44_100 * Echo.CLICK_MS / 1000, click.size)
        // Плавные края: первый и последний отсчёты почти нулевые, иначе это не импульс, а стук.
        assertTrue(kotlin.math.abs(click.first().toInt()) < 400)
        assertTrue(kotlin.math.abs(click.last().toInt()) < 400)
        // В середине сигнал полный.
        assertTrue(kotlin.math.abs(click[click.size / 2].toInt()) > 15_000)
    }

    @Test
    fun envelopeIsNeverNegative() {
        val data = ShortArray(400) { index -> (sin(index / 3.0) * 26_000).toInt().toShort() }
        val env = Echo.envelope(data, window = 16)
        assertEquals(data.size, env.size)
        assertTrue(env.all { it >= 0f })
        assertTrue(env.max() > 0f)
    }

    @Test
    fun echoIsMeasuredFromTheClickItself() {
        // Синтетика: щелчок пришёл на 3 мс, эхо вернулось на 9 мс → задержка 6 мс → ~103 см.
        val rate = 44_100
        val env = FloatArray(rate * 200 / 1000)
        for (index in env.indices) env[index] = 5f
        val clickAt = 3 * rate / 1000
        val echoAt = 9 * rate / 1000
        for (index in 0 until 40) {
            env[clickAt + index] = 4_000f
            env[echoAt + index] = 1_200f
        }
        val delay = Echo.echoDelayMs(env, rate)
        assertNotNull(delay)
        assertEquals(6, delay!!)
        assertEquals(103f, Echo.distanceCm(delay.toFloat(), 20), 2f)
    }

    @Test
    fun silenceGivesNoAnswer() {
        val quiet = FloatArray(4_000) { 4f }
        assertNull(Echo.echoDelayMs(quiet, 44_100))
        assertNull(Echo.echoDelayMs(FloatArray(0), 44_100))
        assertNull(Echo.echoDelayMs(FloatArray(100) { 1f }, 0))
    }

    @Test
    fun clickWithoutEchoIsNotAnEcho() {
        // Щелчок был, отражения нет: честный ответ — «не нашёл», а не выдуманное число.
        val rate = 44_100
        val env = FloatArray(rate * 200 / 1000) { 4f }
        val clickAt = 3 * rate / 1000
        for (index in 0 until 40) env[clickAt + index] = 3_000f
        assertNull(Echo.echoDelayMs(env, rate))
        assertNull(Echo.estimate(env, rate, 22))
    }

    @Test
    fun estimateCarriesDistanceAndLevel() {
        val rate = 44_100
        val env = FloatArray(rate * 200 / 1000) { 3f }
        val clickAt = 2 * rate / 1000
        val echoAt = 8 * rate / 1000
        for (index in 0 until 30) {
            env[clickAt + index] = 3_500f
            env[echoAt + index] = 900f
        }
        val estimate = Echo.estimate(env, rate, 20)
        assertNotNull(estimate)
        assertEquals(6, estimate!!.delayMs)
        assertTrue(estimate.cm > 90f && estimate.cm < 120f)
        assertTrue(estimate.level > 500f)
    }

    @Test
    fun notesExplainTheResult() {
        assertTrue(Echo.note(20f, 1).contains("сливается"))
        assertTrue(Echo.note(200f, 12).contains("похож"))
        assertTrue(Echo.note(1_500f, 80).contains("далеко"))
    }

    // ————— пульс —————

    /** Чистая волна нужной частоты: так проверяется автокорреляция. */
    private fun wave(hz: Float, fps: Float, seconds: Float, noise: Float = 0f): List<Float> {
        val count = (fps * seconds).toInt()
        val random = kotlin.random.Random(7)
        return List(count) { index ->
            val phase = 2.0 * PI * hz * index / fps
            val value = sin(phase).toFloat()
            value + 0.3f + (if (noise > 0f) (random.nextFloat() - 0.5f) * noise else 0f)
        }
    }

    @Test
    fun autocorrelationFindsTheRealRate() {
        // 1,2 Гц — это 72 удара в минуту.
        assertEquals(72, Pulse.beatsPerMinute(wave(1.2f, 30f, 15f), 30f))
        assertEquals(90, Pulse.beatsPerMinute(wave(1.5f, 30f, 15f), 30f))
        // 0,8 Гц — это 48 ударов, но кадры идут целыми: допускаем соседнее значение.
        val slow = Pulse.beatsPerMinute(wave(0.8f, 30f, 20f), 30f)
        assertNotNull(slow)
        assertTrue("медленный пульс посчитан как " + slow, slow!! in 46..50)
    }

    @Test
    fun autocorrelationSurvivesNoise() {
        val bpm = Pulse.beatsPerMinute(wave(1.2f, 30f, 20f, noise = 0.35f), 30f)
        assertNotNull(bpm)
        assertTrue("пульс уехал: " + bpm, bpm!! in 68..76)
    }

    @Test
    fun flatSignalIsNotAPulse() {
        assertNull(Pulse.beatsPerMinute(List(400) { 0.5f }, 30f))
        assertNull(Pulse.beatsPerMinute(emptyList(), 30f))
        assertNull(Pulse.beatsPerMinute(wave(1.2f, 30f, 1f), 30f))
        assertNull(Pulse.beatsPerMinute(wave(1.2f, 30f, 15f), 0f))
    }

    @Test
    fun impossibleRateIsNeverReported() {
        // 5 Гц — это 300 ударов: так не бывает. Приложение не должно показывать такие числа.
        val fast = Pulse.beatsPerMinute(wave(5f, 60f, 15f), 60f)
        assertTrue("за пределы разумного пульс выходить не должен", fast == null || fast in 40..190)
    }

    @Test
    fun detrendRemovesSlowDrift() {
        val drift = List(300) { index -> 1f + index * 0.01f }
        val cleaned = Pulse.detrend(drift)
        assertEquals(drift.size, cleaned.size)
        assertTrue("наклон не убрался", kotlin.math.abs(cleaned.average()) < 0.05)
    }

    @Test
    fun smoothKeepsLengthAndCalmsTheSignal() {
        val noisy = List(200) { index -> if (index % 2 == 0) 1f else 0f }
        val calm = Pulse.smooth(noisy, 5)
        assertEquals(noisy.size, calm.size)
        assertTrue(calm.max() < 1f)
        assertTrue(calm.min() > 0f)
    }

    @Test
    fun qualityReflectsTheSignal() {
        val clean = Pulse.Reading(wave(1.2f, 30f, 15f), stamps(30f, 15f))
        val report = Pulse.analyse(clean)
        assertNotNull(report.bpm)
        assertTrue("качество должно быть приличным: " + report.quality, report.quality >= 30)
        assertTrue(report.note.isNotBlank())
    }

    @Test
    fun shortRecordingIsHonestlyRefused() {
        val short = Pulse.Reading(List(20) { 0.4f }, List(20) { it * 33_000_000L })
        val report = Pulse.analyse(short)
        assertNull(report.bpm)
        assertEquals(0, report.quality)
        assertTrue(report.note.contains("мало"))
    }

    @Test
    fun frameRateIsComputedFromTimestamps() {
        val reading = Pulse.Reading(List(61) { 0.5f }, List(61) { index -> index * 33_333_333L })
        assertEquals(30f, reading.fps, 0.5f)
    }

    @Test
    fun verdictsCoverTheRange() {
        assertTrue(Pulse.verdict(45).contains("спортсмен"))
        assertTrue(Pulse.verdict(72).contains("обычный"))
        assertTrue(Pulse.verdict(130).contains("отдохните"))
    }

    private fun stamps(fps: Float, seconds: Float): List<Long> {
        val count = (fps * seconds).toInt()
        val step = (1_000_000_000f / fps).toLong()
        return List(count) { index -> index * step }
    }

    // ————— проверка глаз: острота —————

    @Test
    fun optotypeSizeFollowsTheFiveMinuteRule() {
        // Строка 1,0 на 40 см — это 0,58 мм: знак занимает 5 угловых минут.
        assertEquals(0.58f, Probe.optotypeMm(1f, 400f), 0.03f)
        // Строка вдвое крупнее — знак вдвое больше.
        assertEquals(1.16f, Probe.optotypeMm(0.5f, 400f), 0.06f)
        assertEquals(0f, Probe.optotypeMm(0f, 400f), 0.0001f)
        assertEquals(0f, Probe.optotypeMm(1f, 0f), 0.0001f)
    }

    @Test
    fun gapIsAFifthOfTheOptotype() {
        assertEquals(Probe.optotypeMm(0.8f, 500f) / 5f, Probe.gapMm(0.8f, 500f), 0.0001f)
    }

    @Test
    fun pixelsAreCountedFromScreenDensity() {
        // 0,58 мм при 400 ppi — это около 9 пикселей.
        assertEquals(9, Probe.optotypePx(1f, 400f, 400))
        assertTrue(Probe.optotypePx(0.1f, 400f, 400) > Probe.optotypePx(1f, 400f, 400))
        assertTrue(Probe.optotypePx(1f, 800f, 400) > Probe.optotypePx(1f, 400f, 400))
    }

    @Test
    fun linesArePrintedTheRussianWay() {
        assertEquals("0,5", Probe.lineLabel(0.5f))
        assertEquals("1,0", Probe.lineLabel(1f))
        assertEquals(10, Probe.LINES.size)
        assertEquals(0.1f, Probe.LINES.first())
    }

    @Test
    fun directionsAreStableAndNeverRepeatTwice() {
        val first = Probe.directions(11, 10)
        assertEquals(first, Probe.directions(11, 10))
        assertEquals(10, first.size)
        first.zipWithNext().forEach { (left, right) ->
            assertFalse("нельзя дважды подряд", left == right)
        }
        assertTrue(Probe.directions(12, 10).size == 10)
    }

    @Test
    fun directionAnglesMatchTheRing() {
        assertEquals(0f, Probe.Dir.RIGHT.degrees)
        assertEquals(90f, Probe.Dir.DOWN.degrees)
        assertEquals(180f, Probe.Dir.LEFT.degrees)
        assertEquals(270f, Probe.Dir.UP.degrees)
    }

    // ————— проверка глаз: цвет —————

    @Test
    fun contrastFollowsTheWcagRule() {
        // Чёрное на белом — максимальные 21:1, одинаковые цвета — 1:1.
        assertEquals(21f, Probe.contrast(Triple(255, 255, 255), Triple(0, 0, 0)), 0.3f)
        assertEquals(1f, Probe.contrast(Triple(120, 120, 120), Triple(120, 120, 120)), 0.01f)
        assertTrue(Probe.contrast(Triple(255, 255, 255), Triple(0, 0, 0)) > 10f)
    }

    @Test
    fun luminanceIsWeightedTowardsGreen() {
        assertEquals(1f, Probe.luminance(255, 255, 255), 0.01f)
        assertEquals(0f, Probe.luminance(0, 0, 0), 0.01f)
        // Зелёный канал в формуле самый весомый.
        assertTrue(Probe.luminance(0, 255, 0) > Probe.luminance(0, 0, 255))
    }

    @Test
    fun contrastVerdictsSaySomethingUseful() {
        assertTrue(Probe.contrastVerdict(8f).contains("солнце"))
        assertTrue(Probe.contrastVerdict(4.6f).contains("нормальный"))
        assertTrue(Probe.contrastVerdict(1.2f).contains("не видно"))
    }

    @Test
    fun platesAreDeterministic() {
        val first = Probe.plate(7, seed = 5, dots = 150)
        val second = Probe.plate(7, seed = 5, dots = 150)
        assertEquals(first.size, second.size)
        assertEquals(first.first(), second.first())
        assertEquals(first.last(), second.last())
        // Другое зерно — другая картинка, иначе тест был бы бессмысленным.
        assertFalse(first.first() == Probe.plate(7, seed = 6, dots = 150).first())
    }

    @Test
    fun plateActuallyContainsTheDigit() {
        val eight = Probe.plate(8, seed = 3, dots = 300)
        val one = Probe.plate(1, seed = 3, dots = 300)
        assertTrue("в восьмёрке точек больше, чем в единице", Probe.figureDots(eight) > Probe.figureDots(one))
        assertTrue(Probe.figureDots(eight) > 20)
        assertTrue(Probe.figureShare(eight) in 0.05f..0.95f)
        assertTrue(Probe.plate(8, seed = 3, dots = 300).all { it.radius > 0f })
    }

    @Test
    fun sevenSegmentGeometryIsRight() {
        assertEquals(setOf(0, 1, 2), Probe.segmentsOf(7))
        assertEquals(7, Probe.segmentsOf(8).size)
        assertTrue(Probe.segmentsOf(99).isEmpty())
        // Точка в верхней полоске попадает в знак, точка в углу — нет.
        assertTrue(Probe.insideDigit(0.5f, 0.14f, 0))
        assertFalse(Probe.insideDigit(0.02f, 0.02f, 0))
    }

    @Test
    fun numbersForPlatesAreStableAndReasonable() {
        val value = Probe.numberFor(9)
        assertEquals(value, Probe.numberFor(9))
        assertTrue(value in 1..99)
        val values = (1..40).map { Probe.numberFor(it) }
        assertTrue("нужны и однозначные, и двузначные", values.any { it < 10 } && values.any { it >= 10 })
    }

    @Test
    fun twoDigitNumbersUseBothHalves() {
        // 42: слева четвёрка, справа двойка. Верхняя полоска есть у двойки, но не у четвёрки.
        assertTrue(Probe.insideNumber(0.72f, 0.20f, 42))
        assertFalse(Probe.insideNumber(0.28f, 0.20f, 42))
        // 12: слева единица — у неё есть правая стойка.
        assertTrue(Probe.insideNumber(0.38f, 0.30f, 12))
    }

    // ————— связь: профиль телефона —————

    @Test
    fun profileLineTellsTheTruthWithoutSecrets() {
        val info = DeviceProfile.Info(
            model = "POCO X8 PRO MAX",
            brand = "Xiaomi",
            androidSdk = 36,
            androidLabel = "16",
            hasNfc = true,
            supportsOverlay = true,
            reason = "проверка",
            tier = DeviceProfile.TIER_FULL,
            width = 1220,
            height = 2712
        )
        val line = DeviceProfile.shareLine(info, 84)
        assertTrue(line.contains("POCO X8 PRO MAX"))
        assertTrue(line.contains("Android 16"))
        assertTrue(line.contains("84 %"))
        assertTrue(line.contains("NFC"))
        assertTrue(line.contains("1220×2712"))
        // Ни токена, ни адреса репозитория в строке быть не может.
        assertFalse(line.contains("ghp_"))
        assertFalse(line.contains("github"))
        val noBattery = DeviceProfile.shareLine(info.copy(width = 0, height = 0), -1)
        assertFalse(noBattery.contains("заряд"))
        assertFalse(noBattery.contains("экран"))
    }

    // ————— связь страниц с каталогом —————

    @Test
    fun newPagesAreInTheRouter() {
        val source = File("src/main/java/com/gsynk/app/ui/Gsui4Screens.kt").readText()
        listOf("echo", "eyes", "pulse").forEach { id ->
            assertTrue("страница " + id + " объявлена, но роутер её не знает", Gsui4PageIds.contains(id))
            assertTrue("в разборщике нет ветки для " + id, source.contains("\"" + id + "\" -> {"))
        }
    }

    @Test
    fun newPagesHaveTiles() {
        val routes = Catalog.ALL.map { it.route }
        listOf("echo", "eyes", "pulse").forEach { id ->
            assertTrue("у страницы " + id + " нет плитки в каталоге", routes.contains("page:" + id))
        }
    }

    @Test
    fun newTilesDoNotRepeatIds() {
        val ids = Catalog.ALL.map { it.id }
        assertEquals("повтор плиток в каталоге", ids.size, ids.distinct().size)
    }
}
