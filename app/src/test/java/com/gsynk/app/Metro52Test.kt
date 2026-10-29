package com.gsynk.app

import com.gsynk.app.core.LifeAutomations
import com.gsynk.app.core.ReleaseNews
import com.gsynk.app.core.Rule
import com.gsynk.app.core.Skin
import com.gsynk.app.ui.Catalog
import com.gsynk.app.ui.startCellWidthDp
import com.gsynk.app.ui.startColumnsForWidth
import com.gsynk.app.ui.startPlacements
import com.gsynk.app.ui.startDesiredCellDp
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
    fun landscapeStartLayoutStaysInsideScreenWidthWithoutOverlap() {
        listOf(640, 760, 840, 960).forEach { width ->
            val columns = startColumnsForWidth(width)
            val cell = startCellWidthDp(width, columns)
            val occupiedWidth = cell * columns + 6 * (columns - 1)
            assertTrue("сетка шире экрана $width", occupiedWidth <= width)
            assertTrue("сетка не по центру: слишком большой остаток на $width", width - occupiedWidth <= columns)
            Catalog.Group.entries.forEach { group ->
                val entries = Catalog.ALL.filter { it.group == group }
                val placements = startPlacements(entries, columns)
                val occupied = mutableSetOf<Pair<Int, Int>>()
                placements.forEach { placement ->
                    assertTrue(placement.x >= 0 && placement.x + placement.w <= columns)
                    assertTrue(placement.y >= 0)
                    for (r in placement.y until placement.y + placement.h) {
                        for (c in placement.x until placement.x + placement.w) {
                            assertTrue("плитки наложились", occupied.add(r to c))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun startHasNoSearchAndTilesAreCenteredInsideScreen() {
        val start = File("src/main/java/com/gsynk/app/ui/StartScreen.kt").readText()
        val main = File("src/main/java/com/gsynk/app/ui/MainScreen.kt").readText()
        assertFalse("поиск в «Пуске» должен быть удалён", start.contains("поиск в пуске"))
        assertFalse("текстовое поле поиска должно быть удалено", start.contains("GrapeTextField("))
        assertFalse("callback поиска должен быть удалён", start.contains("onQueryChange"))
        assertFalse("состояние запроса должно быть удалено", main.contains("var query"))
        assertFalse("горизонтальная лента снова уводит плитки за экран", start.contains("LazyRow"))
        assertTrue("плитки должны идти центрированной вертикальной мозаикой", start.contains("LazyColumn"))
        assertTrue("группы должны центрироваться", start.contains("horizontalAlignment = Alignment.CenterHorizontally"))
        assertTrue("шапка «Пуска» и строка приложения должны быть 34 dp", start.split(".height(34.dp)").size >= 3)
        assertEquals(3, startColumnsForWidth(360))
        assertEquals(7, startColumnsForWidth(840))
        assertEquals(114, startCellWidthDp(840, 7))
        assertEquals(96, startDesiredCellDp(1))
        assertEquals(112, startDesiredCellDp(2))
        assertEquals(128, startDesiredCellDp(3))
    }

    @Test
    fun gsynkPrgIsTheSharedGlassUi() {
        val components = File("src/main/java/com/gsynk/app/ui/Components.kt").readText()
        val start = File("src/main/java/com/gsynk/app/ui/StartScreen.kt").readText()
        val settings = File("src/main/java/com/gsynk/app/ui/SettingsScreen.kt").readText()
        val effects = File("src/main/java/com/gsynk/app/ui/GiouiScreens.kt").readText()
        assertTrue(components.contains("fun PrgGlassSurface("))
        assertTrue(components.contains("fun PrgGlassSheen("))
        assertTrue(components.contains("PrgGlassSurface(\n        modifier = modifier.fillMaxWidth()"))
        assertTrue(start.contains("PrgGlassSheen"))
        assertTrue(start.contains("AnimatedGrapeBackground"))
        assertTrue(settings.contains("GSYNKPRG — дизайн"))
        assertTrue(settings.contains("prgOpacity") && settings.contains("prgGlow") && settings.contains("prgGlare") && settings.contains("prgMotion"))
        assertTrue(effects.contains("GSYNKPRG") && effects.contains("Плотность стекла") && effects.contains("Контурное свечение"))
        val values = Skin.Values()
        assertEquals("prg", values.glass)
        assertEquals(38, values.prgOpacity)
        assertTrue(values.prgGlow in 1..100 && values.prgGlare in 1..100 && values.prgMotion == 100)
        val clamped = Skin.clamp(values.copy(prgOpacity = 999, prgGlow = -5, prgGlare = 999, prgMotion = 999))
        assertEquals(90, clamped.prgOpacity)
        assertEquals(0, clamped.prgGlow)
        assertEquals(100, clamped.prgGlare)
        assertEquals(200, clamped.prgMotion)
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
        assertTrue(start.contains("R.drawable.ic_phone_landscape"))
        assertFalse(start.contains("телефон нужно повернуть горизонтально"))
        assertFalse(start.contains("касаться экрана не нужно"))
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
        assertTrue(start.contains("AnimatedGrapeBackground") && start.contains("PrgGlassSheen") && start.contains("GSYNKPRG"))
        assertTrue(start.contains("LazyColumn"))
        assertTrue(start.contains("combinedClickable"))
        val settings = File(ui, "SettingsScreen.kt").readText()
        assertFalse(settings.contains("ModalBottomSheet"))
        assertFalse(settings.contains("Мультитул"))
        assertEquals("5.4.0", ReleaseNews.VERSION)
    }
}
