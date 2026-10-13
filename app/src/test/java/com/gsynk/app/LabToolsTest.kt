package com.gsynk.app

import com.gsynk.app.core.DeviceLab
import com.gsynk.app.core.DeviceProfile
import com.gsynk.app.core.NfcLink
import com.gsynk.app.core.NfcShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверки лаборатории: расчёты железа, ИК-коды, катушки и новый обмен по NFC.
 *
 * Тесты держат самое хрупкое: формулы датчиков, разбор кодов пульта и то, что
 * «показ по касанию» и передача файла переживают дорогу через метку.
 */
class LabToolsTest {

    // ————— датчики —————

    @Test
    fun magneticStrengthIsVectorLength() {
        assertEquals(0f, DeviceLab.magneticStrength(0f, 0f, 0f), 0.001f)
        assertEquals(5f, DeviceLab.magneticStrength(3f, 4f, 0f), 0.001f)
        assertEquals(50f, DeviceLab.magneticStrength(30f, 40f, 0f), 0.001f)
    }

    @Test
    fun compassHeadingPointsNorth() {
        // магнитное поле по оси X — азимут 0 или 360 градусов
        val north = DeviceLab.compassHeading(30f, 0f)
        assertTrue("север должен быть около нуля, а не $north", north < 1f || north > 359f)
        // поле на -Y — это 90 градусов по часовой
        assertEquals(90f, DeviceLab.compassHeading(0f, -30f), 1f)
    }

    @Test
    fun tiltIsZeroWhenFlat() {
        val (pitch, roll) = DeviceLab.tiltDegrees(0f, 0f)
        assertEquals(0f, pitch, 0.5f)
        assertEquals(0f, roll, 0.5f)
    }

    @Test
    fun altitudeFollowsPressure() {
        assertEquals(0f, DeviceLab.altitudeMeters(1013.25f), 2f)
        assertTrue("выше при низком давлении", DeviceLab.altitudeMeters(900f) > 900f)
        assertEquals(0f, DeviceLab.altitudeMeters(0f), 0.01f)
    }

    @Test
    fun decibelsFromAmplitudeMakeSense() {
        assertEquals(0f, DeviceLab.decibelsFromAmplitude(0f), 0.01f)
        val soft = DeviceLab.decibelsFromAmplitude(327f)
        val loud = DeviceLab.decibelsFromAmplitude(32767f)
        assertTrue("громкое должно быть громче тихого", loud > soft)
        // полная шкала 16-битного синуса — примерно 90 дБ в относительной шкале
        assertEquals(90f, loud, 1f)
    }

    // ————— катушки и экран —————

    @Test
    fun buzzPatternsArePlayable() {
        DeviceLab.Buzz.entries.forEach { kind ->
            val pattern = DeviceLab.buzzPattern(kind)
            assertTrue("шаблон " + kind.label + " пустой", pattern.isNotEmpty())
            assertTrue("шаблон " + kind.label + " начинается не с паузы", pattern[0] == 0L)
            assertTrue("шаблон " + kind.label + " нулевой длины", pattern.size >= 2)
        }
    }

    @Test
    fun rhythmKeepsRate() {
        val pattern = DeviceLab.buzzRhythm(8, 2)
        // 8 ударов в секунду × 2 секунды = 16 импульсов: пауза + удар на каждый
        assertEquals(1 + 16 * 2, pattern.size)
        assertEquals(0L, pattern[0])
    }

    @Test
    fun strobeClampKeepsDisplayAlive() {
        assertEquals(0.2f, DeviceLab.strobeClamp(0f), 0.001f)
        assertEquals(30f, DeviceLab.strobeClamp(500f), 0.001f)
        assertEquals(8f, DeviceLab.strobeClamp(8f), 0.001f)
    }

    // ————— ИК-пульт —————

    @Test
    fun necFrameHasCorrectShape() {
        val frame = DeviceLab.Ir.nec(0x0C, 0x0C)
        assertEquals("несущая NEC", 38_000, frame.carrier)
        // старт 9 мс + 4.5 мс, далее 32 бита по два импульса, плюс замыкающий
        assertEquals(2 + 32 * 2 + 1, frame.pattern.size)
        assertEquals(9000, frame.pattern[0])
        assertEquals(4500, frame.pattern[1])
        assertTrue("все длительности положительные", frame.pattern.all { it > 0 })
    }

    // ————— NFC: показ и файлы —————

    @Test
    fun showPayloadSurvivesTouch() {
        val body = NfcShow.encode(NfcShow.Kind.FIREWORKS, "Салют")
        val decoded = NfcShow.decode(body)
        assertNotNull("показ не разобрался", decoded)
        assertEquals(NfcShow.Kind.FIREWORKS, decoded!!.first)
        assertEquals("Салют", decoded.second)
        assertNull("чужой показ должен отбраковываться", NfcShow.decode("неттакое|текст"))
    }

    @Test
    fun fileInviteTravelsThroughTouch() {
        val invite = NfcLink.FileInvite(
            host = "192.168.0.7",
            port = 45_992,
            token = "123456",
            name = "фото лета.jpg",
            size = 2_400_000
        )
        val restored = NfcLink.parseFileInvite(NfcLink.fileInviteBody(invite))
        assertNotNull("приглашение на файл не разобралось", restored)
        assertEquals("192.168.0.7", restored!!.host)
        assertEquals(45_992, restored.port)
        assertEquals("123456", restored.token)
        assertEquals("фото лета.jpg", restored.name)
        assertEquals(2_400_000L, restored.size)
        assertTrue("это сетевой файл, а не метка", !restored.isInline)
    }

    @Test
    fun tinyFileFitsIntoTheTag() {
        val invite = NfcLink.FileInvite(
            host = "",
            port = 0,
            token = "",
            name = "записка.txt",
            size = 12,
            inline = "привет, мир!"
        )
        val restored = NfcLink.parseFileInvite(NfcLink.fileInviteBody(invite))
        assertTrue("крошечный файл должен помечаться как «в метке»", restored?.isInline == true)
        assertEquals("привет, мир!", restored?.inline)
    }

    @Test
    fun tagsAndFramesDoNotBreakOnSeparators() {
        val invite = NfcLink.FileInvite(
            host = "10.0.0.2",
            port = 1,
            token = "9",
            name = "имя;с|разделителями.txt",
            size = 5
        )
        val restored = NfcLink.parseFileInvite(NfcLink.fileInviteBody(invite))
        assertEquals("разделители заменены на подчёркивание", "имя_с_разделителями.txt", restored?.name)
        assertEquals("10.0.0.2", restored?.host)
    }

    // ————— уровни устройств —————

    @Test
    fun standardTierKeepsSafetyAndDropsFlagshipLoad() {
        val info = DeviceProfile.Info(
            model = "POCO X3 Pro",
            brand = "Xiaomi",
            androidSdk = 33,
            androidLabel = "13",
            hasNfc = true,
            supportsOverlay = true,
            reason = "тест",
            tier = DeviceProfile.TIER_STANDARD,
            hasIr = true
        )
        assertTrue("лаборатория нужна и старому телефону", DeviceProfile.feature(info, DeviceProfile.Feature.LAB))
        assertTrue("датчики есть везде", DeviceProfile.feature(info, DeviceProfile.Feature.SENSORS))
        assertTrue("ИК-порт должен включать пульт", DeviceProfile.feature(info, DeviceProfile.Feature.IR))
        assertTrue("NFC-инструменты доступны", DeviceProfile.feature(info, DeviceProfile.Feature.NFC_LINK))
        assertTrue("шейдерное поле с Android 13", DeviceProfile.feature(info, DeviceProfile.Feature.GPU))
        assertTrue(
            "максимальная нагрузка остаётся флагманам",
            !DeviceProfile.feature(info, DeviceProfile.Feature.MAX_LOAD)
        )
        assertEquals("расширенный набор", info.tierLabel)
    }

    @Test
    fun hyperOsPanelOnlyOnXiaomi() {
        val mi = DeviceProfile.Info(
            model = "POCO X3 Pro", brand = "Xiaomi", androidSdk = 33, androidLabel = "13",
            hasNfc = true, supportsOverlay = true, reason = "тест",
            tier = DeviceProfile.TIER_STANDARD, isHyperOs = true
        )
        val other = mi.copy(model = "Pixel", brand = "Google", isHyperOs = false)
        assertTrue(DeviceProfile.feature(mi, DeviceProfile.Feature.HYPEROS))
        assertTrue(!DeviceProfile.feature(other, DeviceProfile.Feature.HYPEROS))
    }
}
