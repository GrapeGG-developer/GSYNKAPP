package com.gsynk.app

import com.gsynk.app.core.CatalogGuards
import com.gsynk.app.core.ReleaseNews
import com.gsynk.app.ui.Catalog
import com.gsynk.app.ui.Gioui
import com.gsynk.app.ui.GiouiPageIds
import com.gsynk.app.ui.Greal
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 6.2.0: вернули тот самый 3D-GREAL с приложениями-кружочками и вынесли расписание. */
class GrealRestoreTest {

    private val ui = File("src/main/java/com/gsynk/app/ui")
    private fun read(name: String) = File(ui, name).readText()

    @Test
    fun greal3dOrbsAreBackAndExperimentalShellsAreGone() {
        assertEquals("GREAL — Grape Realism Engine", Greal.VERSION)
        assertEquals(Greal.VERSION, Gioui.UI_VERSION)
        val design = read("GrealDesign.kt")
        listOf(
            "fun GrealAuroraField(", "fun GrealOrbFace(", "fun grealDepthTransform(",
            "fun GrealTexture(", "fun GrealActiveCorners(", "fun GrealDepthBackdrop("
        ).forEach { marker -> assertTrue("3D-слой потерял $marker", design.contains(marker)) }
        val infinite = read("GrealInfinite.kt")
        assertTrue(infinite.contains("fun GrealInfiniteOrbit("))
        assertTrue(infinite.contains("fun GrealInfiniteCarousel("))
        val start = read("StartScreen.kt")
        assertTrue(start.contains("GrealInfiniteOrbit(") || start.contains("GrealOrbFace("))
        assertTrue(start.contains("AnimatedGrapeBackground"))
        listOf("ScriptStartScreen", "Blade", "Xmb", "ScriptMonolithHeader").forEach {
            assertFalse("в «Пуск» вернулся $it", start.contains(it))
        }
        listOf(
            "ScriptDesign.kt", "ScriptStartScreen.kt", "ScriptSettingsScreen.kt",
            "BladeDesign.kt", "BladeStartScreen.kt", "XmbDesign.kt", "XmbStartScreen.kt"
        ).forEach { name -> assertFalse("оболочка $name осталась", File(ui, name).exists()) }
        assertTrue(File(ui, "GrealSettingsScreen.kt").exists())
    }

    @Test
    fun scheduleAndLifeAutomationsMovedToGauto() {
        val catalog = read("Catalog.kt")
        assertFalse(catalog.contains("Group.SCHEDULE"))
        assertFalse(Catalog.Group.entries.any { it.name == "SCHEDULE" })
        assertNull(Catalog.byId("schedule"))
        assertNull(Catalog.byId("schedulenew"))
        assertNull(Catalog.byId("schedulestatus"))
        assertNull(Catalog.byId("lifeauto"))
        val main = read("MainScreen.kt")
        listOf(
            "ScheduleScreen(", "RuleEditorSheet(", "LifeAutomationsScreen(",
            "panel:schedule", "panel:lifeauto", "RuleScheduler."
        ).forEach { marker -> assertFalse("остался $marker", main.contains(marker)) }
        val settings = read("SettingsScreen.kt")
        assertFalse(settings.contains("syncToSchedule"))
        val manifest = File("src/main/AndroidManifest.xml").readText()
        listOf(
            "BootReceiver", "ApplyService", "SCHEDULE_EXACT_ALARM"
        ).forEach { marker -> assertFalse("манифест ещё содержит $marker", manifest.contains(marker)) }
        // RECEIVE_BOOT_COMPLETED оставлен осознанно: он нужен OTA-джобу, а не расписанию.
        assertTrue(manifest.contains("RECEIVE_BOOT_COMPLETED"))
        val core = File("src/main/java/com/gsynk/app/core")
        assertFalse(File(core, "ApplyService.kt").exists())
        assertFalse(File(core, "BootReceiver.kt").exists())
        val permissions = File("src/main/java/com/gsynk/app/core/Permissions.kt").readText()
        assertFalse(permissions.contains("Item(ID_EXACT, \"Точные будильники\""))
        val nfc = read("NfcLinkScreen.kt")
        assertFalse(nfc.contains("encodeRules("))
    }

    @Test
    fun versionNewsAndRoutesAreConsistent() {
        assertEquals("6.2.0", ReleaseNews.VERSION)
        val news = File("src/main/java/com/gsynk/app/core/ReleaseNews.kt").readText()
        assertTrue(news.contains("greal-3d-return"))
        assertTrue(news.contains("расписание уехало в GAUTO"))
        val build = File("build.gradle.kts").readText()
        assertTrue(build.contains("versionCode = 40"))
        assertTrue(build.contains("versionName = \"6.2.0\""))
        assertTrue(GiouiPageIds.contains("greal"))
        assertTrue(GiouiPageIds.contains("antistress"))
        assertTrue(Catalog.ALL.size >= 100)
        assertEquals(Catalog.ALL.size, Catalog.ALL.map { it.id }.distinct().size)
        assertTrue(Catalog.ALL.all { CatalogGuards.routeIsWellFormed(it.route) })
        Catalog.Group.entries.forEach { group ->
            assertTrue("пустая категория " + group.title, Catalog.ALL.any { it.group == group })
        }
    }
}
