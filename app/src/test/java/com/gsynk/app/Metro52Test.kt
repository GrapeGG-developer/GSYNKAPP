package com.gsynk.app

import com.gsynk.app.core.LifeAutomations
import com.gsynk.app.core.ReleaseNews
import com.gsynk.app.core.Rule
import com.gsynk.app.ui.Catalog
import com.gsynk.app.ui.startLayoutForMaxRows
import com.gsynk.app.ui.startPlacements
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime
import org.junit.Test

/** GSYNK 5.1: альбомный запуск, Metro-«Пуск», прямые плитки и автоматизации жизни. */
class Metro52Test {

    @Test
    fun startPlacementsNeverOverlapAndStayInsideGrid() {
        Catalog.Group.entries.forEach { group ->
            val entries = Catalog.ALL.filter { it.group == group }
            val placements = startPlacements(entries)
            assertEquals(entries.size, placements.size)
            val occupied = mutableSetOf<Pair<Int, Int>>()
            entries.zip(placements).forEach { (entry, placement) ->
                assertTrue(entry.id + " вышел за 5 колонок", placement.x >= 0 && placement.x + placement.w <= 5)
                assertTrue(entry.id + " имеет отрицательную строку", placement.y >= 0)
                for (r in placement.y until placement.y + placement.h) {
                    for (c in placement.x until placement.x + placement.w) {
                        assertTrue("плитки наложились в группе " + group.title + " на " + r + ":" + c, occupied.add(r to c))
                    }
                }
            }
        }
    }

    @Test
    fun landscapeStartLayoutFitsFewRowsWithoutOverlap() {
        listOf(2, 3).forEach { maxRows ->
            Catalog.Group.entries.forEach { group ->
                val entries = Catalog.ALL.filter { it.group == group }
                val layout = startLayoutForMaxRows(entries, maxRows)
                assertEquals(entries.size, layout.placements.size)
                assertTrue("группа " + group.title + " выше " + maxRows + " строк", layout.rows <= maxRows)
                val occupied = mutableSetOf<Pair<Int, Int>>()
                layout.placements.forEach { placement ->
                    assertTrue(placement.x >= 0 && placement.x + placement.w <= layout.columns)
                    assertTrue(placement.y >= 0 && placement.y + placement.h <= layout.rows)
                    for (r in placement.y until placement.y + placement.h) {
                        for (c in placement.x until placement.x + placement.w) {
                            assertTrue("альбомные плитки наложились", occupied.add(r to c))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun appStartsOnlyInLandscapeWithoutTap() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("fullSensor"))
        assertTrue(manifest.contains("orientation|screenSize"))
        assertFalse(manifest.contains("screenOrientation=" + '"' + "portrait" + '"'))
        val main = File("src/main/java/com/gsynk/app/ui/MainScreen.kt").readText()
        assertTrue(main.contains("LocalConfiguration.current"))
        assertTrue(main.contains("Configuration.ORIENTATION_LANDSCAPE"))
        assertTrue(main.contains("LaunchedEffect(isLandscape, panel, minimizedApp)"))
        assertTrue(main.contains("if (!isLandscape)"))
        assertTrue(main.contains("LandscapeRequiredScreen()"))
        assertTrue(main.contains("isLandscape && (creating || editing != null)"))
        val start = File("src/main/java/com/gsynk/app/ui/StartScreen.kt").readText()
        assertTrue(start.contains("телефон нужно повернуть горизонтально"))
        assertTrue(start.contains("касаться экрана не нужно"))
        assertFalse(start.contains("портретный режим"))
    }

    @Test
    fun catalogHasSeparateNewsSettingsLifeAndNoLabHub() {
        assertTrue(Catalog.ALL.size >= 106)
        assertEquals(Catalog.ALL.size, Catalog.ALL.map { it.id }.distinct().size)
        assertNull("плитка старого хаба вернулась", Catalog.byId("lab"))
        assertEquals("page:news", Catalog.byId("news")?.route)
        assertEquals("panel:settings", Catalog.byId("settings")?.route)
        assertEquals("panel:lifeauto", Catalog.byId("lifeauto")?.route)
        assertEquals("tool:quick", Catalog.byId("quick")?.route)
        listOf("steps", "multitouch", "spectrum", "wifisig", "usb", "dimmer").forEach { id ->
            assertNotNull("нет прямой плитки " + id, Catalog.byId(id))
            assertEquals("lab:" + id, Catalog.byId(id)?.route)
        }
        assertEquals("lab:gpu", Catalog.byId("gpulab")?.route)
        assertFalse(Catalog.ALL.any { it.route == "panel:tools" })
    }

    @Test
    fun lifeAutomationsCoverSchoolHomeAndAreDeterministic() {
        assertEquals(12, LifeAutomations.ALL.size)
        assertEquals(LifeAutomations.ALL.size, LifeAutomations.ALL.map { it.id }.distinct().size)
        LifeAutomations.ALL.forEach { pack ->
            assertTrue(pack.specs.size >= 4)
            val first = LifeAutomations.rules(pack)
            val second = LifeAutomations.rules(pack)
            assertEquals(first, second)
            assertEquals(first.size, first.map { it.id }.distinct().size)
            first.forEach { rule ->
                assertTrue(rule.id.startsWith("life-" + pack.id + "-"))
                assertTrue(rule.brightness in 0..100)
                assertTrue(rule.volume in 0..100)
                assertTrue(rule.days.all { it in 1..7 })
                assertTrue(rule.silentMode in -1..1)
                assertTrue(rule.enabled)
            }
            assertFalse(LifeAutomations.isInstalled(emptyList(), pack))
            assertTrue(LifeAutomations.isInstalled(first, pack))
            assertTrue(LifeAutomations.isEnabled(first, pack))
            assertFalse(LifeAutomations.isEnabled(first.map { it.copy(enabled = false) }, pack))
        }

        val schoolHome = LifeAutomations.byId("school-home")!!
        val rules = LifeAutomations.rules(schoolHome)
        val school = rules.first { it.hour == 7 && it.minute == 0 }
        assertEquals(28, school.brightness)
        assertEquals(0, school.volume)
        assertEquals(1, school.silentMode)
        assertEquals(listOf(1, 2, 3, 4, 5), school.days)
        val youtube = rules.first { it.hour == 15 && it.minute == 0 }
        assertEquals(85, youtube.brightness)
        assertEquals(80, youtube.volume)
        assertEquals(0, youtube.silentMode)

        val wednesdayMorning = ZonedDateTime.parse("2026-10-28T07:30:00+03:00[Europe/Moscow]")
        assertEquals(school.id, LifeAutomations.currentRule(rules, schoolHome, wednesdayMorning)?.id)
        val wednesdayAfternoon = ZonedDateTime.parse("2026-10-28T15:30:00+03:00[Europe/Moscow]")
        assertEquals(youtube.id, LifeAutomations.currentRule(rules, schoolHome, wednesdayAfternoon)?.id)
    }

    @Test
    fun oldRuleJsonWithoutSilentModeStillLoads() {
        val oldJson = """{"id":"old","name":"старое","hour":7,"minute":0,"brightness":30,"volume":0}"""
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val decoded = json.decodeFromString(Rule.serializer(), oldJson)
        assertEquals(-1, decoded.silentMode)
        assertEquals(30, decoded.brightness)
        assertEquals(0, decoded.volume)
        val encoded = json.encodeToString(Rule.serializer(), decoded.copy(silentMode = 1))
        assertTrue(encoded.contains("\"silentMode\":1"))
    }

    @Test
    fun metroShellSourcesHaveNoLegacyContainers() {
        val ui = File("src/main/java/com/gsynk/app/ui")
        assertFalse(File(ui, "ToolsScreen.kt").exists())
        assertFalse(File(ui, "LabScreen.kt").exists())
        assertFalse(File(ui, "SettingsSheet.kt").exists())
        val main = File(ui, "MainScreen.kt").readText()
        assertTrue(main.contains("EmptyHomeScreen("))
        assertTrue(main.contains("StartMenuScreen("))
        assertTrue(main.contains("MetroAppShell("))
        assertFalse(main.contains("HomeHeader("))
        assertFalse(main.contains("ToolsScreen("))
        assertFalse(main.contains("SettingsSheet("))
        val start = File(ui, "StartScreen.kt").readText()
        assertTrue(start.contains("Color(0xFF170326)") && start.contains("Color(0xFF2A0B45)") && start.contains("drawCircle"))
        assertTrue(start.contains("LazyRow"))
        assertTrue(start.contains("combinedClickable"))
        val settings = File(ui, "SettingsScreen.kt").readText()
        assertFalse(settings.contains("ModalBottomSheet"))
        assertFalse(settings.contains("Мультитул"))
        assertEquals("5.0", ReleaseNews.VERSION)
    }
}
