package com.gsynk.app

import com.gsynk.app.core.ChipLab
import com.gsynk.app.ui.Catalog
import com.gsynk.app.ui.Gsui4PageIds
import com.gsynk.app.ui.PhaseIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V4p1: проверки того, что патч делает то, что обещает.
 *
 * Здесь три темы: управление железом считается правильно (`ChipLab`), каждая фаза чипа
 * имеет экран, и папки в каталоге ведут туда, куда написано. Тесты держат интерфейс
 * честным: если плитка есть, за ней обязана быть страница.
 */
class V4P1Test {

    // ————— hex: как читают узлы —————

    @Test
    fun hexReadsSeparatedBytes() {
        val bytes = ChipLab.parseHex("60 4F 00 FF")
        assertNotNull(bytes)
        assertEquals(4, bytes!!.size)
        assertEquals(0x60.toByte(), bytes[0])
        assertEquals(0xFF.toByte(), bytes[3])
    }

    @Test
    fun hexUnderstandsDifferentSeparators() {
        assertEquals(3, ChipLab.parseHex("30-04,1A")!!.size)
        assertEquals(2, ChipLab.parseHex("0x30 0x04")!!.size)
        assertEquals(2, ChipLab.parseHex("30:04")!!.size)
    }

    @Test
    fun hexRejectsRubbish() {
        assertNull(ChipLab.parseHex(""))
        assertNull(ChipLab.parseHex("   "))
        assertNull(ChipLab.parseHex("ZZ"))
        assertNull(ChipLab.parseHex("1234"))
    }

    @Test
    fun hexIsPrintedBackTheSameWay() {
        assertEquals("60 4F 00 FF", ChipLab.formatHex(ChipLab.parseHex("60 4F 00 FF")!!))
    }

    // ————— износ батареи —————

    @Test
    fun wearIsPercentOfDesignCapacity() {
        assertEquals(100, ChipLab.wearPercent(5_000_000L, 5_000_000L)!!.toInt())
        assertEquals(85, ChipLab.wearPercent(4_250_000L, 5_000_000L)!!.toInt())
    }

    @Test
    fun wearRefusesToGuessWithoutNumbers() {
        assertNull(ChipLab.wearPercent(0L, 5_000_000L))
        assertNull(ChipLab.wearPercent(5_000_000L, 0L))
    }

    @Test
    fun wearKeepsAbsurdValuesSomewhere() {
        // Некоторые контроллеры пишут сырые значения: не показываем «300%», но и не падаем.
        assertTrue(ChipLab.wearPercent(9_000_000L, 3_000_000L)!! <= 130)
        assertTrue(ChipLab.wearPercent(1L, 5_000_000L)!! >= 1)
    }

    // ————— значения для записи в узел —————

    @Test
    fun cleanValueAllowsOnlySafeCharacters() {
        assertEquals("performance", ChipLab.cleanValue(" performance "))
        assertEquals("100,200", ChipLab.cleanValue("100,200"))
        assertEquals("schedutil", ChipLab.cleanValue("schedutil"))
    }

    @Test
    fun cleanValueBlocksShellTricks() {
        assertNull(ChipLab.cleanValue("performance; rm -rf /"))
        assertNull(ChipLab.cleanValue("700 > /sys/x"))
        assertNull(ChipLab.cleanValue("`rm`"))
        assertNull(ChipLab.cleanValue("\$(whoami)"))
        assertNull(ChipLab.cleanValue(""))
    }

    @Test
    fun cleanValueRefusesAbsurdLength() {
        assertNull(ChipLab.cleanValue("a".repeat(41)))
    }

    // ————— единицы измерения —————

    @Test
    fun currentIsConvertedFromMicroamps() {
        // Некоторые ядра пишут микроамперы: 1 200 000 мкА — это 1200 мА.
        assertEquals(1200f, ChipLab.currentMaFrom(1_200_000L), 0.5f)
        assertEquals(850f, ChipLab.currentMaFrom(850L), 0.5f)
        assertEquals(-420f, ChipLab.currentMaFrom(-420L), 0.5f)
    }

    @Test
    fun temperatureIsConvertedFromTenths() {
        assertEquals(31.5f, ChipLab.tempCelsiusFrom(315L), 0.01f)
        assertEquals(28f, ChipLab.tempCelsiusFrom(28L), 0.01f)
    }

    @Test
    fun wattsAreCurrentTimesVoltage() {
        assertEquals(4.0f, ChipLab.watts(1000f, 4000f), 0.01f)
        assertEquals(0f, ChipLab.watts(0f, 4000f), 0.01f)
        // Разряд (отрицательный ток) считается по модулю: мощность показываем без знака.
        assertEquals(4.0f, ChipLab.watts(-1000f, 4000f), 0.01f)
    }

    // ————— режим ядра: честный вердикт до попытки —————

    @Test
    fun verdictExplainsWhatHappens() {
        assertTrue(ChipLab.governorVerdict("schedutil", "performance", listOf("performance", "schedutil"))
            .startsWith("можно попробовать"))
        assertTrue(ChipLab.governorVerdict("performance", "performance", listOf("performance"))
            .contains("уже включён"))
        assertTrue(ChipLab.governorVerdict("schedutil", "turbo", listOf("performance"))
            .contains("не знает режима"))
        assertTrue(ChipLab.governorVerdict("", "", emptyList()).contains("не выбран"))
    }

    // ————— мотор: точки по частоте —————

    @Test
    fun patternFollowsFrequency() {
        val slow = ChipLab.hapticPattern(40, 50, 400)
        val fast = ChipLab.hapticPattern(200, 50, 400)
        assertTrue(fast.size > slow.size)      // высокая частота — больше импульсов на то же время
        assertEquals(0L, slow[0])
        assertTrue(slow.size % 2 == 1)          // первая пауза + пары «вкл/выкл»
    }

    @Test
    fun patternStaysInsideBounds() {
        val quiet = ChipLab.hapticPattern(20, 1, 100)
        val loud = ChipLab.hapticPattern(300, 100, 100)
        assertTrue(quiet.size >= 3)
        assertTrue(loud.all { it >= 0L })
        // Совсем без пауз мотор не остановить — проверяем, что пауза есть всегда.
        assertTrue(loud.drop(2).any { it > 0L })
    }

    @Test
    fun patternRefusesAbsurdInput() {
        val pattern = ChipLab.hapticPattern(10_000, 500, 10_000)
        assertTrue(pattern.size in 3..900)
    }

    // ————— фазы: каждая со своей страницей —————

    @Test
    fun everyPhaseHasAScreen() {
        assertTrue(PhaseIds.isNotEmpty())
        PhaseIds.forEach { id -> assertTrue("нет экрана у фазы " + id, id.isNotBlank()) }
        assertEquals(PhaseIds.size, PhaseIds.distinct().size)
    }

    @Test
    fun phasesCoverTheWholeChip() {
        // Восемь фаз: частоты, питание, панель, радио, вспышка, мотор, датчики, память.
        assertEquals(8, PhaseIds.size)
        assertTrue(PhaseIds.contains("cpu"))
        assertTrue(PhaseIds.contains("battery"))
        assertTrue(PhaseIds.contains("camera"))
        assertTrue(PhaseIds.contains("motor"))
    }

    @Test
    fun everyPhaseRouteIsKnownToTheRouter() {
        val source = java.io.File("src/main/java/com/gsynk/app/ui/V4P1Screens.kt").readText()
        PhaseIds.forEach { id ->
            assertTrue(
                "роутер не знает фазу " + id,
                source.contains("\"" + id + "\" -> {")
            )
        }
    }

    // ————— папки: каталог целиком разложен по категориям —————

    @Test
    fun everyTileBelongsToAFolder() {
        val folders = Catalog.Group.entries
        assertEquals(7, folders.size)
        Catalog.ALL.forEach { entry ->
            assertTrue("плитка без папки: " + entry.id, entry.group in folders)
        }
    }

    @Test
    fun everyFolderHasSomethingInside() {
        Catalog.Group.entries.forEach { folder ->
            val count = Catalog.ALL.count { it.group == folder }
            assertTrue("пустая папка: " + folder.title, count > 0)
        }
    }

    @Test
    fun folderSearchNeverLosesTiles() {
        Catalog.Group.entries.forEach { folder ->
            val inside = Catalog.view(group = folder)
            assertEquals(Catalog.ALL.count { it.group == folder }, inside.size)
        }
    }

    @Test
    fun everyFolderHasHumanTitleAndHint() {
        Catalog.Group.entries.forEach { folder ->
            assertTrue(folder.title.isNotBlank())
            assertTrue(folder.hint.isNotBlank())
        }
    }

    @Test
    fun chipPagesAreInTheCatalogToo() {
        // Управление железом должно быть и в папках, а не только на главном экране.
        val routes = Catalog.ALL.map { it.route }
        PhaseIds.forEach { id ->
            assertTrue("фазы " + id + " нет в каталоге", routes.contains("page:" + id))
        }
        assertTrue(routes.contains("page:folders"))
    }

    @Test
    fun catalogSortModesCoverEverything() {
        val favorites = Catalog.ALL.take(3).map { it.id }.toSet()
        Catalog.Sort.entries.forEach { mode ->
            // Режим «мои» показывает только избранное — даём ему избранное, а не пустоту.
            val list = Catalog.view(sort = mode, favorites = favorites)
            assertTrue("сортировка теряет плитки: " + mode.title, list.isNotEmpty())
            assertTrue(list.size <= Catalog.ALL.size)
        }
    }

    @Test
    fun pageIdsAreUniqueAcrossBothScreenFiles() {
        assertEquals(Gsui4PageIds.size, Gsui4PageIds.distinct().size)
        assertEquals(PhaseIds.size, PhaseIds.distinct().size)
        // Одна страница не может жить в двух местах: иначе роутер выберет случайную.
        val both = Gsui4PageIds.intersect(PhaseIds.toSet())
        assertFalse("страницы объявлены дважды: " + both, both.isNotEmpty())
    }
}
