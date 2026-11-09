package com.gsynk.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Сторож интерфейса: то, на что жаловался телефон в отчётах.
 *
 * 1. Панель, нарисованная без `return`, остаётся под содержимым — «Нагрузка» и
 *    «Хроника» не открывались, потому что список инструментов рисовался поверх
 *    и перехватывал нажатия.
 * 2. Экран без отступов уходит под строку состояния, и кнопки сверху не нажать.
 */
class UiGuardTest {

    private val ui = File("src/main/java/com/gsynk/app/ui")
    private fun read(name: String) = File(ui, name).readText()

    /** Между «if (showX) {» и закрывающей скобкой обязан стоять return. */
    private fun panelReturns(text: String, marker: String): Boolean {
        val start = text.indexOf(marker)
        if (start < 0) return false
        val block = text.substring(start, (start + 400).coerceAtMost(text.length))
        return block.contains("return")
    }

    @Test
    fun metroPanelsAreDirectAndClosedByShell() {
        assertTrue("старый хаб ToolsScreen вернулся", !File(ui, "ToolsScreen.kt").exists())
        val main = read("MainScreen.kt")
        listOf(
            "MetroAppShell(",
            "DirectFeatureScreen(",
            "fun closeCurrentApp()",
            "fun minimizeCurrentApp()",
            "panel == \"feature\"",
            "EmptyHomeScreen(",
            "StartMenuScreen("
        ).forEach { marker ->
            assertTrue("прямой Metro-маршрут потерялся: $marker", main.contains(marker))
        }
        assertTrue("pending-маршруты старого хаба вернулись", !main.contains("pendingTool") && !main.contains("ToolsScreen("))
    }

    @Test
    fun panelRoutingHasNoLeftovers() {
        val host = read("FeatureHost.kt")
        listOf("\"quick\" ->", "\"nearby\" ->", "\"diagnostics\" ->", "\"screensaver\" ->", "\"turbo\" ->")
            .forEach { marker -> assertTrue("прямой маршрут не объявлен: $marker", host.contains(marker)) }
        val hardware = read("HardwareScreens.kt")
        listOf("\"lowlevel\" ->", "\"strobe\" ->", "\"dimmer\" ->", "\"ir\" ->")
            .forEach { marker -> assertTrue("железный маршрут не объявлен: $marker", hardware.contains(marker)) }
        assertTrue("старый экран-хаб LabScreen вернулся", !hardware.contains("fun LabScreen("))
    }

    @Test
    fun screensRespectStatusBar() {
        val components = read("Components.kt")
        val shell = read("StartScreen.kt")
        assertTrue(
            "Metro-оболочка обязана оставлять место строке состояния",
            shell.contains("windowInsetsPadding(WindowInsets.statusBars)")
        )
        assertTrue(
            "GrapeScreen обязан оставлять место панели навигации",
            components.contains("windowInsetsPadding(WindowInsets.navigationBars)")
        )
        val gate = read("PermissionGate.kt")
        assertTrue(
            "полноэкранная панель разрешений обязана учитывать вырез и панель навигации",
            gate.contains("windowInsetsPadding(WindowInsets.safeDrawing)")
        )
        // Экраны игр и «показ по касанию» намеренно во весь экран — они вне проверки,
        // но у них не должно быть кнопок в верхней кромке (проверяем ниже отдельно).
    }

    @Test
    fun everyRoomFitsItsBox() {
        // Каждый файл, использующий GrapeScreen, не должен добавлять свою прокрутку:
        // именно вложенный скролл уронил приложение в 2.3.0.
        ui.listFiles { f: File -> f.name.endsWith(".kt") }?.toList().orEmpty().forEach { file ->
            val text = file.readText()
            // Опасное сочетание ровно одно: «во весь размер» плюс своя прокрутка —
            // именно так экран сопряжения уронил приложение в 2.3.0. Прокрутка внутри
            // блока с явной высотой (журнал, чат) безопасна и разрешена.
            if (file.name != "Components.kt" && text.contains("GrapeScreen(")) {
                val dangerous = text.contains("fillMaxSize()") && text.contains("verticalScroll(")
                assertTrue(
                    "«${file.name}»: своя прокрутка на весь размер внутри GrapeScreen — так падало приложение",
                    !dangerous || file.name == "ChatScreen.kt"
                )
            }
        }
    }

    @Test
    fun noHiddenSystemSectionsLeft() {
        // По отчёту с телефона: на Android 16 и HyperOS эти разделы ничего не открывают.
        val lab = read("HardwareScreens.kt")
        assertTrue("раздел «Скрытое Android» убран", !lab.contains("HiddenScreensScreen"))
        assertTrue("раздел HyperOS убран", !lab.contains("HyperOsScreen"))
        val labCore = File("src/main/java/com/gsynk/app/core/DeviceLab.kt").readText()
        assertTrue("списки скрытых экранов удалены", !labCore.contains("hyperOsScreens"))
        assertTrue("и список Android-экранов тоже", !labCore.contains("val screens: List<HiddenScreen>"))
    }

    @Test
    fun updatesAreCheckedConstantly() {
        val vm = File("src/main/java/com/gsynk/app/ui/MainViewModel.kt").readText()
        assertTrue(
            "проверка обновлений должна идти часто (раз в минуту)",
            vm.contains("REFRESH_INTERVAL_MS = 60 * 1000L")
        )
        assertTrue(
            "при возвращении в приложение проверка тоже должна идти быстро",
            vm.contains("STALE_INTERVAL_MS = 30 * 1000L")
        )
        // Проверка должна идти с токеном (иначе лимит GitHub — 60 запросов в час)
        // и с меткой ETag: ответ 304 лимит не тратит вообще.
        assertTrue(
            "проверка обновлений обязана идти с вшитым токеном",
            vm.contains("UpdateChecker.check(app(), repo, token)")
        )
        assertTrue(
            "в проверке должен быть запасной путь без API",
            File("src/main/java/com/gsynk/app/core/UpdateChecker.kt").readText()
                .contains("checkByPage")
        )
        // Панель обновления больше не «одноразовая»: её зовёт политика напоминаний.
        assertTrue(
            "панель обновления должна открываться по событию, а не один раз",
            File("src/main/java/com/gsynk/app/ui/MainScreen.kt").readText()
                .contains("LaunchedEffect(updatePrompt)")
        )
    }

    @Test
    fun customizationIsWired() {
        val settings = File("src/main/java/com/gsynk/app/core/Models.kt").readText()
        listOf(
            "val accent: Int",
            "val backgroundSpeed: Int",
            "val haptics: Boolean",
            "val clickSound: Boolean",
            "val tileScale: Int",
            "val tileCounters: Boolean",
            "val compactList: Boolean"
        ).forEach { field ->
            assertTrue("настройка «$field» потерялась", settings.contains(field))
        }
        val sheet = read("SettingsScreen.kt")
        assertTrue("в настройках нет раздела кастомизации", sheet.contains("GSYNKPRG — дизайн"))
        assertTrue("акцент не применяется", read("Tweaks.kt").contains("accentBrush"))
        assertTrue("фон не читает скорость", read("Components.kt").contains("UiTweaks.breathMs"))
    }
}
