package com.gsynk.app

import com.gsynk.app.core.DeviceProfile
import com.gsynk.app.core.NfcLink
import com.gsynk.app.core.ScheduledTask
import com.gsynk.app.core.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Логика мультитула: профиль устройства, версии для OTA, обмен по NFC, задачи.
 * Всё без обращения к Android — проверяется чистая логика.
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
        autoTier = tier,
        tier = tier,
        mainModeManual = false
    )

    // ————— профиль устройства —————

    @Test
    fun `полный режим разрешает все функции`() {
        val full = info(DeviceProfile.TIER_FULL)
        assertTrue(DeviceProfile.feature(full, DeviceProfile.Feature.RULES))
        assertTrue(DeviceProfile.feature(full, DeviceProfile.Feature.AI_CHAT))
        assertTrue(DeviceProfile.feature(full, DeviceProfile.Feature.PERSISTENT_ISLAND))
        assertTrue(DeviceProfile.feature(full, DeviceProfile.Feature.UPDATE))
        assertTrue(DeviceProfile.feature(full, DeviceProfile.Feature.TASKS))
    }

    @Test
    fun `базовый режим оставляет только яркость-громкость NFC игры фонарик и скринсейвер`() {
        val legacy = info(DeviceProfile.TIER_LEGACY, sdk = 33)
        assertTrue(DeviceProfile.feature(legacy, DeviceProfile.Feature.RULES))
        assertTrue(DeviceProfile.feature(legacy, DeviceProfile.Feature.NFC_LINK))
        assertTrue(DeviceProfile.feature(legacy, DeviceProfile.Feature.GAMES))
        assertTrue(DeviceProfile.feature(legacy, DeviceProfile.Feature.FLASHLIGHT))
        assertTrue(DeviceProfile.feature(legacy, DeviceProfile.Feature.SCREENSAVER))

        assertFalse(DeviceProfile.feature(legacy, DeviceProfile.Feature.DND))
        assertFalse(DeviceProfile.feature(legacy, DeviceProfile.Feature.TASKS))
        assertFalse(DeviceProfile.feature(legacy, DeviceProfile.Feature.PERSISTENT_ISLAND))
        assertFalse(DeviceProfile.feature(legacy, DeviceProfile.Feature.AI_CHAT))
        assertFalse(DeviceProfile.feature(legacy, DeviceProfile.Feature.GRAPE_UI))
        assertFalse(DeviceProfile.feature(legacy, DeviceProfile.Feature.UPDATE))
    }

    @Test
    fun `базовый набор не содержит полных функций`() {
        assertEquals(5, DeviceProfile.LEGACY_FEATURES.size)
        assertFalse(DeviceProfile.Feature.AI_CHAT in DeviceProfile.LEGACY_FEATURES)
        assertFalse(DeviceProfile.Feature.PERSISTENT_ISLAND in DeviceProfile.LEGACY_FEATURES)
        assertTrue(DeviceProfile.Feature.SCREENSAVER in DeviceProfile.LEGACY_FEATURES)
    }

    @Test
    fun `у каждой функции есть человеческое название`() {
        val ids = listOf(
            DeviceProfile.Feature.RULES, DeviceProfile.Feature.NFC_LINK, DeviceProfile.Feature.GAMES,
            DeviceProfile.Feature.FLASHLIGHT, DeviceProfile.Feature.DND, DeviceProfile.Feature.TASKS,
            DeviceProfile.Feature.PERSISTENT_ISLAND, DeviceProfile.Feature.AI_CHAT,
            DeviceProfile.Feature.GRAPE_UI, DeviceProfile.Feature.UPDATE,
            DeviceProfile.Feature.SCREENSAVER, DeviceProfile.Feature.SHIZUKU
        )
        ids.forEach { id ->
            assertNotNull("нет названия для $id", DeviceProfile.Feature.TITLES[id])
        }
        assertEquals(ids.size, DeviceProfile.Feature.TITLES.size)
    }

    @Test
    fun `подпись режима зависит от профиля`() {
        assertEquals("MAIN — все функции", info(DeviceProfile.TIER_FULL).summary)
        assertEquals("Базовый набор", info(DeviceProfile.TIER_LEGACY).summary)
        assertTrue(info(DeviceProfile.TIER_FULL).isFull)
        assertFalse(info(DeviceProfile.TIER_LEGACY).isFull)
    }

    // ————— OTA: сравнение версий —————

    @Test
    fun `одинаковые версии не требуют обновления`() {
        assertEquals(0, UpdateChecker.compareVersions("1.2.0", "1.2.0"))
        assertEquals(0, UpdateChecker.compareVersions("v2.0", "2.0.0"))
        assertEquals(0, UpdateChecker.compareVersions("2.0.0", "v2.0.0"))
    }

    @Test
    fun `новая версия определяется любой длиной`() {
        assertTrue(UpdateChecker.compareVersions("1.3", "1.2.9") > 0)
        assertTrue(UpdateChecker.compareVersions("2.0.0", "1.9.9") > 0)
        assertTrue(UpdateChecker.compareVersions("1.2.1", "1.2") > 0)
        assertTrue(UpdateChecker.compareVersions("v2.1.0", "2.0.9") > 0)
    }

    @Test
    fun `старая версия и суффиксы читаются верно`() {
        assertTrue(UpdateChecker.compareVersions("1.2", "1.2.1") < 0)
        assertTrue(UpdateChecker.compareVersions("1.1.9", "1.2.0") < 0)
        // суффикс сборки не мешает: 2.0.0-beta = 2.0.0
        assertEquals(0, UpdateChecker.compareVersions("2.0.0-beta", "2.0.0"))
        assertEquals(1, UpdateChecker.compareVersions("2.0.1-rc1", "2.0.0"))
    }

    @Test
    fun `мусор вместо версии не ломает сравнение`() {
        assertEquals(0, UpdateChecker.compareVersions("", ""))
        assertTrue(UpdateChecker.compareVersions("2.0", "версия") > 0)
    }

    // ————— NFC-обмен —————

    @Test
    fun `вызов в игру разбирается на поля`() {
        val invite = NfcLink.parseGameInvite("плитки-tap;Poco X3 Pro;12345")
        assertNotNull(invite)
        assertEquals("плитки-tap", invite!!.game)
        assertEquals("Poco X3 Pro", invite.from)
        assertEquals(12345L, invite.seed)
    }

    @Test
    fun `вызов без счёта получает текущее время вместо падения`() {
        val invite = NfcLink.parseGameInvite("дуэль;Олег")
        assertNotNull(invite)
        assertEquals("дуэль", invite!!.game)
        assertEquals("Олег", invite.from)
        assertTrue(invite.seed > 0L)
    }

    @Test
    fun `пустая нагрузка не роняет разбор`() {
        val invite = NfcLink.parseGameInvite("")
        assertNotNull(invite)
        assertEquals("", invite!!.game)
        assertTrue(invite.seed > 0L)
    }

    @Test
    fun `типы полезной нагрузки различимы`() {
        assertEquals("text", NfcLink.KIND_TEXT)
        assertEquals("url", NfcLink.KIND_URL)
        assertEquals("contact", NfcLink.KIND_CONTACT)
        assertEquals("game", NfcLink.KIND_GAME)
        assertEquals("application/vnd.gsynk.game", NfcLink.MIME_GAME)
    }

    // ————— задачи —————

    @Test
    fun `у каждого вида задачи есть название`() {
        assertEquals("Звонок + озвучка", ScheduledTask.KIND_TITLES[ScheduledTask.KIND_CALL])
        assertEquals("Озвучить фразу", ScheduledTask.KIND_TITLES[ScheduledTask.KIND_SPEAK])
        assertEquals("Фейковый звонок", ScheduledTask.KIND_TITLES[ScheduledTask.KIND_FAKE_CALL])
        assertEquals(3, ScheduledTask.KIND_TITLES.size)
    }

    @Test
    fun `новая задача по умолчанию через минуту и включена`() {
        val before = System.currentTimeMillis()
        val task = ScheduledTask()
        assertTrue(task.enabled)
        assertTrue(task.triggerAt >= before + 55_000L)
        assertTrue(task.triggerAt <= before + 65_000L)
        assertEquals(ScheduledTask.KIND_SPEAK, task.kind)
        assertTrue(task.id.isNotBlank())
    }

    @Test
    fun `задержка озвучки по умолчанию безопасна`() {
        val task = ScheduledTask()
        assertTrue("пауза перед речью должна быть не меньше 6 секунд", task.speakDelayMs >= 6_000L)
    }
}
