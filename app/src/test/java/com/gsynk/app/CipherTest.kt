package com.gsynk.app

import com.gsynk.app.core.Ciphers
import com.gsynk.app.core.Skin
import com.gsynk.app.ui.Catalog
import com.gsynk.app.ui.GiouiPageIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Шифраторы GIOUI/5.0: каждый шифр обязан честно возвращать исходный текст,
 * автоопределение — узнавать свои шифры, а битые данные — получать отказ, а не мусор.
 */
class CipherTest {

    private fun ok(outcome: Ciphers.Outcome): String {
        assertTrue("ждали успех, а там " + outcome, outcome is Ciphers.Outcome.Ok)
        return (outcome as Ciphers.Outcome.Ok).text
    }

    private fun err(outcome: Ciphers.Outcome): String {
        assertTrue("ждали отказ, а там " + outcome, outcome is Ciphers.Outcome.Err)
        return (outcome as Ciphers.Outcome.Err).message
    }

    private fun roundTrip(id: String, plain: String, key: String = ""): String {
        val coded = ok(Ciphers.encode(id, plain, key))
        assertEquals("шифр " + id + " не вернул исходник", plain, ok(Ciphers.decode(id, coded, key)))
        return coded
    }

    private val sample = "Привет, GSYNK! Виноград созрел в 2026 году."

    @Test
    fun registryIsCompleteAndUnique() {
        val ids = Ciphers.ALL.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue("шифров должно быть много", Ciphers.ALL.size >= 18)
        Ciphers.Family.entries.forEach { family ->
            assertTrue("пустая семья " + family.title, Ciphers.ALL.any { it.family == family })
        }
        // шифры пользователя на месте все до единого
        listOf("bin", "d67", "d42", "gs", "gonly", "nat9", "emoji", "fruit").forEach { id ->
            assertNotNull("нет шифра " + id, Ciphers.ALL.firstOrNull { it.id == id })
        }
        // популярные и уникальные — тоже
        listOf("base64", "hex", "url", "morse", "caesar", "xor", "gs32", "g85", "vine", "lock").forEach { id ->
            assertNotNull("нет шифра " + id, Ciphers.ALL.firstOrNull { it.id == id })
        }
    }

    @Test
    fun everyCipherRoundTripsRussian() {
        Ciphers.ALL.filter { it.id != "morse" }.forEach { info ->
            val key = if (info.needsKey) "виноградный ключ 42" else ""
            roundTrip(info.id, sample, key)
        }
    }

    @Test
    fun emptyAndEdgeTextsRoundTrip() {
        listOf("base64", "bin", "gonly", "nat9", "gs32", "g85", "vine", "fruit").forEach { id ->
            assertEquals("", ok(Ciphers.encode(id, "")))
            roundTrip(id, "я")
            roundTrip(id, "a b\nвторая строка\tтаб")
        }
    }

    @Test
    fun morseIsHonestAboutAlphabet() {
        roundTrip("morse", "SOS GSYNK 2026")
        val coded = ok(Ciphers.encode("morse", "SOS"))
        assertEquals("... --- ...", coded)
        err(Ciphers.encode("morse", "привет")) // кириллицу морзе честно отказывается нести
    }

    @Test
    fun caesarShiftsBothAlphabets() {
        roundTrip("caesar", sample)
        val coded = ok(Ciphers.encode("caesar", "абв ABC"))
        assertNotEquals("абв ABC", coded)
        assertEquals("абв ABC", ok(Ciphers.decode("caesar", coded)))
    }

    @Test
    fun userAlphabetsReallyUseOnlyTheirSymbols() {
        val d67 = roundTrip("d67", sample)
        assertTrue(d67.all { it == '6' || it == '7' || it == ' ' })
        val d42 = roundTrip("d42", sample)
        assertTrue(d42.all { it == '4' || it == '2' || it == ' ' })
        val gs = roundTrip("gs", sample)
        assertTrue(gs.all { it == 'G' || it == 'S' || it == ' ' })
        val gonly = roundTrip("gonly", sample)
        assertTrue(gonly.all { it == 'G' || it == ' ' })
        assertTrue(gonly.split(' ').all { it == "G" || it == "GG" })
        val nat9 = roundTrip("nat9", sample)
        assertTrue("в натуральном шифре не должно быть нуля", nat9.none { it == '0' })
        assertTrue(nat9.all { it in '1'..'9' || it == ' ' })
        val bin = roundTrip("bin", sample)
        assertTrue(bin.all { it == '0' || it == '1' || it == ' ' })
        val fruit = roundTrip("fruit", sample)
        assertTrue("виноград обязателен", fruit.contains("\uD83C\uDF47"))
        val emoji = roundTrip("emoji", sample)
        assertTrue(emoji.contains("\uD83D\uDE21") || emoji.contains("\uD83D\uDE0F"))
    }

    @Test
    fun uniqueCiphersHaveTheirFaces() {
        val gs32 = roundTrip("gs32", sample)
        assertTrue(gs32.startsWith("GS32:"))
        assertTrue(gs32.removePrefix("GS32:").all { it in "GSYNK23456789ABCDEFHJLMPQRTUVWXZ" })
        val g85 = roundTrip("g85", sample)
        assertTrue(g85.startsWith("G85:"))
        assertTrue("GS-85 обязан быть заметно короче двоичного", g85.length < ok(Ciphers.encode("bin", sample)).length / 4)
        val vine = roundTrip("vine", sample)
        val vineWords = setOf(
            "лоза", "гроздь", "ягода", "сок", "лист", "вино", "сад", "кисть",
            "мускат", "рислинг", "шардоне", "мерло", "каберне", "изабелла", "дамский", "тайфи"
        )
        assertTrue(vine.split('-').all { it in vineWords })
    }

    @Test
    fun lockIsRealEncryption() {
        val first = roundTrip("lock", sample, "правильный пароль")
        assertTrue(first.startsWith("GSLOCK1."))
        val second = ok(Ciphers.encode("lock", sample, "правильный пароль"))
        assertNotEquals("соль случайна — шифры одного текста должны отличаться", first, second)
        err(Ciphers.decode("lock", first, "не тот пароль"))
        err(Ciphers.decode("lock", first, ""))
        err(Ciphers.encode("lock", sample, ""))
        // подмена хоть одного символа ломает замок (GCM это замечает)
        val tampered = first.dropLast(2) + if (first.endsWith("A")) "BC" else "AA"
        err(Ciphers.decode("lock", tampered, "правильный пароль"))
    }

    @Test
    fun xorNeedsItsKey() {
        err(Ciphers.encode("xor", sample, ""))
        val coded = roundTrip("xor", sample, "фраза")
        val wrong = Ciphers.decode("xor", coded, "совсем другая фраза")
        assertTrue("чужой ключ не должен давать исходник", wrong !is Ciphers.Outcome.Ok || wrong.text != sample)
    }

    @Test
    fun detectionRecognisesEveryCipher() {
        val keyed = mapOf("xor" to "ключ", "lock" to "пароль")
        listOf(
            "base64", "hex", "url", "bin", "d67", "d42", "gs", "gonly", "nat9",
            "emoji", "fruit", "gs32", "g85", "vine"
        ).forEach { id ->
            val coded = ok(Ciphers.encode(id, sample))
            val detected = Ciphers.detect(coded)
            assertNotNull("автоопределение не узнало " + id, detected)
            assertEquals("автоопределение перепутало " + id, id, detected!!.first.id)
            assertEquals(sample, ok(Ciphers.decode(detected.first.id, coded)))
        }
        // морзе определяется по латинскому образцу
        val morse = ok(Ciphers.encode("morse", "GSYNK RULES"))
        assertEquals("morse", Ciphers.detect(morse)?.first?.id)
        // закрытые ключами узнаются по префиксу, даже без ключа в руках
        keyed.forEach { (id, key) ->
            val coded = ok(Ciphers.encode(id, sample, key))
            assertEquals(id, Ciphers.detect(coded)?.first?.id)
        }
        // обычный русский текст и цезаревский сдвиг автоопределение не должно выдумывать
        assertNull(Ciphers.detect(sample))
        assertNull(Ciphers.detect(ok(Ciphers.encode("caesar", sample))))
        assertNull(Ciphers.detect(""))
        assertNull(Ciphers.detect("я"))
    }

    @Test
    fun brokenCiphersGetRefusalsNotGarbage() {
        val bin = ok(Ciphers.encode("bin", sample))
        err(Ciphers.decode("bin", bin.dropLast(1)))             // потерян бит
        err(Ciphers.decode("nat9", "1029384756"))               // ноль запрещён
        err(Ciphers.decode("hex", "abc"))                       // нечётная длина
        err(Ciphers.decode("gonly", "GGG G"))                   // куски только G и GG
        err(Ciphers.decode("gs32", "GS32:привет"))              // чужие символы
        err(Ciphers.decode("base64", "это вообще не base64!!")) // мусор вместо данных
        err(Ciphers.decode("vine", "лоза-арбуз-гроздь-сок"))    // слово не из набора
        err(Ciphers.decode("bin", ok(Ciphers.encode("d67", sample)))) // чужой алфавит
    }

    @Test
    fun tooLongTextIsRefused() {
        val huge = "а".repeat(Ciphers.MAX_CHARS + 1)
        err(Ciphers.encode("bin", huge))
    }

    @Test
    fun cryptoCategoryIsInCatalogAndRouted() {
        val crypto = Catalog.Group.CRYPTO
        assertEquals("шифраторы", crypto.title)
        val inside = Catalog.ALL.filter { it.group == crypto }
        assertTrue("в шифраторах должны жить и старые кодеры", inside.size >= 7)
        listOf("ciphers", "cipherlock", "ciphergrape", "cipherauto").forEach { page ->
            assertTrue("страница " + page + " не в роутере GIOUI", GiouiPageIds.contains(page))
            assertNotNull("плитки страницы " + page + " нет", Catalog.ALL.firstOrNull { it.route == "page:" + page })
        }
        // расфасовка по смыслу: кодер, пароли и морзянка переехали в шифраторы
        listOf("coder", "password", "morse").forEach { id ->
            assertEquals(id, Catalog.byId(id)?.id)
            assertEquals(crypto, Catalog.byId(id)?.group)
        }
        // и по-прежнему: каждая функция ровно в одной категории, пустых категорий нет
        Catalog.Group.entries.forEach { group ->
            assertTrue("пустая категория " + group.title, Catalog.ALL.any { it.group == group })
        }
    }

    @Test
    fun giouiSkinCanonIsUnified() {
        val values = Skin.Values()
        assertEquals(24, values.tile)
        assertEquals(24, values.card)
        assertEquals(28, values.sheet)
        assertEquals(16, values.button)
        assertEquals(16, values.toggle)
        // старая квадратная форма через clamp не просочится
        val clamped = Skin.clamp(Skin.Values(tile = 0, card = 0, button = 0, toggle = 0, sheet = 0))
        assertTrue(clamped.tile >= 12 && clamped.card >= 12 && clamped.button >= 10 && clamped.sheet >= 16)
        assertTrue(Skin.PRESETS.any { it.first == "gioui" })
        assertTrue(Skin.PRESETS.none { it.first == "cube" || it.first == "metri" })
    }
}
