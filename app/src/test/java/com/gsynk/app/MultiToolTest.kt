package com.gsynk.app

import com.gsynk.app.core.DeviceProfile
import com.gsynk.app.core.IslandPosition
import com.gsynk.app.core.IslandWidget
import com.gsynk.app.core.NfcLink
import com.gsynk.app.core.Passwords
import com.gsynk.app.core.PayloadCodec
import com.gsynk.app.core.Rule
import com.gsynk.app.core.CrashAnalyzer
import com.gsynk.app.core.ReportSanitizer
import com.gsynk.app.core.RuleSort
import com.gsynk.app.core.TileGame
import com.gsynk.app.core.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Логика мультитула: профиль устройства, версии для OTA, обмен по NFC,
 * сортировка правил, остров и генератор паролей. Всё без Android — чистая логика.
 */
class MultiToolTest {

    private fun info(
        tier: String,
        hasNfc: Boolean = true,
        sdk: Int = 36
    ) = DeviceProfile.Info(
        model = "Poco X8 Pro Max",
        brand = "Xiaomi",
        androidSdk = sdk,
        androidLabel = "Android 16",
        hasNfc = hasNfc,
        supportsOverlay = true,
        reason = "тест",
        tier = tier
    )

    @Test
    fun fullTier_hasEveryFeature() {
        val full = info(DeviceProfile.TIER_FULL)
        assertTrue(DeviceProfile.feature(full, DeviceProfile.Feature.RULES))
        assertTrue(DeviceProfile.feature(full, DeviceProfile.Feature.AI_CHAT))
        assertTrue(DeviceProfile.feature(full, DeviceProfile.Feature.ISLAND))
        assertTrue(DeviceProfile.feature(full, DeviceProfile.Feature.UPDATE))
        assertTrue(DeviceProfile.feature(full, DeviceProfile.Feature.TOOLS))
    }

    @Test
    fun legacyTier_keepsBaseFeaturesOnly() {
        val legacy = info(DeviceProfile.TIER_LEGACY, sdk = 33)
        assertTrue(DeviceProfile.feature(legacy, DeviceProfile.Feature.RULES))
        assertTrue(DeviceProfile.feature(legacy, DeviceProfile.Feature.NFC_LINK))
        assertTrue(DeviceProfile.feature(legacy, DeviceProfile.Feature.GAMES))
        assertTrue(DeviceProfile.feature(legacy, DeviceProfile.Feature.FLASHLIGHT))
        assertTrue(DeviceProfile.feature(legacy, DeviceProfile.Feature.SCREENSAVER))
        assertFalse(DeviceProfile.feature(legacy, DeviceProfile.Feature.AI_CHAT))
        assertFalse(DeviceProfile.feature(legacy, DeviceProfile.Feature.ISLAND))
        assertFalse(DeviceProfile.feature(legacy, DeviceProfile.Feature.UPDATE))
    }

    @Test
    fun profileSummary_mentionsModel() {
        val full = info(DeviceProfile.TIER_FULL)
        assertTrue(full.isFull)
        assertTrue(full.summary.isNotBlank())
    }

    // ————— обновления —————

    @Test
    fun compareVersions_ordersCorrectly() {
        assertTrue(UpdateChecker.compareVersions("2.1.0", "2.0.2") > 0)
        assertTrue(UpdateChecker.compareVersions("2.0.2", "2.0.10") < 0)
        assertEquals(0, UpdateChecker.compareVersions("2.1.0", "2.1.0"))
        assertTrue(UpdateChecker.compareVersions("v2.2", "2.1.9") > 0)
    }

    // ————— NFC —————

    @Test
    fun transferKinds_coverAllRequiredTypes() {
        val kinds = NfcLink.TRANSFER_KINDS.map { it.kind }
        listOf(
            NfcLink.KIND_TEXT,
            NfcLink.KIND_URL,
            NfcLink.KIND_CONTACT,
            NfcLink.KIND_PASSWORD,
            NfcLink.KIND_GEO,
            NfcLink.KIND_PROFILE,
            NfcLink.KIND_GAME
        ).forEach { assertTrue("нет типа $it", it in kinds) }
    }

    @Test
    fun transferOption_hasTitleAndHint() {
        NfcLink.TRANSFER_KINDS.forEach { option ->
            assertTrue(option.title.isNotBlank())
            assertTrue(option.hint.isNotBlank())
        }
    }

    @Test
    fun gameInvite_roundTrip() {
        val invite = NfcLink.GameInvite(game = "reaction", from = "Poco X3 Pro", seed = 4242L)
        val parsed = NfcLink.parseGameInvite("${invite.game};${invite.from};${invite.seed}")
        assertNotNull(parsed)
        assertEquals("reaction", parsed!!.game)
        assertEquals(4242L, parsed.seed)
    }

    // ————— профиль правил по NFC —————

    @Test
    fun rulesEncode_decodeKeepsFields() {
        val rules = listOf(
            Rule(name = "Вечер", hour = 22, minute = 15, days = listOf(1, 2, 3, 4, 5), brightness = 20, volume = 0),
            Rule(name = "Утро", hour = 6, minute = 45, days = listOf(6, 7), brightness = 80, volume = 60, enabled = false)
        )
        val decoded = PayloadCodec.decodeRules(PayloadCodec.encodeRules(rules))
        assertEquals(2, decoded.size)
        assertEquals("Вечер", decoded[0].name)
        assertEquals(22, decoded[0].hour)
        assertEquals(20, decoded[0].brightness)
        assertEquals(listOf(6, 7), decoded[1].days)
        assertFalse(decoded[1].enabled)
    }

    @Test
    fun rulesDecode_ignoresForeignPayload() {
        assertTrue(PayloadCodec.decodeRules("просто текст").isEmpty())
        assertTrue(PayloadCodec.decodeRules("").isEmpty())
    }

    @Test
    fun rulesEncode_staysWithinNfcLimit() {
        val rules = (1..12).map {
            Rule(name = "Правило номер $it", hour = it % 24, minute = 0)
        }
        val encoded = PayloadCodec.encodeRules(rules)
        assertTrue("слишком длинно: ${encoded.length}", encoded.length < 800)
    }

    // ————— сортировка и остров —————

    @Test
    fun ruleSort_ordersAsExpected() {
        val rules = listOf(
            Rule(name = "Б", hour = 18, minute = 0, enabled = false),
            Rule(name = "А", hour = 7, minute = 30, enabled = true)
        )
        assertEquals("А", RuleSort.apply(rules, RuleSort.BY_NAME)[0].name)
        assertEquals("А", RuleSort.apply(rules, RuleSort.BY_TIME)[0].name)
        assertEquals("А", RuleSort.apply(rules, RuleSort.ACTIVE_FIRST)[0].name)
    }

    @Test
    fun islandPositions_allHaveLabels() {
        assertEquals(8, IslandPosition.ALL.size)
        IslandPosition.ALL.forEach { assertTrue(IslandPosition.label(it).isNotBlank()) }
        assertEquals(IslandPosition.ALL.size, IslandPosition.ALL.toSet().size)
    }

    @Test
    fun islandWidgets_allHaveLabels() {
        assertTrue(IslandWidget.ALL.isNotEmpty())
        IslandWidget.ALL.forEach { assertTrue(IslandWidget.label(it).isNotBlank()) }
    }

    // ————— 2048 —————

    @Test
    fun tiles_mergeAndScore() {
        val board = listOf(
            2, 2, 4, 4,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0
        )
        val (moved, gained) = TileGame.slide(board, TileGame.LEFT)
        assertEquals(listOf(4, 8, 0, 0), moved.take(4))
        assertEquals(12, gained)
    }

    @Test
    fun tiles_doNotMergeTwiceInARow() {
        val board = listOf(
            2, 2, 2, 2,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0
        )
        val (moved, gained) = TileGame.slide(board, TileGame.LEFT)
        assertEquals(listOf(4, 4, 0, 0), moved.take(4))
        assertEquals(8, gained)
    }

    @Test
    fun tiles_slideIsDirectional() {
        val board = listOf(
            2, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0
        )
        // плитка «2» из левого верхнего угла уезжает в нужный край
        assertEquals(2, TileGame.slide(board, TileGame.RIGHT).first[3])
        assertEquals(2, TileGame.slide(board, TileGame.LEFT).first[0])
        assertEquals(2, TileGame.slide(board, TileGame.UP).first[0])
        assertEquals(2, TileGame.slide(board, TileGame.DOWN).first[12])
    }

    @Test
    fun tiles_stuckWhenFullAndNoMerges() {
        val full = listOf(
            2, 4, 2, 4,
            4, 2, 4, 2,
            2, 4, 2, 4,
            4, 2, 4, 2
        )
        assertTrue(TileGame.isStuck(full))
        val withPair = full.toMutableList().also { it[1] = 2 }
        assertFalse(TileGame.isStuck(withPair))
    }

    @Test
    fun tiles_spawnAddsOneTile() {
        val board = List(16) { 0 }
        val next = TileGame.spawn(board)
        assertEquals(1, next.count { it != 0 })
        assertTrue(next.all { it == 0 || it == 2 || it == 4 })
        val started = TileGame.start()
        assertEquals(2, started.count { it != 0 })
    }

    @Test
    fun tiles_winFlag() {
        assertTrue(TileGame.hasReached(listOf(2048) + List(15) { 0 }))
        assertFalse(TileGame.hasReached(listOf(1024) + List(15) { 0 }))
        assertEquals(2048, TileGame.maxTile(listOf(2, 2048, 4)))
    }

    // ————— анализатор отчётов —————

    /** Похоже на токен, но собрано на месте: настоящие секреты в коде не держим. */
    private val fakeToken = "ghp_" + "A".repeat(36)

    private val crashA = """
        GSYNK 2.1.1 (versionCode 9)
        Тип: сбой приложения
        java.lang.IllegalStateException: не удалось прочитать показатели 1500
        at android.os.BatteryManager.getIntProperty(BatteryManager.java:1200)
        at com.gsynk.app.core.DeviceStats.snapshot(HackTools.kt:49)
        at com.gsynk.app.ui.ToolsViewModel.startStats(ToolsViewModel.kt:49)
    """.trimIndent()

    @Test
    fun analyzer_groupsSameCrash() {
        val groups = CrashAnalyzer.group(
            listOf(
                "crash-20261013-101500.txt" to crashA,
                "crash-20261013-113000.txt" to crashA
            )
        )
        assertEquals(1, groups.size)
        assertEquals(2, groups.first().count)
        assertTrue(groups.first().signature.contains("IllegalStateException"))
        assertTrue(groups.first().signature.contains("DeviceStats"))
        assertEquals("13.10.2026 11:30", groups.first().lastStamp)
    }

    @Test
    fun analyzer_masksNumbersInSignature() {
        val other = crashA.replace("1500", "20000")
        val groups = CrashAnalyzer.group(listOf("crash-1-1.txt" to crashA, "crash-2-2.txt" to other))
        assertEquals(1, groups.size)
        assertTrue(groups.first().signature.contains("N"))
    }

    @Test
    fun analyzer_separatesDifferentCrashes() {
        val second = "java.lang.NullPointerException: toolsMessage\n\tat com.gsynk.app.ui.ToolsScreen.nav(ToolsScreen.kt:70)"
        val groups = CrashAnalyzer.group(listOf("crash-a" to crashA, "crash-b" to second))
        assertEquals(2, groups.size)
        assertTrue(CrashAnalyzer.verdict(groups).startsWith("Сбой:"))
    }

    @Test
    fun analyzer_emptyReports() {
        assertTrue(CrashAnalyzer.group(emptyList()).isEmpty())
        assertEquals("Сбоев не зафиксировано", CrashAnalyzer.verdict(emptyList()))
    }

    // ————— отчёты —————

    @Test
    fun sanitizer_hidesTokensAndNumbers() {
        val dirty = "токен $fakeToken и почта user@example.com, номер 89161234567"
        val clean = ReportSanitizer.scrub(dirty)
        assertFalse(clean.contains("ghp_"))
        assertFalse(clean.contains("user@example.com"))
        assertFalse(clean.contains("89161234567"))
        assertTrue(clean.contains("[токен скрыт]"))
    }

    @Test
    fun sanitizer_keepsOrdinaryText() {
        val text = "Правил: 3, активных: 2\nОстров: постоянный"
        assertEquals(text, ReportSanitizer.scrub(text))
    }

    @Test
    fun sanitizer_detectsSecrets() {
        assertTrue(ReportSanitizer.looksSecret(fakeToken))
        assertTrue(ReportSanitizer.looksSecret("письмо на a@b.ru"))
        assertFalse(ReportSanitizer.looksSecret("обычный текст отчёта"))
    }

    // ————— пароли —————

    @Test
    fun password_respectsLengthAndAlphabet() {
        val random = Random(7)
        val password = Passwords.generate(Passwords.Options(24, letters = true, digits = true, symbols = true), random)
        assertEquals(24, password.length)
        assertTrue(password.any { it.isDigit() } || password.any { !it.isLetterOrDigit() })
    }

    @Test
    fun password_strengthGrowsWithVariety() {
        assertTrue(Passwords.strength("aaaaaaaa") < Passwords.strength("A7#kLp2!Qz"))
        assertEquals(0, Passwords.strength(""))
    }
}
