package com.gsynk.app

import com.gsynk.app.core.LifeAutomations
import com.gsynk.app.core.ReleaseNews
import com.gsynk.app.core.Rule
import com.gsynk.app.core.Skin
import com.gsynk.app.ui.Catalog
import com.gsynk.app.ui.prgEdgeGlints
import com.gsynk.app.ui.startCellHeightDp
import com.gsynk.app.ui.startCellWidthDp
import com.gsynk.app.ui.startColumnsForWidth
import com.gsynk.app.ui.startPageChunks
import com.gsynk.app.ui.startPlacements
import com.gsynk.app.ui.startDesiredCellDp
import com.gsynk.app.ui.startRowsForHeight
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
    fun startHasNoSearchAndBladesAreCenteredInsideScreen() {
        val blade = File("src/main/java/com/gsynk/app/ui/BladeStartScreen.kt").readText()
        val start = File("src/main/java/com/gsynk/app/ui/StartScreen.kt").readText()
        val main = File("src/main/java/com/gsynk/app/ui/MainScreen.kt").readText()
        assertFalse("поиск в «Пуске» должен быть удалён", blade.contains("поиск в пуске") || start.contains("поиск в пуске"))
        assertFalse("текстовое поле поиска должно быть удалено", blade.contains("GrapeTextField("))
        assertFalse("callback поиска должен быть удалён", blade.contains("onQueryChange"))
        assertFalse("состояние запроса должно быть удалено", main.contains("var query"))
        assertTrue("EXP: «Пуск» — Blades", blade.contains("BladeTabRail(") && blade.contains("BladeItemColumn("))
        assertFalse("горизонтальных страниц больше нет", blade.contains("LazyRow"))
        assertFalse("snap-страниц больше нет", blade.contains("rememberSnapFlingBehavior"))
        assertTrue("категории Blades подписаны", blade.contains("category.title.lowercase()"))
        assertTrue("лезвия и рамка фокуса", blade.contains("BladeSurface(") && blade.contains("BladeFocusFrame("))
        assertTrue("Blade-фон и белые иконки", blade.contains("BladeBackground(") && blade.contains("BladeIcon("))
        assertFalse("GREAL/XMB в «Пуске» быть не должно", blade.contains("AnimatedGrapeBackground") || blade.contains("GrealAuroraField") || blade.contains("Xmb"))
        assertTrue("группы должны центрироваться", blade.contains("horizontalAlignment = Alignment.CenterHorizontally"))
        assertTrue("сетка должна центрироваться на странице", blade.contains("contentAlignment = Alignment.Center"))
        assertTrue("Blade-шапка «Пуска» — 46 dp, физическая шапка окна с описанием — 52 dp", blade.contains(".height(46.dp)") && start.contains(".height(52.dp)"))
        assertEquals(3, startColumnsForWidth(360))
        assertEquals(7, startColumnsForWidth(840))
        assertEquals(114, startCellWidthDp(840, 7))
        assertEquals(64, startDesiredCellDp(1))
        assertEquals(72, startDesiredCellDp(2))
        assertEquals(84, startDesiredCellDp(3))
        assertEquals(2, startRowsForHeight(230))
        assertEquals(3, startRowsForHeight(350))
        assertEquals(112, startCellHeightDp(230, 2, 114))
    }

    @Test
    fun startPagesScrollRightAndEveryPageFitsLandscapeScreen() {
        Catalog.Group.entries.forEach { group ->
            val entries = Catalog.ALL.filter { it.group == group }
            val pages = startPageChunks(entries, columns = 7, maxRows = 2)
            assertEquals("страницы не должны терять или дублировать плитки", entries, pages.flatten())
            pages.forEach { page ->
                val placements = startPlacements(page, 7)
                assertTrue("страница ${group.title} вышла за две строки", placements.all { it.y + it.h <= 2 })
                assertTrue("страница ${group.title} вышла за ширину", placements.all { it.x >= 0 && it.x + it.w <= 7 })
            }
        }
        listOf(180, 230, 280, 360).forEach { height ->
            val rows = startRowsForHeight(height)
            val cellHeight = startCellHeightDp(height, rows, cellWidthDp = 114)
            assertTrue("сетка выше экрана: $height", cellHeight * rows + 6 * (rows - 1) <= height)
        }
    }

    @Test
    fun bladeIsTheSharedUi() {
        val components = File("src/main/java/com/gsynk/app/ui/Components.kt").readText()
        val blade = File("src/main/java/com/gsynk/app/ui/BladeStartScreen.kt").readText()
        val settings = File("src/main/java/com/gsynk/app/ui/SettingsScreen.kt").readText()
        val bladeSettings = File("src/main/java/com/gsynk/app/ui/BladeSettingsScreen.kt").readText()
        val design = File("src/main/java/com/gsynk/app/ui/BladeDesign.kt").readText()
        assertTrue(components.contains("fun PrgGlassSurface("))
        assertTrue(components.contains("BladeBackground(") && components.contains("BladeFocusFrame("))
        assertTrue(components.contains("fun PrgGlassSheen("))
        assertTrue(blade.contains("BladeBackground(") && blade.contains("BladeSurface(") && blade.contains("BladeIcon("))
        assertTrue(settings.contains("Blade — дизайн"))
        assertTrue(settings.contains("Router.page(\"greal\")"))
        assertTrue(bladeSettings.contains("Настройки Blade UI") && bladeSettings.contains("Blade.VERSION"))
        assertTrue(bladeSettings.contains("BladeSurface(") && bladeSettings.contains("BladeIcon("))
        assertTrue(design.contains("fun BladeBackground(") && design.contains("fun BladeFocusFrame("))
        assertFalse(design.contains("Color.Gray") || design.contains("drawLine(") || design.contains("GrealTapeBackdrop"))
    }

    @Test
    fun appStartsOnlyInPortraitWithoutTap() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("screenOrientation=" + '"' + "portrait" + '"'))
        assertFalse(manifest.contains("fullSensor"))
        val main = File("src/main/java/com/gsynk/app/ui/MainScreen.kt").readText()
        assertTrue(main.contains("LocalConfiguration.current"))
        assertTrue(main.contains("val isPortrait = configuration.orientation != Configuration.ORIENTATION_LANDSCAPE"))
        assertTrue(main.contains("LaunchedEffect(isPortrait, panel, minimizedApps)"))
        assertTrue(main.contains("if (!isPortrait)"))
        assertTrue(main.contains("PortraitRequiredScreen()"))
        assertTrue(main.contains("isPortrait && (creating || editing != null)"))
        val start = File("src/main/java/com/gsynk/app/ui/StartScreen.kt").readText()
        assertTrue(start.contains("R.drawable.ic_phone_landscape"))
        assertTrue(start.contains("rotationZ = 90f"))
        assertFalse(start.contains("телефон нужно повернуть горизонтально"))
        assertFalse(start.contains("касаться экрана не нужно"))
    }

    @Test
    fun catalogHasSeparateNewsSettingsLifeAndNoLabHub() {
        assertTrue(Catalog.ALL.size >= 104)
        assertEquals(Catalog.ALL.size, Catalog.ALL.map { it.id }.distinct().size)
        assertNull("плитка старого хаба вернулась", Catalog.byId("lab"))
        assertEquals("page:greal", Catalog.byId("grealui")?.route)
        assertNull("отдельная плитка эффектов вернулась", Catalog.byId("effects"))
        assertNull("отдельная плитка форм вернулась", Catalog.byId("radius"))
        assertNull("отдельная плитка дизайна вернулась", Catalog.byId("tweaks"))
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
        assertTrue(main.contains("LocalLiquidWindowLaunch provides"))
        assertFalse(main.contains("SplashLogoScreen("))
        assertFalse(main.contains("splashVisible"))
        assertFalse(main.contains("HomeHeader("))
        assertFalse(main.contains("ToolsScreen("))
        assertFalse(main.contains("SettingsSheet("))
        val start = File(ui, "BladeStartScreen.kt").readText()
        val legacy = File(ui, "StartScreen.kt").readText()
        assertTrue(start.contains("BladeBackground(") && start.contains("BladeSurface(") && start.contains("BladeIcon("))
        assertFalse(start.contains("LazyRow"))
        assertTrue(start.contains("BladeTabRail(") && start.contains("BladeItemColumn("))
        assertTrue(start.contains("combinedClickable"))
        assertTrue(start.contains("LiquidWindowOrigin"))
        assertTrue(start.contains("onGloballyPositioned"))
        assertTrue(legacy.contains("liquidWindowRect"))
        assertTrue(start.contains("BladeFocusFrame(") && start.contains("BladeControlBar("))
        assertFalse(start.contains("SplashLogoScreen"))
        val settings = File(ui, "SettingsScreen.kt").readText()
        assertFalse(settings.contains("ModalBottomSheet"))
        assertFalse(settings.contains("Мультитул"))
        assertEquals("5.9.0", ReleaseNews.VERSION)
    }
}
