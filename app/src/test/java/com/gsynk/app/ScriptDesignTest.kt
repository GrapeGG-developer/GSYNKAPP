package com.gsynk.app

import com.gsynk.app.core.ReleaseNews
import com.gsynk.app.core.Script
import com.gsynk.app.ui.Catalog
import com.gsynk.app.ui.GiouiPageIds
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Постоянная дизайн-система GSYNK Script UI: математика, исходники и версия. */
class ScriptDesignTest {

    @Test
    fun scriptVersionAndTransformsAreBounded() {
        assertEquals("GSYNK Script UI — Monolith baseline 1.0", Script.VERSION)
        assertEquals(Script.VERSION, com.gsynk.app.ui.Gioui.UI_VERSION)
        assertTrue(Script.lineScale(0f) > Script.lineScale(1f))
        assertTrue(Script.lineAlpha(0f) > Script.lineAlpha(1f))
        listOf(-9f, -2.5f, -1f, 0f, 0.4f, 1f, 2f, 9f).forEach { distance ->
            assertTrue(Script.lineScale(distance) in 0.85f..1.05f)
            assertTrue(Script.lineAlpha(distance) in 0.35f..1f)
            assertTrue(Script.lineTranslationY(distance) in -110f..110f)
            assertTrue(Script.chapterAlpha(distance) in 0.4f..1f)
        }
        val radii = Script.isoRadii(42)
        assertEquals(4, radii.size)
        assertTrue(radii[0] != radii[1] && radii[2] != radii[3])
        assertTrue(radii.all { it in 8f..26f })
        assertEquals(Script.isoRadii(42), Script.isoRadii(42))
        assertTrue(Script.hash01(42, 0, 11) in 0f..1f)
    }

    @Test
    fun navigationIsAnEndlessRing() {
        assertEquals(0, Script.wrap(11, 11))
        assertEquals(10, Script.wrap(-1, 11))
        assertEquals(0, Script.wrap(0, 0))
        assertEquals(3, Script.move(2, 1, 11))
        assertEquals(0, Script.move(10, 1, 11))
        assertEquals(-1f, Script.cyclicDistance(4, 0f, 5), 0.0001f)
        assertEquals(1f, Script.cyclicDistance(1, 0f, 5), 0.0001f)
        assertEquals(0f, Script.cyclicDistance(0, 5f, 5), 0.0001f)
        assertEquals(2.9f, Script.normalizePhase(-0.1f, 3), 0.0001f)
        val shortest = Script.nearestPhase(9.8f, 0, 5)
        assertTrue(kotlin.math.abs(shortest - 9.8f) <= 2.5f)
        assertEquals(0, Script.wrap(kotlin.math.round(shortest).toInt(), 5))
        assertEquals(0, Script.flingSteps(0f))
        assertEquals(1, Script.flingSteps(2f))
        assertEquals(2, Script.flingSteps(9f))
        assertEquals(-1, Script.flingSteps(-2f))
        assertEquals(-2, Script.flingSteps(-9f))
        assertEquals(0, Script.flingSteps(9f, 0))
        assertEquals(5f, Script.settleTarget(4.6f, 0f), 0.0001f)
        assertEquals(7f, Script.settleTarget(4.6f, 9f), 0.0001f)
        assertEquals(2f, Script.settleTarget(4.4f, -9f), 0.0001f)
        listOf(-3f, 0f, 0.25f, 0.9f, 4f).forEach { phase ->
            assertTrue(Script.focusPulse(phase) in 0f..1f)
        }
    }

    @Test
    fun startMenuIsScriptMonolithAndKeepsAllRoutes() {
        val start = File("src/main/java/com/gsynk/app/ui/StartScreen.kt").readText()
        val script = File("src/main/java/com/gsynk/app/ui/ScriptStartScreen.kt").readText()
        assertTrue("старый «Пуск» не делегирует Script", start.contains("ScriptStartScreen("))
        assertTrue(script.contains("ScriptMonolithHeader("))
        assertTrue(script.contains("ScriptChapterHeader("))
        assertTrue(script.contains("ScriptActiveLine("))
        assertTrue(script.contains("ScriptLineColumn("))
        assertTrue(script.contains("ScriptControlBar("))
        assertTrue(script.contains("ScriptBackground(") && script.contains("ScriptSurface("))
        assertTrue(script.contains("ScriptGlyph(") && script.contains("ScriptIcon("))
        assertTrue(script.contains("ScriptFocusBar(") && script.contains("ScriptControlButton("))
        assertTrue(script.contains("ScriptEdgeMark(") && script.contains("ScriptCorners("))
        assertTrue(script.contains("ScriptTexture(") && script.contains("ScriptShards("))
        assertTrue(script.contains("ScriptDepthBackdrop("))
        assertTrue(script.contains("Script.cyclicDistance(") && script.contains("Script.settleTarget"))
        assertTrue(script.contains("Script.lineScale(") && script.contains("Script.lineAlpha("))
        assertTrue(script.contains("awaitEachGesture") && script.contains("awaitFirstDown"))
        assertTrue(script.contains("Animatable(") && script.contains("Catalog.Group.entries"))
        assertTrue(script.contains("onOpen(entry, focusedOrigin)"))
        assertTrue(script.contains("onToggleFavorite(entry)"))
        assertFalse("поиск в «Пуске» вернулся", script.contains("GrapeTextField(") || script.contains("var query"))
        assertFalse("старые ленивые списки вернулись", script.contains("LazyRow") || script.contains("LazyColumn"))
        assertFalse("старая оболочка вернулась", script.contains("AnimatedGrapeBackground") || script.contains("GrealAuroraField") || script.contains("Blade") || script.contains("Xmb"))
        assertFalse("стекло/3D в Script быть не должно", script.contains("Color.Gray") || script.contains("drawLine(") || script.contains("drawCircle(") || script.contains("GrealTapeBackdrop") || script.contains("blur(") || script.contains("cameraDistance") || script.contains("rotationX") || script.contains("rotationY") || script.contains("shadowElevation") || script.contains("ShaderBrush") || script.contains("RuntimeShader"))
    }

    @Test
    fun scriptDesignIsFlat2D() {
        val design = File("src/main/java/com/gsynk/app/ui/ScriptDesign.kt").readText()
        assertTrue(design.contains("ScriptPalette") && design.contains("ScriptShapes"))
        assertTrue(design.contains("Brush.linearGradient") && design.contains("RoundedCornerShape"))
        listOf(
            "fun ScriptBackground(",
            "fun ScriptSurface(",
            "fun ScriptFocusBar(",
            "fun ScriptControlButton(",
            "fun ScriptIcon(",
            "fun ScriptGlyph(",
            "fun ScriptTexture(",
            "fun ScriptEdgeMark(",
            "fun ScriptCorners(",
            "fun ScriptInfiniteOrbit(",
            "fun ScriptDepthBackdrop(",
            "fun ScriptShards("
        ).forEach { marker -> assertTrue("нет $marker", design.contains(marker)) }
        assertFalse("серые линии/круги/стекло/3D", design.contains("Color.Gray") || design.contains("drawLine(") || design.contains("drawCircle(") || design.contains("GrealTapeBackdrop") || design.contains("blur(") || design.contains("cameraDistance") || design.contains("rotationX") || design.contains("rotationY") || design.contains("shadowElevation") || design.contains("ShaderBrush") || design.contains("RuntimeShader"))
    }

    @Test
    fun scriptSettingsAndVersionArePublished() {
        val settings = File("src/main/java/com/gsynk/app/ui/ScriptSettingsScreen.kt").readText()
        val general = File("src/main/java/com/gsynk/app/ui/SettingsScreen.kt").readText()
        val catalog = File("src/main/java/com/gsynk/app/ui/Catalog.kt").readText()
        val router = File("src/main/java/com/gsynk/app/ui/GiouiScreens.kt").readText()
        assertTrue(settings.contains("Настройки Script UI") && settings.contains("Script.VERSION"))
        assertTrue(settings.contains("ScriptSurface(") && settings.contains("ScriptIcon("))
        assertTrue(settings.contains("ScriptBackground(") && settings.contains("ScriptFocusBar("))
        assertTrue(settings.contains("ScriptEdgeMark(") && settings.contains("ScriptCorners("))
        assertFalse("настройки не должны содержать стекло/3D", settings.contains("Color.Gray") || settings.contains("drawLine(") || settings.contains("drawCircle(") || settings.contains("blur(") || settings.contains("cameraDistance") || settings.contains("rotationX") || settings.contains("rotationY") || settings.contains("shadowElevation"))
        assertTrue(general.contains("Script — дизайн"))
        assertTrue(general.contains("Router.page(\"script\")"))
        assertTrue(catalog.contains("новости 6.0.0 EXP"))
        assertTrue(catalog.contains("настройки Script UI"))
        assertTrue(catalog.contains("антистресс Script"))
        assertEquals("page:script", Catalog.byId("grealui")?.route)
        assertTrue(GiouiPageIds.contains("script"))
        assertTrue(router.contains("\"script\" -> {") && router.contains("ScriptSettingsScreen("))
        assertEquals("6.0.0", ReleaseNews.VERSION)
        val build = File("build.gradle.kts").readText()
        assertTrue(build.contains("versionCode = 38"))
        assertTrue(build.contains("versionName = \"6.0.0\""))
    }

    @Test
    fun commonComponentsUseScript() {
        val components = File("src/main/java/com/gsynk/app/ui/Components.kt").readText()
        val start = File("src/main/java/com/gsynk/app/ui/StartScreen.kt").readText()
        val gioui = File("src/main/java/com/gsynk/app/ui/Gioui.kt").readText()
        val main = File("src/main/java/com/gsynk/app/ui/MainScreen.kt").readText()
        val settings = File("src/main/java/com/gsynk/app/ui/SettingsScreen.kt").readText()
        val home = File("src/main/java/com/gsynk/app/ui/HomeTiles.kt").readText()
        assertTrue(components.contains("ScriptBackground(") && components.contains("ScriptFocusBar("))
        assertTrue(components.contains("ScriptControlButton(") && components.contains("ScriptPalette"))
        assertTrue(components.contains("UiTweaks.breathMs") && components.contains("fun PrgGlassSheen("))
        assertFalse(components.contains("BladeBackground(") || components.contains("BladeFocusFrame(") || components.contains("BladeControlOrb(") || components.contains("BladeIcon(") || components.contains("BladeSurface(") || components.contains("GrealAuroraField(") || components.contains("GrealTexture("))
        val flatSources = listOf(components, start, gioui, main, settings, home)
        flatSources.forEach { source ->
            assertFalse("осталась 3D-перспектива", source.contains("cameraDistance") || source.contains("rotationX") || source.contains("rotationY") || source.contains("blur(") || source.contains("shadowElevation"))
        }
        assertFalse(start.contains("BLADE") || start.contains("GREAL") || start.contains("Blade") || start.contains("Xmb"))
    }
}
