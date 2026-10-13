package com.gsynk.app

import com.gsynk.app.core.CoopCodec
import com.gsynk.app.core.NfcLink
import com.gsynk.app.core.RogueGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Провода подземелья: кодек кадра, ввод игрока и вызов по NFC.
 *
 * Сокеты в тестах не поднимаем — проверяем самое хрупкое: что мир переживает
 * дорогу «телефон → телефон» без потерь, что кириллица в баннере не портится
 * и что старое приглашение без адреса комнаты всё ещё читается.
 */
class CoopTest {

    private fun dungeon(): RogueGame.World {
        val start = RogueGame.start(4_242_424L, 2)
        return start.copy(
            floor = 2,
            room = 3,
            score = 137,
            time = 42.5f,
            doorOpen = true,
            banner = "ЭТАЖ 2 · ПОДЗЕМЕЛЬЕ GSYNK",
            bannerTime = 1.25f
        )
    }

    @Test
    fun world_survivesTheWire() {
        val source = dungeon()
        val restored = CoopCodec.decode(CoopCodec.encode(source))
        assertNotNull("мир не собрался обратно", restored)
        val back = restored!!
        assertEquals("ключ подземелья", source.seed, back.seed)
        assertEquals("этаж", source.floor, back.floor)
        assertEquals("комната", source.room, back.room)
        assertEquals("счёт", source.score, back.score)
        assertEquals("игроков", source.players.size, back.players.size)
        assertEquals("врагов", source.enemies.size, back.enemies.size)
        assertEquals("камней", source.rocks.size, back.rocks.size)
        assertTrue("дверь должна остаться открытой", back.doorOpen)
        assertEquals("время", source.time, back.time, 0.002f)
    }

    @Test
    fun positionsKeepMillimetrePrecision() {
        val source = dungeon()
        val restored = CoopCodec.decode(CoopCodec.encode(source))!!
        source.players.zip(restored.players).forEach { (before, after) ->
            assertEquals("x игрока ${before.id}", before.x, after.x, 0.001f)
            assertEquals("y игрока ${before.id}", before.y, after.y, 0.001f)
            assertEquals("hp игрока ${before.id}", before.hp, after.hp)
        }
        source.rocks.zip(restored.rocks).forEach { (before, after) ->
            assertEquals("x камня", before.x, after.x, 0.001f)
            assertEquals("ширина камня", before.w, after.w, 0.001f)
        }
    }

    @Test
    fun russianBannerSurvives() {
        val source = dungeon().copy(banner = "БОСС · УРОВЕНЬ 3 «ГРАПЕ» | стена", bannerTime = 2.5f)
        val restored = CoopCodec.decode(CoopCodec.encode(source))!!
        assertEquals("баннер с кириллицей", source.banner, restored.banner)
        assertEquals("время баннера", source.bannerTime, restored.bannerTime, 0.002f)
    }

    @Test
    fun playerInputGoesRoundTrip() {
        val input = RogueGame.Input(moveX = -0.75f, moveY = 0.33f, shooting = true)
        val restored = CoopCodec.decodeInput(CoopCodec.encodeInput(input))
        assertNotNull("ввод не разобрался", restored)
        assertEquals("стик по x", input.moveX, restored!!.moveX, 0.001f)
        assertEquals("стик по y", input.moveY, restored.moveY, 0.001f)
        assertTrue("кнопка огня", restored.shooting)
    }

    @Test
    fun handshakeCarriesCodeAndName() {
        val hello = CoopCodec.decodeHello(CoopCodec.encodeHello("4821", "Xiaomi 15"))
        assertNotNull("приветствие не разобралось", hello)
        assertEquals("код комнаты", "4821", hello!!.code)
        assertEquals("имя телефона", "Xiaomi 15", hello.name)
        assertEquals("версия протокола", CoopCodec.VERSION, hello.version)

        assertEquals("ответ хозяина", 777L, CoopCodec.decodeWelcome(CoopCodec.encodeWelcome(777L)))
    }

    @Test
    fun junkIsRefusedInsteadOfCrashing() {
        assertNull("пустая строка", CoopCodec.decode(""))
        assertNull("случайный текст", CoopCodec.decode("привет, это не мир"))
        assertNull("чужой пакет", CoopCodec.decodeInput("I|не число|ещё|0"))
        assertNull("не приветствие", CoopCodec.decodeHello("HELLO"))
        assertNull("не ответ хозяина", CoopCodec.decodeWelcome("нет"))
    }

    @Test
    fun nfcInviteCarriesRoomAddress() {
        val invite = NfcLink.GameInvite(
            game = "rogue",
            from = "Xiaomi 15",
            seed = 909_090L,
            host = "192.168.1.42",
            port = 45_991,
            code = "1234"
        )
        assertTrue("вызов должен считаться сетевым", invite.isWifi)

        val restored = NfcLink.parseGameInvite(NfcLink.gameInviteBody(invite))!!
        assertEquals("адрес хозяина", "192.168.1.42", restored.host)
        assertEquals("порт", 45_991, restored.port)
        assertEquals("код комнаты", "1234", restored.code)
        assertEquals("ключ подземелья", 909_090L, restored.seed)
        assertEquals("отправитель", "Xiaomi 15", restored.from)
    }

    @Test
    fun oldInviteWithoutAddressStillWorks() {
        // так выглядели вызовы в 2.2.0: игра;отправитель;ключ
        val restored = NfcLink.parseGameInvite("rogue;Старый телефон;555")!!
        assertEquals("ключ подземелья", 555L, restored.seed)
        assertTrue("без адреса это не сетевой вызов", !restored.isWifi)
    }
}
