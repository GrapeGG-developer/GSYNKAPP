package com.gsynk.app

import com.gsynk.app.core.ReleaseNews
import com.gsynk.app.ui.Catalog
import com.gsynk.app.ui.Gioui
import com.gsynk.app.ui.GiouiPageIds
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Возврат классического «Пуска» 5.6.2 в 6.1.0.
 *
 * Проверяем не только картинку по словам, но и то, что оболочки 5.7–6.0
 * действительно удалены, а все приложения и маршруты остались на месте.
 */
class ClassicUiRestoreTest {

    private val ui = File("src/main/java/com/gsynk/app/ui")
    private val core = File("src/main/java/com/gsynk/app/core")
    private fun read(name: String) = File(ui, name).readText()

    @Test
    fun classicDesignSystemAndVersionAreBack() {
        assertEquals("GSYNKPRG Premium Realtime Glass", Gioui.UI_VERSION)
        assertEquals("6.1.0", ReleaseNews.VERSION)
        val gioui = read("Gioui.kt")
        assertTrue(gioui.contains("const val UI_VERSION = \"GSYNKPRG Premium Realtime Glass\""))
        val news = File(core, "ReleaseNews.kt").readText()
        assertTrue(news.contains("6.1.0 EXP: возвращён классический «Пуск» с приложениями"))
        val build = File("build.gradle.kts").readText()
        assertTrue(build.contains("versionCode = 39"))
        assertTrue(build.contains("versionName = \"6.1.0\""))
    }

    @Test
    fun startMenuIsTheOldTimelineWithAppTiles() {
        val start = read("StartScreen.kt")
        listOf(
            "fun StartMenuScreen(",
            "AnimatedGrapeBackground",
            "PrgGlassSheen",
            "LiquidWindowOrigin",
            "scrollToItem(nowIndex)",
            "LazyColumn",
            "TimelineGroupSection(",
            "StartTile(",
            "GSYNKPRG"
        ).forEach { marker -> assertTrue("старый «Пуск» потерял $marker", start.contains(marker)) }
        listOf(
            "ScriptStartScreen(",
            "BladeStartScreen(",
            "XmbStartScreen(",
            "ScriptMonolithHeader",
            "GrapeTextField(",
            "LazyRow",
            "var query"
        ).forEach { marker -> assertFalse("в классический «Пуск» вернулся $marker", start.contains(marker)) }
        val main = read("MainScreen.kt")
        assertTrue(main.contains("StartMenuScreen("))
        assertTrue(main.contains("MetroAppShell("))
        assertFalse(main.contains("ScriptStartScreen("))
    }

    @Test
    fun glassSettingsAndComponentsAreRestored() {
        val settings = read("SettingsScreen.kt")
        listOf(
            "GSYNKPRG — дизайн",
            "prgOpacity",
            "prgGlow",
            "prgGlare",
            "prgMotion"
        ).forEach { marker -> assertTrue("настройки стекла потеряли $marker", settings.contains(marker)) }
        assertFalse(settings.contains("Router.page(\"script\")"))
        val components = read("Components.kt")
        assertTrue(components.contains("fun AnimatedGrapeBackground("))
        assertTrue(components.contains("fun PrgGlassSheen("))
        assertTrue(components.contains("fun PrgGlassSurface("))
        assertTrue(components.contains("fun rememberPrgPhaseState("))
        val effects = read("Effects.kt")
        assertTrue(effects.contains("PRG(\"GSYNKPRG\")"))
        val home = read("HomeTiles.kt")
        assertTrue(home.contains("PrgGlassSheen(Modifier.matchParentSize(), accent = accent"))
        val skin = File(core, "Skin.kt").readText()
        assertTrue(skin.contains("\"prg\" to \"GSYNKPRG\""))
        assertTrue(skin.contains("GSYNKPRG Premium"))
    }

    @Test
    fun experimentalShellSourcesAreGone() {
        listOf(
            "ScriptDesign.kt",
            "ScriptStartScreen.kt",
            "ScriptSettingsScreen.kt",
            "BladeDesign.kt",
            "BladeStartScreen.kt",
            "BladeSettingsScreen.kt",
            "XmbDesign.kt",
            "XmbStartScreen.kt"
        ).forEach { name -> assertFalse("оболочка $name осталась", File(ui, name).exists()) }
        assertFalse(File(core, "Script.kt").exists())
        assertTrue(File(ui, "GrealAntistressScreen.kt").exists())
        assertTrue(File(ui, "GrealDesign.kt").exists())
        assertTrue(File(ui, "GrealInfinite.kt").exists())
        assertTrue(File(core, "GrealPlay.kt").exists())
    }

    @Test
    fun allAppsAndLegacyRoutesStillOpen() {
        val catalog = read("Catalog.kt")
        assertTrue(catalog.contains("новости 6.1.0 EXP"))
        assertTrue(catalog.contains("GSYNKPRG"))
        assertFalse(catalog.contains("настройки Script UI"))
        assertFalse(catalog.contains("антистресс Script"))
        assertTrue(Catalog.ALL.size >= 106)
        assertEquals(Catalog.ALL.size, Catalog.ALL.map { it.id }.distinct().size)

        val antistress = Catalog.byId("antistress")
        assertEquals("page:antistress", antistress?.route)
        assertEquals(Catalog.Group.FUN, antistress?.group)
        assertTrue(antistress?.fresh == true)
        assertEquals("page:news", Catalog.byId("news")?.route)
        assertEquals("page:effects", Catalog.byId("effects")?.route)
        assertEquals("page:radius", Catalog.byId("radius")?.route)
        assertEquals("panel:settings", Catalog.byId("tweaks")?.route)

        listOf("antistress", "script", "greal", "blade", "xmb", "effects", "radius").forEach { id ->
            assertTrue("страница $id не зарегистрирована", GiouiPageIds.contains(id))
        }
        val router = read("GiouiScreens.kt")
        assertTrue(router.contains("\"antistress\" -> {"))
        assertTrue(router.contains("\"script\", \"greal\", \"blade\", \"xmb\" -> {"))
        assertTrue(router.contains("\"effects\" -> {"))
        assertTrue(router.contains("\"radius\" -> {"))
        val main = read("MainScreen.kt")
        assertTrue(main.contains("\"page:antistress\" -> return \"антистресс\""))
        assertTrue(main.contains("\"page:script\", \"page:greal\", \"page:blade\", \"page:xmb\" -> return \"настройки дизайна\""))
    }
}
