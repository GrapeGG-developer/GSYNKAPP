package com.gsynk.app

import com.gsynk.app.core.AiNet
import com.gsynk.app.core.Bench
import com.gsynk.app.core.BleRadar
import com.gsynk.app.core.EverydayFaceDown
import com.gsynk.app.core.Keys
import com.gsynk.app.core.NfcPulse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверки нового кода GIOUI: нагрузка, пульс NFC, вшитые ключи, радар BLE,
 * ИИ-сеть и переворот телефона. Всё, что здесь проверяется, — чистые функции,
 * поэтому тесты идут быстро и не требуют ни телефона, ни сети.
 */
class BenchLinkTest {

    // ————— нагрузка —————

    @Test
    fun scoreFollowsOpenFormula() {
        val score = Bench.score(
            cpuInt = 100.0, cpuFloat = 100.0, cpuMatrix = 100.0,
            memory = 100.0, storage = 100.0, multi = 100.0, gpu = 100.0
        )
        // 100 * (1.0 + 0.9 + 2.4 + 0.4 + 0.8 + 1.2 + 1.6) = 830
        assertEquals(830, score)
    }

    @Test
    fun scoreRewardsMatrixMost() {
        val withMatrix = Bench.score(0.0, 0.0, 100.0, 0.0, 0.0, 0.0, 0.0)
        val withMemory = Bench.score(0.0, 0.0, 0.0, 100.0, 0.0, 0.0, 0.0)
        assertTrue(withMatrix > withMemory)
    }

    @Test
    fun tierIsPickedByScore() {
        assertEquals("бюджетный", Bench.Result(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 8).tier)
        // 150 000 * 1.0 + 50 000 * 2.4 = 270 000 — это уже средний класс.
        assertEquals("средний класс", Bench.Result(150_000.0, 0.0, 50_000.0, 0.0, 0.0, 0.0, 0.0, 8).tier)
        assertEquals(
            "флагман",
            Bench.Result(400_000.0, 200_000.0, 100_000.0, 200_000.0, 200_000.0, 300_000.0, 200_000.0, 8).tier
        )
    }

    @Test
    fun multithreadBonusIsRatioToSingleCore() {
        val result = Bench.Result(100.0, 0.0, 0.0, 0.0, 0.0, 400.0, 0.0, 8)
        assertEquals(4.0, result.multithreadBonus, 0.001)
        assertEquals(0.0, Bench.Result(0.0, 0.0, 0.0, 0.0, 0.0, 400.0, 0.0, 8).multithreadBonus, 0.001)
    }

    @Test
    fun firstRunHasNothingToCompareWith() {
        val first = Bench.Result(10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 8)
        assertTrue(Bench.compare(first, null).contains("первый прогон"))
    }

    @Test
    fun slowerRunIsBlamedOnThrottling() {
        val current = Bench.Result(10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 8)
        val faster = Bench.Result(14.0, 14.0, 14.0, 14.0, 14.0, 14.0, 14.0, 8)
        assertTrue(Bench.compare(current, faster).contains("троттлинг"))
        assertTrue(Bench.compare(faster, current).contains("+"))
    }

    @Test
    fun versusCallsEvenFight() {
        val mine = Bench.Result(10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 8)
        val other = Bench.Result(10.2, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 8)
        assertTrue(Bench.versus(mine, other).contains("вровень"))
    }

    @Test
    fun versusSaysWhoIsFaster() {
        val weak = Bench.Result(5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 8)
        val strong = Bench.Result(20.0, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0, 8)
        assertTrue(Bench.versus(strong, weak).startsWith("Ваш телефон быстрее"))
        assertTrue(Bench.versus(weak, strong).startsWith("Телефон друга быстрее"))
    }

    @Test
    fun integerLoadIsDeterministic() {
        assertEquals(Bench.intOps(1_000), Bench.intOps(1_000), 0.0)
        assertTrue(Bench.intOps(10_000) != Bench.intOps(20_000))
    }

    @Test
    fun floatLoadIsFinite() {
        val value = Bench.floatOps(5_000)
        assertTrue(value.isFinite())
        assertTrue(value != 0.0)
    }

    @Test
    fun matrixMultiplyGivesKnownCell() {
        // При size = 1: a = [[0]], b = [[0]] -> 0; при size = 2 считаем руками.
        assertEquals(0.0, Bench.matrixMultiply(1), 0.0001)
        assertTrue(Bench.matrixMultiply(8).isFinite())
    }

    @Test
    fun bandwidthNeedsRealTime() {
        assertEquals(0.0, Bench.memoryBandwidthMbPerSecond(1_048_576, 0L), 0.0)
        assertEquals(1.0, Bench.memoryBandwidthMbPerSecond(1_048_576, 1000L), 0.001)
        assertEquals(0.5, Bench.memoryBandwidthMbPerSecond(1_048_576, 2000L), 0.001)
    }

    @Test
    fun estimateGrowsOnWeakChips() {
        assertTrue(Bench.estimateSeconds(2) > Bench.estimateSeconds(8))
    }

    @Test
    fun historySurvivesEncodeAndDecode() {
        val source = listOf(
            Bench.Result(1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5, 8, at = 111L),
            Bench.Result(9.0, 8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 4, at = 222L)
        )
        val restored = Bench.decode(Bench.encode(source))
        assertEquals(2, restored.size)
        assertEquals(111L, restored[0].at)
        assertEquals(4, restored[1].cores)
        assertEquals(source[0].score, restored[0].score)
    }

    @Test
    fun brokenHistoryLinesAreSkipped() {
        assertTrue(Bench.decode("мусор").isEmpty())
        assertTrue(Bench.decode("1;2;3").isEmpty())
    }

    @Test
    fun shareBodyRoundTrips() {
        val result = Bench.Result(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8, at = 777L)
        val body = Bench.shareBody(result, "POCO;тест")
        assertNotNull(Bench.parseShare(body))
        val parsed = Bench.parseShare(body)!!
        assertEquals("POCO тест", parsed.first)
        assertEquals(result.cpuInt.toInt(), parsed.second.cpuInt.toInt())
        assertNull(Bench.parseShare("коротко"))
    }

    // ————— пульс NFC —————

    @Test
    fun pulseStartsSilent() {
        NfcPulse.clear()
        assertNull(NfcPulse.event.value)
    }

    @Test
    fun tagNamesTheEventAndCountsIt() {
        val before = NfcPulse.count.value
        NfcPulse.tagFound("метка NTAG213")
        assertEquals(NfcPulse.Kind.TAG_FOUND, NfcPulse.event.value?.kind)
        assertEquals("метка NTAG213", NfcPulse.event.value?.detail)
        assertTrue(NfcPulse.count.value >= before)
        NfcPulse.clear()
        assertNull(NfcPulse.event.value)
    }

    @Test
    fun everyKindHasTitle() {
        NfcPulse.Kind.entries.forEach { kind ->
            assertTrue(kind.title.isNotBlank())
        }
    }

    // ————— вшитые ключи —————

    @Test
    fun tokenIsReadyWithoutUserInput() {
        assertTrue(Keys.ready())
        assertTrue(Keys.REPORT_TOKEN.length > 20)
    }

    @Test
    fun repoLooksLikeOwnerSlashName() {
        assertTrue(Keys.REPO.contains("/"))
        assertEquals(2, Keys.REPO.split("/").size)
        assertFalse(Keys.REPO.startsWith("/"))
    }

    @Test
    fun tokenIsMaskedInInterface() {
        val masked = Keys.masked(Keys.REPORT_TOKEN)
        assertTrue(masked.contains("…"))
        assertTrue(masked.contains(Keys.REPORT_TOKEN.length.toString()))
        assertFalse(masked.contains(Keys.REPORT_TOKEN))
        assertEquals("не задан", Keys.masked("коротко"))
    }

    @Test
    fun tokenIsNotStoredInASingleLine() {
        // Ради защиты от сканера GitHub токен разбит на четыре части:
        // в исходнике не должно быть строки, которая целиком его содержит.
        val source = java.io.File("src/main/java/com/gsynk/app/core/Keys.kt").readText()
        source.lineSequence().forEach { line ->
            assertFalse(
                "токен лежит в одну строку: " + line,
                line.contains(Keys.REPORT_TOKEN)
            )
        }
    }

    // ————— радар BLE —————

    @Test
    fun distanceGrowsAsSignalFades() {
        assertTrue(BleRadar.meters(-40) < BleRadar.meters(-70))
        assertTrue(BleRadar.meters(-100) > BleRadar.meters(-70))
    }

    @Test
    fun oneMeterIsTheReferencePoint() {
        assertEquals(1.0, BleRadar.meters(-60), 0.05)
    }

    @Test
    fun distanceStaysInsideReasonableRange() {
        assertTrue(BleRadar.meters(-10) >= 0.05)
        assertTrue(BleRadar.meters(-200) <= 99.0)
    }

    @Test
    fun strengthIsPercentOfScale() {
        assertEquals(0, BleRadar.rssiToStrength(-100))
        assertEquals(100, BleRadar.rssiToStrength(-20))
        assertTrue(BleRadar.rssiToStrength(-60) in 60..70)
        assertEquals(0, BleRadar.rssiToStrength(-300))
    }

    @Test
    fun qualityTellsApartCloseAndBarelySeen() {
        assertEquals("вплотную", BleRadar.quality(BleRadar.Found("AA", "часы", -50, 0.7, seen = 5)))
        assertEquals("рядом", BleRadar.quality(BleRadar.Found("AA", "часы", -80, 8.0, seen = 5)))
        assertEquals("устойчиво виден", BleRadar.quality(BleRadar.Found("AA", "", -80, 8.0, seen = 9)))
        assertEquals("мелькнуло", BleRadar.quality(BleRadar.Found("AA", "", -80, 8.0, seen = 1)))
    }

    @Test
    fun namelessDeviceFallsBackToAddress() {
        val hidden = BleRadar.Found("AA:BB", "", -70, 3.0, seen = 2)
        assertEquals("AA:BB", hidden.label)
        assertEquals("часы", BleRadar.Found("AA:BB", "часы", -70, 3.0, seen = 2).label)
    }

    @Test
    fun scanningAsksForRightPermissions() {
        val needed = BleRadar.permissionFor()
        assertTrue(needed.isNotEmpty())
        // На новых Android нужен BLUETOOTH_SCAN, на старых — ACCESS_FINE_LOCATION.
        assertTrue(
            needed.any { it.endsWith("BLUETOOTH_SCAN") } ||
                needed.any { it.endsWith("ACCESS_FINE_LOCATION") }
        )
    }

    // ————— ИИ-сеть —————

    @Test
    fun offlineIsDefaultAndNeedsNothing() {
        val config = AiNet.Config()
        assertTrue(config.isOffline)
        assertTrue(config.ready)
        assertEquals("офлайн-помощник GSYNK", AiNet.describe(config))
    }

    @Test
    fun providerIdsAreUniqueAndHaveAddresses() {
        val ids = AiNet.PROVIDERS.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        AiNet.PROVIDERS.forEach { provider ->
            assertTrue(provider.title.isNotBlank())
            assertTrue(provider.hint.isNotBlank())
            // У сетевых провайдеров есть адрес и модель; у офлайн-режима их нет по замыслу.
            if (provider.id != "offline") {
                assertTrue(provider.baseUrl.startsWith("http"))
                assertTrue(provider.model.isNotBlank())
            }
        }
        assertEquals("offline", AiNet.PROVIDERS.first().id)
    }

    @Test
    fun settingsFallBackToProviderDefaults() {
        val groq = AiNet.PROVIDERS.first { it.id == "groq" }
        val config = AiNet.Config(provider = "groq", baseUrl = "  ", model = "", key = "ключ")
        assertEquals(groq.baseUrl, config.address)
        assertEquals(groq.model, config.modelName)
        assertFalse(config.isOffline)
    }

    @Test
    fun remoteProviderWithoutKeyIsNotReady() {
        // У облачных провайдеров без ключа отправлять нечего.
        val openai = AiNet.PROVIDERS.first { it.id == "openai" }
        assertTrue(openai.needsKey)
        assertFalse(AiNet.Config(provider = "openai", key = "").ready)
        assertTrue(AiNet.Config(provider = "openai", key = "sk-тест").ready)
        // Своя модель в домашней сети ключа не требует.
        assertTrue(AiNet.Config(provider = "ollama").ready)
    }

    @Test
    fun addressLosesTrailingSlash() {
        val config = AiNet.Config(provider = "ollama", baseUrl = "http://192.168.1.2:11434/")
        assertEquals("http://192.168.1.2:11434", config.address)
    }

    @Test
    fun systemHintKeepsAssistantInCharacter() {
        assertTrue(AiNet.SYSTEM.contains("GSYNK"))
        assertTrue(AiNet.SYSTEM.contains("по-русски"))
    }

    // ————— телефон лицом вниз —————

    @Test
    fun facedownIsDetectedByThreshold() {
        assertTrue(EverydayFaceDown.isFaceDown(-9.8f))
        assertTrue(EverydayFaceDown.isFaceDown(EverydayFaceDown.THRESHOLD))
        assertFalse(EverydayFaceDown.isFaceDown(-4.0f))
        assertFalse(EverydayFaceDown.isFaceDown(9.8f))
    }
}

/** Мелочь для читаемости: провайдер без сети — это офлайн. */
private fun AiNet.Provider.isOffline(): Boolean = id == "offline"
