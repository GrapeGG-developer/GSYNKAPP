package com.gsynk.app

import com.gsynk.app.core.Anonymity
import com.gsynk.app.core.LowLevel
import com.gsynk.app.core.ReleaseNews
import com.gsynk.app.ui.Catalog
import com.gsynk.app.ui.GiouiPageIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 5.1: анонимность, низкоуровневый вывод, новости и новая структура каталога. */
class AnonymityLowLevelTest {

    @Test
    fun nicknamesAndPersonaAreDeterministicFromSeed() {
        val first = Anonymity.nicknames("тихий виноград", 12)
        val second = Anonymity.nicknames("тихий виноград", 12)
        assertEquals(first, second)
        assertEquals(12, first.size)
        assertEquals(first.size, first.distinct().size)
        assertTrue(first.all { it.matches(Regex("[A-Za-z0-9_.-]+")) })

        val personaA = Anonymity.persona("одно зерно")
        val personaB = Anonymity.persona("одно зерно")
        assertEquals(personaA, personaB)
        assertTrue(personaA.nickname.isNotBlank())
        assertTrue(personaA.bio.isNotBlank())
        assertNotEquals(
            Anonymity.stableSeed("зерно"),
            Anonymity.stableSeed("другое зерно")
        )
    }

    @Test
    fun avatarConstructorBuildsStableGrids() {
        Anonymity.AvatarStyle.entries.forEach { style ->
            val cells = Anonymity.avatarCells("avatar-seed", style)
            assertEquals(64, cells.size)
            assertEquals(cells, Anonymity.avatarCells("avatar-seed", style))
            assertTrue("аватар не должен быть пустым", cells.any { it })
            assertTrue("аватар не должен быть полностью залитым", cells.any { !it })
        }
        val symmetric = Anonymity.avatarCells("зеркало", Anonymity.AvatarStyle.SYMMETRIC)
        for (y in 0 until 8) for (x in 0 until 8) {
            assertEquals(symmetric[y * 8 + x], symmetric[y * 8 + (7 - x)])
        }
        val palette = Anonymity.avatarPalette("avatar-seed")
        listOf(palette.first, palette.second, palette.third).forEach { argb ->
            assertEquals(0xFF, argb ushr 24)
        }
    }

    @Test
    fun traceInspectorFindsDirectIdentifiersAndAcceptsCleanText() {
        val dirty = Anonymity.inspectText(
            "Пиши на mail@example.com, мой телефон +7 900 123-45-67, профиль https://example.com/me, живу в Казани"
        )
        assertTrue(dirty.any { it.title.contains("почты") && it.risk >= 3 })
        assertTrue(dirty.any { it.title.contains("телефон") })
        assertTrue(dirty.any { it.title.contains("ссылки") })
        assertTrue(dirty.any { it.title.contains("Контекст") })

        val clean = Anonymity.inspectText("Собираю интерфейсы, читаю документацию и люблю тихие плейлисты.")
        assertEquals(1, clean.size)
        assertEquals(0, clean.first().risk)
    }

    @Test
    fun lowLevelViewsExposeBytesEndianHashesAndCpu() {
        val bytes = "AБ".toByteArray(Charsets.UTF_8) // 41 D0 91
        val hex = LowLevel.hexDump(bytes)
        assertTrue(hex.contains("41 D0 91"))
        assertTrue(hex.contains("|A··|"))

        val binary = LowLevel.binaryDump(bytes)
        assertTrue(binary.contains("01000001"))
        assertTrue(binary.contains("11010000"))

        val endian = LowLevel.endianView(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        assertTrue(endian.contains("BE=01020304"))
        assertTrue(endian.contains("LE=04030201"))

        val sums = LowLevel.checksums("GSYNK".toByteArray(Charsets.UTF_8))
        assertTrue(sums.contains("CRC32:"))
        assertTrue(sums.contains("SHA-256:"))
        assertTrue(sums.contains("байтов: 5"))

        val trace = LowLevel.traceCpu(byteArrayOf(1, 2, 3, 4))
        assertEquals(5, trace.size) // четыре шага + HALT
        assertEquals("HALT", trace.last().instruction)
        assertTrue(trace.any { it.instruction.startsWith("LOAD A") })
        assertTrue(trace.any { it.instruction.startsWith("XOR B") })
    }

    @Test
    fun releaseNewsIsCompleteFor52() {
        assertEquals("5.4.0", ReleaseNews.VERSION)
        assertTrue(ReleaseNews.items.size >= 5)
        assertEquals(ReleaseNews.items.size, ReleaseNews.items.map { it.id }.distinct().size)
        ReleaseNews.items.forEach { item ->
            assertTrue(item.title.isNotBlank())
            assertTrue(item.body.isNotBlank())
            assertTrue(item.points.size >= 3)
            assertTrue(item.iconKey.isNotBlank())
        }
    }

    @Test
    fun catalog52HasDirectCategoriesScheduleAnonymityAndNoDeletedEntries() {
        assertEquals(10, Catalog.Group.entries.size)
        assertNotNull(Catalog.byId("anonymity"))
        assertNotNull(Catalog.byId("avatarlab"))
        assertNotNull(Catalog.byId("ganonym"))
        assertNotNull(Catalog.byId("lowlevel"))
        assertNotNull(Catalog.byId("news"))
        assertEquals(Catalog.Group.ANON, Catalog.byId("ganonym")?.group)
        assertEquals(Catalog.Group.SCHEDULE, Catalog.byId("schedule")?.group)
        assertEquals("panel:schedule", Catalog.byId("schedule")?.route)
        assertEquals("panel:schedule_new", Catalog.byId("schedulenew")?.route)

        listOf("island", "folders", "nfc", "nfcmark").forEach { id ->
            assertEquals("удалённая плитка вернулась: " + id, null, Catalog.byId(id))
        }
        val routes = Catalog.ALL.map { it.route }
        assertFalse(routes.contains("panel:nfc"))
        assertFalse(routes.contains("page:folders"))
        assertFalse(routes.contains("page:island"))
        assertEquals("tool:nearby", Catalog.byId("nearby")?.route)
        assertTrue(Catalog.byId("nearby")!!.matches("nfc"))
        assertTrue(Catalog.byId("nearby")!!.matches("grape link"))

        listOf("news", "anonymity", "nickgen", "avatarlab", "tracecheck", "ganonym", "cipherpig", "gstls").forEach { page ->
            assertTrue("страница не зарегистрирована: " + page, GiouiPageIds.contains(page))
        }
    }
}
