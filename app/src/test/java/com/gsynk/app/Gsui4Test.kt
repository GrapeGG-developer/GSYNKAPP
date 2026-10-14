package com.gsynk.app

import com.gsynk.app.core.CatalogGuards
import com.gsynk.app.core.Deep
import com.gsynk.app.core.Everyday
import com.gsynk.app.core.Eggs
import com.gsynk.app.core.Flicker
import com.gsynk.app.core.Roulette
import com.gsynk.app.core.Skin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Тесты GSUI V4: всё, что можно проверить без телефона.
 *
 * Проверяем не «нарисовалось ли», а логику: разборы из лаборатории железа,
 * ежедневный набор (детерминированный), пасхалки, рулетку, кожу и правила каталога.
 * Каждая из этих вещей раньше жила в интерфейсе и не проверялась вовсе.
 */
class Gsui4Test {

    // ————— железо: разборы —————

    @Test
    fun frequencyFromSysIsConvertedToMhz() {
        assertEquals(2_400, Deep.parseFreqKhz("2400000\n"))
        assertEquals(0, Deep.parseFreqKhz(""))
        assertEquals(0, Deep.parseFreqKhz("не число"))
    }

    @Test
    fun wattsAreCountedFromCurrentAndVoltage() {
        // 2 А при 4,2 В — это 8,4 Вт.
        assertEquals(8.4f, Deep.watts(2_000f, 4_200), 0.01f)
        // Разряд считается по модулю: минус — это направление, а не отрицательная мощность.
        assertEquals(1.85f, Deep.watts(-500f, 3_700), 0.01f)
        assertEquals(0f, Deep.watts(0f, 0), 0.001f)
    }

    @Test
    fun temperatureRawValuesAreNormalized() {
        assertEquals(42.5f, Deep.tempFromRaw(42.5f), 0.01f)
        assertEquals(42.5f, Deep.tempFromRaw(425f), 0.01f)
        assertEquals(42.5f, Deep.tempFromRaw(4_250f), 0.01f)
        assertEquals(42.5f, Deep.tempFromRaw(42_500f), 0.01f)
    }

    @Test
    fun thermalZonesGetHumanNames() {
        assertEquals("батарея", Deep.prettyZone("battery"))
        assertEquals("чип", Deep.prettyZone("cpu-0-0"))
        assertEquals("ускоритель", Deep.prettyZone("gpu-thermal"))
        assertEquals("разъём", Deep.prettyZone("usb-therm"))
        assertEquals("экран", Deep.prettyZone("display_zone"))
        assertEquals("неизвестная", Deep.prettyZone("неизвестная"))
    }

    @Test
    fun wifiChannelsAreCountedByFrequency() {
        assertEquals(1, Deep.channelOf(2412))
        assertEquals(6, Deep.channelOf(2437))
        assertEquals(13, Deep.channelOf(2472))
        assertEquals(14, Deep.channelOf(2484))
        assertEquals(36, Deep.channelOf(5180))
        assertEquals(44, Deep.channelOf(5220))
        assertEquals(100, Deep.channelOf(5500))
        assertEquals(149, Deep.channelOf(5745))
        assertEquals(0, Deep.channelOf(1))
    }

    @Test
    fun bandIsRecognized() {
        assertEquals("2,4 ГГц", Deep.bandOf(2437))
        assertEquals("5 ГГц", Deep.bandOf(5180))
        assertEquals("6 ГГц", Deep.bandOf(5955))
    }

    @Test
    fun congestionIsSortedByLoad() {
        val pairs = listOf(1 to 6, 2 to 6, 3 to 6, 4 to 11, 5 to 1)
        val sorted = Deep.congestion(pairs)
        assertEquals(6, sorted.first().first)
        assertEquals(3, sorted.first().second)
        assertEquals(1, sorted.last().first)
    }

    @Test
    fun memoryReadingParsesKbToMb() {
        // В /proc/meminfo значения в килобайтах: 8 388 608 КБ — это 8 192 МБ.
        val file = java.io.File.createTempFile("meminfo", ".txt")
        file.writeText("MemTotal:        8388608 kB\nSwapTotal:       2097152 kB\n")
        assertEquals(8_192, Deep.readLongMb(file.absolutePath, "MemTotal"))
        assertEquals(2_048, Deep.readLongMb(file.absolutePath, "SwapTotal"))
        assertEquals(0, Deep.readLongMb(file.absolutePath, "НетТакого"))
        file.delete()
    }

    @Test
    fun batteryLabelsAreReadable() {
        assertEquals("хорошее", Deep.healthLabel(2))
        assertEquals("перегрев", Deep.healthLabel(3))
        assertEquals("розетка", Deep.pluggedLabel(1))
        assertEquals("USB", Deep.pluggedLabel(2))
        assertEquals("заряжается", Deep.statusLabel(2))
    }

    @Test
    fun rssiIsExtractedFromText() {
        assertEquals(-67, Deep.rssiFrom("-67 дБм"))
        assertEquals(42, Deep.rssiFrom("уровень 42"))
        assertEquals(null, Deep.rssiFrom("нет числа"))
    }

    // ————— мерцание —————

    @Test
    fun flickerFrequencyIsCountedByCrossings() {
        // Ровно 100 периодов за секунду: 1 000 точек по 1 мс, знак меняется 200 раз.
        val samples = (0 until 1_000).map { index -> if (index % 10 < 5) 0.2f else 0.8f }
        val stamps = (0 until 1_000).map { index -> index * 1_000_000L }
        val hz = Flicker.frequencyOf(samples, stamps)
        assertEquals(100f, hz, 3f)
    }

    @Test
    fun steadyLightMeansNoFlicker() {
        val samples = List(500) { 0.5f }
        val stamps = List(500) { it * 1_000_000L }
        assertEquals(0f, Flicker.frequencyOf(samples, stamps), 0.001f)
    }

    @Test
    fun flickerDepthIsMeasured() {
        val samples = listOf(0.2f, 1.0f, 0.2f, 1.0f)
        assertTrue(Flicker.depthOf(samples) > 0.5f)
        assertEquals(0f, Flicker.depthOf(listOf(0.4f, 0.4f)), 0.0001f)
    }

    // ————— каждый день —————

    @Test
    fun dailyContentIsTheSameForTheSameDay() {
        val date = LocalDate.of(2026, 10, 14)
        val first = Everyday.forDate(date)
        val second = Everyday.forDate(date)
        assertEquals(first.fact, second.fact)
        assertEquals(first.task, second.task)
        assertEquals(first.joke, second.joke)
        assertEquals(first.number, second.number)
        assertEquals(first.title, second.title)
    }

    @Test
    fun dailyContentChangesOverTheMonth() {
        // За тридцать дней набор обязан меняться: иначе «каждый день» — обман.
        val facts = (0..29).map { Everyday.forDate(LocalDate.of(2026, 10, 1).plusDays(it.toLong())).fact }
        val jokes = (0..29).map { Everyday.forDate(LocalDate.of(2026, 10, 1).plusDays(it.toLong())).joke }
        assertTrue("факт не менялся", facts.distinct().size >= 5)
        assertTrue("прикол не менялся", jokes.distinct().size >= 5)
    }

    @Test
    fun dailyNumberAndToneAreInRange() {
        (0..60).forEach { offset ->
            val day = Everyday.forDate(LocalDate.of(2026, 1, 1).plusDays(offset.toLong()))
            assertTrue(day.number in 100..999)
            assertTrue(day.tone in 0 until Everyday.MetroTones)
            assertTrue(day.sticker in 0 until 12)
            assertTrue(day.fact.length > 20)
        }
    }

    @Test
    fun streakCountsConsecutiveDays() {
        val today = 20_000L
        assertEquals(0, Everyday.streakFrom(emptyList(), today))
        assertEquals(1, Everyday.streakFrom(listOf(today), today))
        assertEquals(3, Everyday.streakFrom(listOf(today, today - 1, today - 2), today))
        assertEquals(2, Everyday.streakFrom(listOf(today - 1, today - 2), today))
        // Визит пять дней назад серию не сохраняет.
        assertEquals(0, Everyday.streakFrom(listOf(today - 5), today))
    }

    @Test
    fun rewardGrowsWithStreak() {
        assertTrue(Everyday.rewardFor(1).contains("третий день"))
        assertTrue(Everyday.rewardFor(3).contains("три дня"))
        assertTrue(Everyday.rewardFor(7).contains("неделя"))
        assertTrue(Everyday.rewardFor(30).contains("тридцатка"))
    }

    @Test
    fun generatorStaysInBounds() {
        val rnd = Everyday.Lcg(1)
        repeat(500) {
            val value = rnd.next(20)
            assertTrue(value in 0 until 20)
        }
    }

    // ————— пасхалки и рулетка —————

    @Test
    fun everyEggHasFullDescription() {
        assertTrue(Eggs.ALL.size >= 12)
        Eggs.ALL.forEach { egg ->
            assertTrue(egg.id.isNotBlank())
            assertTrue(egg.title.isNotBlank())
            assertTrue(egg.hint.isNotBlank())
            assertTrue(egg.secret.isNotBlank())
            assertTrue(egg.reward.isNotBlank())
        }
        assertEquals(Eggs.ALL.size, Eggs.ALL.map { it.id }.distinct().size)
    }

    @Test
    fun eggIsFoundById() {
        assertNotNull(Eggs.byId("logo7"))
        assertEquals("семь касаний", Eggs.byId("logo7")?.title)
        assertEquals(null, Eggs.byId("нет такой"))
    }

    @Test
    fun rouletteNeverRepeatsTwiceInARow() {
        val pool = listOf("a", "b", "c", "d")
        var previous: String? = null
        repeat(30) { roll ->
            val picked = Roulette.pick(pool, emptyList(), roll)
            assertTrue(pool.contains(picked))
            assertFalse(picked == previous)
            previous = picked
        }
    }

    @Test
    fun rouletteUsesRarePoolOnEveryFifthRoll() {
        val pool = listOf("обычная-1", "обычная-2")
        val rare = listOf("редкая")
        assertEquals("редкая", Roulette.pick(pool, rare, 4))
        assertEquals("редкая", Roulette.pick(pool, rare, 9))
    }

    @Test
    fun rouletteSurvivesEmptyPools() {
        assertEquals("page:surprise", Roulette.pick(emptyList(), emptyList(), 0))
    }

    // ————— кожа и скругления —————

    @Test
    fun skinClampsValuesToReasonableRanges() {
        val wild = Skin.Values(tile = 500, card = -20, sheet = 900, blur = 99, rays = 99, speed = 1_000, level = 9)
        val clamped = Skin.clamp(wild)
        assertEquals(32, clamped.tile)
        assertEquals(0, clamped.card)
        assertEquals(36, clamped.sheet)
        assertEquals(30, clamped.blur)
        assertEquals(12, clamped.rays)
        assertEquals(200, clamped.speed)
        assertEquals(2, clamped.level)
    }

    @Test
    fun weakPhoneKeepsEffectsLower() {
        // Полный набор разрешён только там, где железо тянет: остальным — облегчённый уровень.
        assertEquals(1, Skin.effectiveLevel(profile(full = false), 2))
        assertEquals(0, Skin.effectiveLevel(profile(full = false), 0))
        assertEquals(2, Skin.effectiveLevel(profile(full = true), 2))
    }

    @Test
    fun presetsAreCompleteAndDistinct() {
        assertTrue(Skin.PRESETS.size >= 5)
        val names = Skin.PRESETS.map { it.first }
        assertEquals(names.size, names.distinct().size)
        Skin.PRESETS.forEach { (_, title, values) ->
            assertTrue(title.isNotBlank())
            assertTrue(Skin.GLASSES.any { it.first == values.glass })
            assertEquals(0, Skin.clamp(values).level - values.level)
        }
    }

    @Test
    fun glassHasHumanTitles() {
        assertEquals("жидкое", Skin.glassTitle("liquid"))
        assertEquals("матовое", Skin.glassTitle("matte"))
        assertEquals("облегчённые", Skin.label(1))
        assertEquals("полные", Skin.label(2))
    }

    // ————— метрики плиток и каталога —————

    @Test
    fun metroGridPlacesTilesWithoutOverlap() {
        val plan = com.gsynk.app.ui.Metro.plan(
            listOf(
                com.gsynk.app.ui.Metro.Size.SMALL,
                com.gsynk.app.ui.Metro.Size.SMALL,
                com.gsynk.app.ui.Metro.Size.MEDIUM,
                com.gsynk.app.ui.Metro.Size.SMALL,
                com.gsynk.app.ui.Metro.Size.WIDE
            )
        )
        assertEquals(5, plan.size)
        // Плитки не накладываются друг на друга — это главное свойство сетки.
        val sizes = listOf(
            com.gsynk.app.ui.Metro.Size.SMALL,
            com.gsynk.app.ui.Metro.Size.SMALL,
            com.gsynk.app.ui.Metro.Size.MEDIUM,
            com.gsynk.app.ui.Metro.Size.SMALL,
            com.gsynk.app.ui.Metro.Size.WIDE
        )
        val taken = HashSet<Pair<Int, Int>>()
        plan.forEachIndexed { index, place ->
            val size = sizes[index]
            for (row in place.first until place.first + size.h) {
                for (column in place.second until place.second + size.w) {
                    assertTrue("колонка вышла за сетку", column < com.gsynk.app.ui.Metro.COLUMNS)
                    assertTrue("плитки наложились", taken.add(row to column))
                }
            }
        }
        // Первые две маленькие стоят рядом в первой строке.
        assertEquals(0 to 0, plan[0])
        assertEquals(0 to 1, plan[1])
        // Широкая плитка начинается у левого края: иначе она не влезет в четыре клетки.
        assertEquals(0, plan[4].second)
    }

    @Test
    fun catalogFiltersBySearchAndGroup() {
        val all = com.gsynk.app.ui.Catalog.ALL
        assertTrue(all.size >= 60)
        assertTrue(all.map { it.id }.distinct().size == all.size)

        val found = com.gsynk.app.ui.Catalog.view(query = "остров")
        assertTrue(found.isNotEmpty())
        assertTrue(found.all { it.matches("остров") })

        val hardware = com.gsynk.app.ui.Catalog.view(group = com.gsynk.app.ui.Catalog.Group.HARDWARE)
        assertTrue(hardware.isNotEmpty())
        assertTrue(hardware.all { it.group == com.gsynk.app.ui.Catalog.Group.HARDWARE })
    }

    @Test
    fun catalogSortModesKeepEverything() {
        com.gsynk.app.ui.Catalog.Sort.entries
            .filter { it != com.gsynk.app.ui.Catalog.Sort.FAVORITE }
            .forEach { mode ->
            val list = com.gsynk.app.ui.Catalog.view(sort = mode)
            assertEquals("режим " + mode.title, com.gsynk.app.ui.Catalog.ALL.size, list.size)
        }
    }

    @Test
    fun catalogFavoritesHideOthers() {
        val favoriteId = com.gsynk.app.ui.Catalog.ALL.first().id
        val list = com.gsynk.app.ui.Catalog.view(sort = com.gsynk.app.ui.Catalog.Sort.FAVORITE, favorites = setOf(favoriteId))
        assertEquals(1, list.size)
        assertEquals(favoriteId, list.first().id)
    }

    @Test
    fun catalogRoutesUseKnownPrefixes() {
        val prefixes = setOf("panel", "page", "tool", "flag", "lab", "sys")
        com.gsynk.app.ui.Catalog.ALL.forEach { entry ->
            val (kind, name) = com.gsynk.app.ui.Router.split(entry.route)
            assertTrue("маршрут " + entry.route, prefixes.contains(kind))
            assertTrue("пустое имя в " + entry.route, name.isNotBlank())
        }
    }

    @Test
    fun catalogToneIsInsidePalette() {
        val tones = com.gsynk.app.ui.Metro.Palette.tiles.size
        com.gsynk.app.ui.Catalog.ALL.forEach { entry ->
            assertTrue("плитка " + entry.id, entry.tone in 0 until tones)
        }
    }

    @Test
    fun metroPaletteKeepsWindowsColors() {
        // Цвета Metro должны остаться узнаваемыми: фиолетовый, бирюзовый, оранжевый.
        assertEquals(0xFFA200FF.toInt(), com.gsynk.app.ui.Metro.Palette.Purple.toArgbCompat())
        assertEquals(0xFF00ABA9.toInt(), com.gsynk.app.ui.Metro.Palette.Teal.toArgbCompat())
        assertEquals(0xFFF09609.toInt(), com.gsynk.app.ui.Metro.Palette.Mango.toArgbCompat())
    }

    @Test
    fun guardRestoresDefaults() {
        assertEquals(Skin.Values(), Skin.clamp(Skin.Values()))
        assertTrue(CatalogGuards.everyRouteIsWellFormed(com.gsynk.app.ui.Catalog.ALL.map { it.route }))
        assertFalse(CatalogGuards.routeIsWellFormed("panel"))
        assertFalse(CatalogGuards.routeIsWellFormed("panel:"))
        assertFalse(CatalogGuards.routeIsWellFormed("неведомо:nfc"))
        assertTrue(CatalogGuards.everyIdIsUnique(com.gsynk.app.ui.Catalog.ALL.map { it.id }))
        assertFalse(CatalogGuards.everyIdIsUnique(listOf("nfc", "nfc")))
    }

    private fun profile(full: Boolean): com.gsynk.app.core.DeviceProfile.Info =
        com.gsynk.app.core.DeviceProfile.Info(
            model = "тест",
            brand = "тест",
            androidSdk = 36,
            androidLabel = "16",
            hasNfc = true,
            supportsOverlay = true,
            reason = "тест",
            tier = if (full) com.gsynk.app.core.DeviceProfile.TIER_FULL else com.gsynk.app.core.DeviceProfile.TIER_LEGACY
        )
}

/**
 * Цвет в ARGB. Считаем сами и в скобках: у инфиксных сдвигов один уровень
 * приоритета, и без скобок «Alpha shl 24 or Red shl 16» склеится не в тот байт.
 */
private fun androidx.compose.ui.graphics.Color.toArgbCompat(): Int {
    val a = (alpha * 255f).toInt()
    val r = (red * 255f).toInt()
    val g = (green * 255f).toInt()
    val b = (blue * 255f).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
