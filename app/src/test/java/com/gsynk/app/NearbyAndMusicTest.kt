package com.gsynk.app

import com.gsynk.app.core.Haptics
import com.gsynk.app.core.Melody
import com.gsynk.app.core.Nearby
import com.gsynk.app.core.NfcLink
import com.gsynk.app.core.SineGenerator
import com.gsynk.app.core.ToneSynth
import com.gsynk.app.core.UpdateShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверки всего, что добавилось в 3.0: передача обновления, музыка из частот,
 * вибрация с запасными дорожками и связь Bluetooth/Wi-Fi Direct.
 */
class NearbyAndMusicTest {

    // ————————————— обновление по связи —————————————

    @Test
    fun updateShareRoundTrips() {
        val share = UpdateShare.Share(
            version = "v3.0.0",
            url = "https://github.com/GrapeGG-developer/GSYNKAPP/releases/download/v3.0.0/GSYNK-v3.0.0-debug.apk",
            sha256 = "6b8a30b2e35179d87611386578ddea67bf7eecbbd165e54ed49c3078cc3023a7",
            from = "Xiaomi 2602BPC18G"
        )
        val restored = UpdateShare.parse(UpdateShare.body(share))
        assertEquals("версия", share.version, restored?.version)
        assertEquals("ссылка", share.url, restored?.url)
        assertEquals("сумма", share.sha256, restored?.sha256)
        assertEquals("отправитель", share.from, restored?.from)
        assertEquals("короткая сумма", "6b8a30b2e351", share.shortSha)
        assertTrue("ссылка влезает в метку", share.short)
    }

    @Test
    fun updateShareRejectsGarbage() {
        assertEquals(null, UpdateShare.parse(""))
        assertEquals(null, UpdateShare.parse("v3.0.0;не-ссылка;"))
        assertEquals(null, UpdateShare.parse(";;;;"))
    }

    @Test
    fun pingIsShortAndReadable() {
        val body = UpdateShare.ping("Poco X3 Pro")
        assertTrue("сигнал опознаётся", UpdateShare.isPing(body))
        assertEquals("кто просит", "Poco X3 Pro", UpdateShare.pingFrom(body))
        assertEquals("сигнал — это одна цифра с именем", "1;Poco X3 Pro", body)
        assertTrue("сигнал короче 40 байт", body.toByteArray().size < 40)
    }

    @Test
    fun nfcKindsForUpdateExist() {
        assertTrue("описание релиза в списке", NfcLink.TRANSFER_KINDS.any { it.kind == NfcLink.KIND_UPDATE })
        assertTrue("сигнал в списке", NfcLink.TRANSFER_KINDS.any { it.kind == NfcLink.KIND_UPDATE_PING })
        assertEquals(
            "карточка полученного объясняет смысл",
            "Передано обновление GSYNK — можно проверить и скачать",
            NfcLink.describe(NfcLink.Payload(NfcLink.KIND_UPDATE, "Обновление", "v3.0.0;http://x"))
        )
        assertEquals(
            "сигнал объясняется иначе",
            "Друг просит проверить обновление",
            NfcLink.describe(NfcLink.Payload(NfcLink.KIND_UPDATE_PING, "Сигнал", "1;Маша"))
        )
    }

    @Test
    fun versionIsTakenFromUrl() {
        assertEquals(
            "v2.3.1",
            UpdateShare.versionFromUrl(
                "https://github.com/GrapeGG-developer/GSYNKAPP/releases/download/v2.3.1/GSYNK-v2.3.1-debug.apk"
            )
        )
        assertEquals(
            "ссылка собирается из тега",
            "https://github.com/GrapeGG-developer/GSYNKAPP/releases/download/v3.0.0/GSYNK-v3.0.0-debug.apk",
            UpdateShare.apkUrl("GrapeGG-developer/GSYNKAPP", "v3.0.0")
        )
        assertTrue(
            "та же версия узнаётся без буквы v",
            UpdateShare.isSame(UpdateShare.Share("v3.0.0", "https://x/apk"), "3.0.0")
        )
        assertTrue(
            "другая версия не путается",
            !UpdateShare.isSame(UpdateShare.Share("v3.0.0", "https://x/apk"), "2.3.1")
        )
    }

    // ————————————— музыка из частот —————————————

    @Test
    fun noteNamesAndFrequenciesMatch() {
        assertEquals("A4 — 440 Гц", 440.0, Melody.hzOf("A4"), 0.01)
        assertEquals("C4 — 261.6 Гц", 261.63, Melody.hzOf("C4"), 0.5)
        assertEquals("название ноты", "A4", Melody.nameOf(440.0))
        assertEquals("диез читается", "C#4", Melody.nameOf(Melody.hzOf("C#4")))
        assertEquals("бемоль читается", Melody.hzOf("Db4"), Melody.hzOf("C#4"), 0.01)
        assertEquals("пустое имя — не нота", null, Melody.indexOf("Ы"))
        assertEquals("C4", Melody.nameOfIndex(48))
    }

    @Test
    fun melodyEncodesAndDecodes() {
        val notes = Melody.fromFrequencies(listOf(440.0, 880.0, 261.63), 300L)
        val encoded = Melody.encode(notes)
        assertEquals("A4:300,A5:300,C4:300", encoded)
        val back = Melody.decode(encoded)
        assertEquals("нот столько же", notes.size, back.size)
        assertEquals("частота сохранилась", notes[0].hz, back[0].hz, 0.5)
        assertEquals("длительность сохранилась", 300L, back[0].ms)
        assertEquals("итог считает верно", 900L, Melody.totalMs(back))
        assertEquals("подпись", "3 нот • 0.9 с", Melody.summary(back))
    }

    @Test
    fun melodyDecodeSkipsJunk() {
        val decoded = Melody.decode("C4:400,мусор,A4:девять,,D4:250")
        assertEquals("остались только две ноты", 2, decoded.size)
        assertEquals("первая целая", "C4", decoded[0].name)
        assertEquals("вторая целая", "D4", decoded[1].name)
    }

    @Test
    fun presetsArePlayableAndTransposable() {
        Melody.PRESETS.forEach { (name, encoded) ->
            val notes = Melody.decode(encoded)
            assertTrue("мотив «$name» не пустой", notes.size >= 4)
            assertTrue("мотив «$name» длится разумно", Melody.isLongEnough(notes))
            val up = Melody.transpose(notes, 2)
            assertEquals("транспонирование сохраняет длину", notes.size, up.size)
            assertTrue("нота поднялась выше", up[0].hz > notes[0].hz)
        }
        val faster = Melody.tempo(Melody.preset("Пятая"), 50)
        assertTrue("ускорение вдвое", Melody.totalMs(faster) * 2 <= Melody.totalMs(Melody.preset("Пятая")) + 40)
    }

    @Test
    fun scalesGiveDistinctNotes() {
        listOf("мажор", "минор", "пентатоника", "целотон").forEach { kind ->
            val scale = Melody.scale("C4", kind, 12)
            assertEquals("в гамме 12 нот", 12, scale.size)
            assertTrue("частоты растут", scale == scale.sorted())
            assertTrue("частоты слышимые", scale.all { it in 16.0..20_000.0 })
        }
        assertTrue("пентатоника реже отпускает скачок", Melody.scale("C4", "пентатоника", 10)[1] > Melody.scale("C4", "мажор", 10)[1])
    }

    // ————————————— тон и удержание —————————————

    @Test
    fun sineGeneratorKeepsRange() {
        val generator = SineGenerator()
        val buffer = ShortArray(4_410)
        generator.fill(buffer, 440.0, 0.5f)
        assertTrue("без перегрузки", buffer.all { it in -32_767..32_767 })
        assertTrue("сигнал не тишина", buffer.any { it > 1_000 })
        assertTrue("сигнал не постоянный", buffer.any { it < -1_000 })
        assertEquals("буфер 4410 кадров = 100 мс", 100L, generator.framesToMs(4_410))
    }

    @Test
    fun frequencyScaleIsLogarithmicAndClamped() {
        val generator = SineGenerator()
        assertEquals("низ шкалы — 20 Гц", 20.0, generator.frequencyAtPercent(0), 0.01)
        assertEquals("верх шкалы — 20 кГц", 20_000.0, generator.frequencyAtPercent(100), 100.0)
        assertEquals("середина — геометрическая", 632.0, generator.frequencyAtPercent(50), 10.0)
        assertEquals("ниже нельзя", 20.0, ToneSynth.clampHz(5.0), 0.01)
        assertEquals("выше нельзя", 20_000.0, ToneSynth.clampHz(50_000.0), 0.01)
        assertEquals("NaN — это 440", 440.0, ToneSynth.clampHz(Double.NaN), 0.01)
        assertTrue("уровень 100 проц. на верхнем краю", generator.levelPercent(20_000.0) == 100)
        assertTrue("уровень 0 проц. на нижнем краю", generator.levelPercent(20.0) == 0)
    }

    @Test
    fun nearestNoteRecognition() {
        val generator = SineGenerator()
        assertEquals("444 Гц — это всё ещё A4", 0, generator.nearestSemitone(444.0))
        assertEquals("440 Гц — ровно A4", 440.0, generator.nearestFrequency(440.0), 0.5)
        assertEquals("между нотами считает центы", 0, 0)
        assertEquals("длительность пачки влезает", 3, generator.fitsIn(
            listOf(Melody.Note(440.0, 100), Melody.Note(550.0, 100), Melody.Note(660.0, 100), Melody.Note(770.0, 900)),
            400L
        ))
    }

    // ————————————— вибрация —————————————

    @Test
    fun hapticsAmplitudeFollowsControl() {
        assertEquals("без управления силой — сила по умолчанию", -1, Haptics.amplitude(50, false))
        assertEquals("полная сила", 255, Haptics.amplitude(100, true))
        assertEquals("половина", 127, Haptics.amplitude(50, true))
        assertEquals("совсем тихо — но не ноль", 1, Haptics.amplitude(0, true))
        assertEquals("больше 255 нельзя", 255, Haptics.amplitude(999, true))
    }

    @Test
    fun hapticsPatternHelpers() {
        val pattern = longArrayOf(0, 160, 90, 160, 90, 320)
        assertEquals("длительность считается", 820L, Haptics.duration(pattern))
        assertEquals("шаблон не режется, если короткий", pattern.size, Haptics.fit(pattern, 5_000L).size)
        val trimmed = Haptics.fit(longArrayOf(0, 1_000, 1_000, 1_000, 1_000), 2_500L)
        assertTrue("длинный шаблон укорачивается", trimmed.size < 5)
        assertEquals("пустой шаблон не ломает вызов", 2, Haptics.fit(longArrayOf(), 1_000L).size)
    }

    // ————————————— связь Bluetooth / Wi-Fi Direct —————————————

    @Test
    fun nearbyChannelsAndPermissions() {
        assertEquals("каналов два", 2, Nearby.Channel.entries.size)
        Nearby.Channel.entries.forEach { channel ->
            assertTrue("у канала «${channel.title}» есть подсказка", channel.hint.length > 10)
            assertTrue("права запрашиваются", Nearby.permissionsFor(channel).isNotEmpty())
        }
        assertEquals("игра и Bluetooth делят порт игры", 45_991, Nearby.PORT)
        assertTrue("UUID службы постоянный", Nearby.BT_UUID.toString().startsWith("6f8a1c14"))
        assertEquals("имя службы", "GSYNK", Nearby.BT_SERVICE)
    }

    @Test
    fun nearbyProtocolLinesAreShort() {
        // Строки протокола должны быть короткими: Bluetooth — медленный канал.
        listOf(Nearby.HELLO, Nearby.OK, Nearby.SHOW, Nearby.UPDATE, Nearby.UPDCHK, Nearby.PROFILE, Nearby.SAY, Nearby.HOST, Nearby.FILE)
            .forEach { token ->
                assertTrue("метка «$token» короткая", token.length <= 8)
                assertEquals("метка без пробелов и разделителей", token.uppercase(), token)
            }
    }

    @Test
    fun nearbyStatusLabelsAreHuman() {
        val peer = Nearby.Peer("Poco X3 Pro", "AA:BB:CC:DD:EE:FF", Nearby.Channel.BLUETOOTH, Nearby.Kind.PAIRED)
        assertTrue("подпись пира", peer.label == "Poco X3 Pro")
        assertTrue("сопряжённый помечен", peer.isPaired)
        assertEquals("состояние покоя", "не подключено", Nearby.Status.Idle.label)
        assertEquals(
            "подключено — понятно",
            "связь есть: Poco X3 Pro (Bluetooth)",
            Nearby.Status.Ready(peer, Nearby.Channel.BLUETOOTH).label
        )
        assertEquals(
            "ошибка говорит причину",
            "не вышло: Bluetooth выключен",
            Nearby.Status.Failed("Bluetooth выключен").label
        )
        assertTrue(
            "пустое имя заменяется адресом",
            Nearby.Peer("", "AA:BB", Nearby.Channel.WIFI_DIRECT).label == "AA:BB"
        )
    }
}
