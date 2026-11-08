package com.gsynk.app

import com.gsynk.app.core.ReleaseNews
import com.gsynk.app.core.Xmb
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** XMB Motion: фокус, циклическая фаза, инерция и исходники оболочки. */
class XmbDesignTest {

    @Test
    fun xmbFocusAndDepthAreBounded() {
        assertEquals("GREAL XMB Motion — инерционный XrossMediaBar на физическом GREAL", Xmb.VERSION)
        assertTrue(Xmb.categoryScale(0f) > Xmb.categoryScale(1f))
        assertTrue(Xmb.categoryScale(1f) > Xmb.categoryScale(2f))
        assertTrue(Xmb.categoryAlpha(0f) > Xmb.categoryAlpha(1f))
        assertTrue(Xmb.itemScale(0f) > Xmb.itemScale(1f))
        assertTrue(Xmb.itemAlpha(0f) > Xmb.itemAlpha(1f))
        listOf(-9f, -2.5f, -1f, 0f, 0.4f, 1f, 2f, 9f).forEach { distance ->
            assertTrue(Xmb.categoryScale(distance) in 0.6f..1.2f)
            assertTrue(Xmb.categoryAlpha(distance) in 0.2f..1f)
            assertTrue(Xmb.categoryRotationY(distance) in -24f..24f)
            assertTrue(Xmb.itemScale(distance) in 0.7f..1.1f)
            assertTrue(Xmb.itemAlpha(distance) in 0.3f..1f)
            assertTrue(Xmb.itemTranslationX(distance) in -160f..30f)
            assertTrue(Xmb.itemRotationX(distance) in -10f..10f)
        }
    }

    @Test
    fun navigationIsAnEndlessRing() {
        assertEquals(0, Xmb.wrap(11, 11))
        assertEquals(10, Xmb.wrap(-1, 11))
        assertEquals(0, Xmb.wrap(0, 0))
        assertEquals(3, Xmb.move(2, 1, 11))
        assertEquals(10, Xmb.move(0, -1, 11))
        assertEquals(0, Xmb.move(10, 1, 11))
        var index = 0
        repeat(37) { index = Xmb.move(index, 1, 11) }
        assertEquals(4, index)
    }

    @Test
    fun cyclicDistanceAndShortestPathAreStable() {
        assertEquals(0f, Xmb.cyclicDistance(0, 0f, 5), 0.0001f)
        assertEquals(1f, Xmb.cyclicDistance(1, 0f, 5), 0.0001f)
        assertEquals(-1f, Xmb.cyclicDistance(4, 0f, 5), 0.0001f)
        assertEquals(2f, Xmb.cyclicDistance(2, 0f, 5), 0.0001f)
        assertEquals(-2f, Xmb.cyclicDistance(3, 0f, 5), 0.0001f)
        assertEquals(0f, Xmb.cyclicDistance(0, 5f, 5), 0.0001f)
        assertEquals(2.9f, Xmb.normalizePhase(-0.1f, 3), 0.0001f)
        val shortest = Xmb.nearestPhase(9.8f, 0, 5)
        assertTrue("переход через край должен быть коротким", kotlin.math.abs(shortest - 9.8f) <= 2.5f)
        assertEquals(0, Xmb.wrap(kotlin.math.round(shortest).toInt(), 5))
    }

    @Test
    fun flingAndSettleAreBounded() {
        assertEquals(0, Xmb.flingSteps(0f))
        assertEquals(1, Xmb.flingSteps(2f))
        assertEquals(2, Xmb.flingSteps(9f))
        assertEquals(-1, Xmb.flingSteps(-2f))
        assertEquals(-2, Xmb.flingSteps(-9f))
        assertEquals(0, Xmb.flingSteps(9f, 0))
        assertEquals(5f, Xmb.settleTarget(4.6f, 0f), 0.0001f)
        assertEquals(7f, Xmb.settleTarget(4.6f, 9f), 0.0001f)
        assertEquals(2f, Xmb.settleTarget(4.4f, -9f), 0.0001f)
    }

    @Test
    fun visibleWindowAlwaysContainsFocusAndFitsScreen() {
        assertEquals(IntRange.EMPTY, Xmb.visibleRange(0, 0))
        listOf(1, 3, 7, 12, 105).forEach { size ->
            listOf(0, size / 2, size - 1).forEach { selected ->
                val range = Xmb.visibleRange(selected, size)
                assertTrue("диапазон вышел за список", range.first >= 0 && range.last < size)
                assertTrue("фокус потерялся", range.contains(selected))
                assertTrue("слишком много строк", range.count() <= Xmb.VISIBLE_ITEMS)
                assertTrue("диапазон пуст", !range.isEmpty())
            }
        }
    }

    @Test
    fun waveIsBoundedAndAdaptive() {
        val low = Xmb.waveLayers(0, false)
        val medium = Xmb.waveLayers(1, false)
        val high = Xmb.waveLayers(2, true)
        assertTrue(low >= 2 && low <= medium && medium <= high && high <= Xmb.WAVE_LAYERS)
        listOf(-3f, 0f, 0.25f, 0.9f, 4f).forEach { phase ->
            listOf(-2f, 0f, 0.5f, 1f, 3f).forEach { x ->
                listOf(0, 2, 5).forEach { layer ->
                    val height = Xmb.waveHeight(phase, x, layer)
                    assertTrue("волна вышла за границы: $height", height >= -1f && height <= 1f)
                }
            }
        }
        assertEquals(Xmb.hash01(581, 3, 11), Xmb.hash01(581, 3, 11), 0.0001f)
        assertTrue(Xmb.hash01(581, 3, 11) != Xmb.hash01(581, 4, 11))
        assertTrue(Xmb.hash01(581, 3, 11) in 0f..1f)
    }

    @Test
    fun startMenuIsNowMotionXmbAndKeepsAllRoutes() {
        val start = File("src/main/java/com/gsynk/app/ui/StartScreen.kt").readText()
        val xmb = File("src/main/java/com/gsynk/app/ui/XmbStartScreen.kt").readText()
        assertTrue("старый «Пуск» не делегирует XMB", start.contains("XmbStartScreen("))
        assertTrue(xmb.contains("XmbCategoryRail(") && xmb.contains("XmbItemColumn("))
        assertTrue(xmb.contains("XmbWaveField(") && xmb.contains("XmbFocusBeam("))
        assertTrue(xmb.contains("XmbControlBar(") && xmb.contains("AnimatedContent("))
        assertTrue(xmb.contains("Animatable(") && xmb.contains("awaitEachGesture"))
        assertTrue(xmb.contains("awaitFirstDown") && xmb.contains("awaitPointerEvent"))
        assertTrue(xmb.contains("xmbMotionGestures(") && xmb.contains("Xmb.cyclicDistance("))
        assertTrue(xmb.contains("Xmb.flingSteps") || xmb.contains("settleTarget"))
        assertFalse("старые конфликтующие детекторы вернулись", xmb.contains("detectHorizontalDragGestures") || xmb.contains("detectVerticalDragGestures"))
        assertFalse("низкоуровневый трекер скорости не должен оставлять несвязанный код", xmb.contains("VelocityTracker"))
        assertTrue(xmb.contains("Catalog.Group.entries"))
        assertTrue(xmb.contains("onOpen(entry, focusedOrigin)"))
        assertTrue(xmb.contains("onToggleFavorite(entry)"))
        assertFalse("поиск в «Пуске» вернулся", xmb.contains("GrapeTextField(") || xmb.contains("var query"))
        assertFalse("старые горизонтальные страницы вернулись", xmb.contains("LazyRow"))
        assertFalse("старая вертикальная лента вернулась", xmb.contains("LazyColumn"))
    }

    @Test
    fun xmbKeepsGrealEffectsWithoutCassetteGraphics() {
        val xmb = File("src/main/java/com/gsynk/app/ui/XmbStartScreen.kt").readText()
        val design = File("src/main/java/com/gsynk/app/ui/XmbDesign.kt").readText()
        assertTrue(xmb.contains("AnimatedGrapeBackground"))
        assertTrue(xmb.contains("GrealTexture(") && xmb.contains("PrgGlassSheen("))
        assertTrue(xmb.contains("GrealOrbFace(") && xmb.contains("GrealInfiniteOrbit("))
        assertTrue(xmb.contains("GrealParallaxShards(") && xmb.contains("GrealActiveCorners("))
        assertTrue(design.contains("animateFloatAsState"))
        assertFalse(xmb.contains("Color.Gray"))
        assertFalse(xmb.contains("GrealTapeBackdrop"))
        assertFalse(design.contains("Color.Gray"))
        assertFalse(design.contains("drawLine("))
        assertFalse(design.contains("drawCircle("))
        assertFalse(design.contains("GrealTapeBackdrop"))
    }

    @Test
    fun xmbSettingsAndVersionArePublished() {
        val settings = File("src/main/java/com/gsynk/app/ui/GrealSettingsScreen.kt").readText()
        assertTrue(settings.contains("XMB Motion: категории, фокус и волна"))
        assertTrue(settings.contains("XmbWaveField("))
        assertTrue(settings.contains("Xmb.VERSION"))
        assertTrue(settings.contains("Инерция жестов XMB"))
        val catalog = File("src/main/java/com/gsynk/app/ui/Catalog.kt").readText()
        assertTrue(catalog.contains("новости 5.8.1 EXP"))
        assertTrue(catalog.contains("GREAL XMB"))
        assertEquals("5.8.1", ReleaseNews.VERSION)
        val build = File("build.gradle.kts").readText()
        assertTrue(build.contains("versionCode = 36"))
        assertTrue(build.contains("versionName = \"5.8.1\""))
    }
}
