package com.gsynk.app

import com.gsynk.app.core.Blade
import com.gsynk.app.core.ReleaseNews
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** GSYNK Blades: геометрия лезвий, инерция, исходники оболочки и версия. */
class BladeDesignTest {

    @Test
    fun bladeFocusAndDepthAreBounded() {
        assertEquals("GSYNK Blades — Xbox 360 Blade Dashboard", Blade.VERSION)
        assertTrue(Blade.tabScale(0f) > Blade.tabScale(1f))
        assertTrue(Blade.tabAlpha(0f) > Blade.tabAlpha(1f))
        assertTrue(Blade.itemScale(0f) > Blade.itemScale(1f))
        assertTrue(Blade.itemAlpha(0f) > Blade.itemAlpha(1f))
        listOf(-9f, -2.5f, -1f, 0f, 0.4f, 1f, 2f, 9f).forEach { distance ->
            assertTrue(Blade.tabScale(distance) in 0.7f..1.2f)
            assertTrue(Blade.tabAlpha(distance) in 0.25f..1f)
            assertTrue(Blade.tabSkew(distance) in -18f..18f)
            assertTrue(Blade.itemScale(distance) in 0.75f..1.1f)
            assertTrue(Blade.itemAlpha(distance) in 0.3f..1f)
            assertTrue(Blade.itemTranslationX(distance) in -150f..28f)
            assertTrue(Blade.itemSkew(distance) in -9f..9f)
        }
    }

    @Test
    fun navigationIsAnEndlessRing() {
        assertEquals(0, Blade.wrap(11, 11))
        assertEquals(10, Blade.wrap(-1, 11))
        assertEquals(0, Blade.wrap(0, 0))
        assertEquals(3, Blade.move(2, 1, 11))
        assertEquals(0, Blade.move(10, 1, 11))
        assertEquals(-1f, Blade.cyclicDistance(4, 0f, 5), 0.0001f)
        assertEquals(1f, Blade.cyclicDistance(1, 0f, 5), 0.0001f)
        assertEquals(0f, Blade.cyclicDistance(0, 5f, 5), 0.0001f)
        assertEquals(2.9f, Blade.normalizePhase(-0.1f, 3), 0.0001f)
        val shortest = Blade.nearestPhase(9.8f, 0, 5)
        assertTrue(kotlin.math.abs(shortest - 9.8f) <= 2.5f)
        assertEquals(0, Blade.wrap(kotlin.math.round(shortest).toInt(), 5))
    }

    @Test
    fun flingAndSettleAreBounded() {
        assertEquals(0, Blade.flingSteps(0f))
        assertEquals(1, Blade.flingSteps(2f))
        assertEquals(2, Blade.flingSteps(9f))
        assertEquals(-1, Blade.flingSteps(-2f))
        assertEquals(-2, Blade.flingSteps(-9f))
        assertEquals(0, Blade.flingSteps(9f, 0))
        assertEquals(5f, Blade.settleTarget(4.6f, 0f), 0.0001f)
        assertEquals(7f, Blade.settleTarget(4.6f, 9f), 0.0001f)
        assertEquals(2f, Blade.settleTarget(4.4f, -9f), 0.0001f)
    }

    @Test
    fun sweepAndPulseAreBounded() {
        listOf(-3f, 0f, 0.25f, 0.9f, 4f).forEach { phase ->
            listOf(-2f, 0f, 0.5f, 1f, 3f).forEach { x ->
                listOf(0, 2, 5).forEach { layer ->
                    assertTrue(Blade.sweepHeight(phase, x, layer) in -1f..1f)
                    assertTrue(Blade.focusPulse(phase) in 0f..1f)
                }
            }
        }
        assertEquals(Blade.hash01(581, 3, 11), Blade.hash01(581, 3, 11), 0.0001f)
        assertTrue(Blade.hash01(581, 3, 11) != Blade.hash01(581, 4, 11))
        assertTrue(Blade.hash01(581, 3, 11) in 0f..1f)
    }

    @Test
    fun startMenuIsBladesAndKeepsAllRoutes() {
        val start = File("src/main/java/com/gsynk/app/ui/StartScreen.kt").readText()
        val blade = File("src/main/java/com/gsynk/app/ui/BladeStartScreen.kt").readText()
        assertTrue("старый «Пуск» не делегирует Blades", start.contains("BladeStartScreen("))
        assertTrue(blade.contains("BladeTabRail(") && blade.contains("BladeItemColumn("))
        assertTrue(blade.contains("BladeControlBar(") && blade.contains("AnimatedContent("))
        assertTrue(blade.contains("awaitEachGesture") && blade.contains("awaitFirstDown"))
        assertTrue(blade.contains("Animatable(") && blade.contains("Blade.cyclicDistance("))
        assertTrue(blade.contains("Blade.settleTarget") || blade.contains("settleTarget"))
        assertFalse("старые детекторы XMB вернулись", blade.contains("detectHorizontalDragGestures") || blade.contains("detectVerticalDragGestures") || blade.contains("VelocityTracker"))
        assertTrue(blade.contains("Catalog.Group.entries"))
        assertTrue(blade.contains("onOpen(entry, focusedOrigin)"))
        assertTrue(blade.contains("onToggleFavorite(entry)"))
        assertFalse("поиск в «Пуске» вернулся", blade.contains("GrapeTextField(") || blade.contains("var query"))
        assertFalse("старые горизонтальные страницы вернулись", blade.contains("LazyRow"))
        assertFalse("старая вертикальная лента вернулась", blade.contains("LazyColumn"))
        assertFalse("GREAL/XMB в «Пуске» быть не должно", blade.contains("AnimatedGrapeBackground") || blade.contains("GrealAuroraField") || blade.contains("Xmb"))
    }

    @Test
    fun bladeKeepsNoCassetteGraphics() {
        val blade = File("src/main/java/com/gsynk/app/ui/BladeStartScreen.kt").readText()
        val design = File("src/main/java/com/gsynk/app/ui/BladeDesign.kt").readText()
        assertTrue(blade.contains("BladeBackground(") && blade.contains("BladeSurface("))
        assertTrue(blade.contains("BladeIcon(") && blade.contains("BladeFocusFrame("))
        assertTrue(design.contains("fun BladeBackground(") && design.contains("fun BladeFocusFrame("))
        assertFalse(blade.contains("Color.Gray") || blade.contains("GrealTapeBackdrop"))
        assertFalse(design.contains("Color.Gray"))
        assertFalse(design.contains("drawLine("))
        assertFalse(design.contains("drawCircle("))
        assertFalse(design.contains("GrealTapeBackdrop"))
    }

    @Test
    fun bladeSettingsAndVersionArePublished() {
        val settings = File("src/main/java/com/gsynk/app/ui/BladeSettingsScreen.kt").readText()
        val general = File("src/main/java/com/gsynk/app/ui/SettingsScreen.kt").readText()
        val catalog = File("src/main/java/com/gsynk/app/ui/Catalog.kt").readText()
        assertTrue(settings.contains("Настройки Blade UI"))
        assertTrue(settings.contains("Blade.VERSION"))
        assertTrue(settings.contains("BladeSurface(") && settings.contains("BladeIcon("))
        assertTrue(general.contains("Blade — дизайн"))
        assertTrue(catalog.contains("новости 5.9.0 EXP"))
        assertTrue(catalog.contains("настройки Blade UI"))
        assertEquals("5.9.0", ReleaseNews.VERSION)
        val build = File("build.gradle.kts").readText()
        assertTrue(build.contains("versionCode = 37"))
        assertTrue(build.contains("versionName = \"5.9.0\""))
    }
}
