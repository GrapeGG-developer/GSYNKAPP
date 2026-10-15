package com.gsynk.app

import com.gsynk.app.core.UpdateChecker
import com.gsynk.app.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Обновления: проверяем правила, на которых держится «обновление приходит само».
 *
 * Здесь три темы:
 *  1. Разбор настоящего ответа GitHub (включая случай, когда APK к релизу не приложен
 *     и когда версия видна только по странице релиза).
 *  2. Политика напоминаний — когда панель обязана открыться сама, а когда не должна мешать.
 *  3. Паузы между проверками: без токена приложение упиралось в лимит GitHub,
 *     поэтому расчёт паузы теперь отдельная, проверяемая функция.
 */
class UpdateFlowTest {

    private val releaseJson = """
        {
          "tag_name": "v4.0.3",
          "name": "GSYNK 4.0.3",
          "body": "Обновления, которые приходят сами",
          "html_url": "https://github.com/GrapeGG-developer/GSYNKAPP/releases/tag/v4.0.3",
          "assets": [
            {
              "name": "GSYNK-v4.0.3-debug.apk",
              "browser_download_url": "https://github.com/GrapeGG-developer/GSYNKAPP/releases/download/v4.0.3/GSYNK-v4.0.3-debug.apk",
              "size": 37600000,
              "digest": "sha256:0b33f0de7fad381b27224c3a1234567890abcdef1234567890abcdef12345678"
            }
          ]
        }
    """.trimIndent()

    private val releaseWithoutApk = """
        {
          "tag_name": "v4.0.3",
          "body": "без файла",
          "html_url": "https://github.com/GrapeGG-developer/GSYNKAPP/releases/tag/v4.0.3",
          "assets": []
        }
    """.trimIndent()

    // ————— разбор ответа GitHub —————

    @Test
    fun newerReleaseIsRecognizedWithItsApk() {
        val result = UpdateChecker.parse(releaseJson, "4.0.2")
        assertTrue(result.ok)
        assertTrue(result.updateAvailable)
        assertEquals("v4.0.3", result.latestTag)
        assertTrue(result.apkUrl.endsWith(".apk"))
        assertTrue(result.sha256.startsWith("0b33f0de"))
        assertFalse(result.webOnly)
    }

    @Test
    fun releaseWithoutApkDoesNotOfferDownload() {
        val result = UpdateChecker.parse(releaseWithoutApk, "4.0.2")
        // Версия новее, но качать нечего: обещать установку нельзя.
        assertFalse(result.updateAvailable)
        assertTrue(result.message.contains("APK не прикреплён"))
        assertTrue(result.apkUrl.isBlank())
    }

    @Test
    fun sameVersionMeansNoUpdate() {
        val result = UpdateChecker.parse(releaseJson, "4.0.3")
        assertFalse(result.updateAvailable)
        assertEquals("Установлена последняя версия", result.message)
    }

    @Test
    fun notesAreKeptForTheSheet() {
        val result = UpdateChecker.parse(releaseJson, "4.0.0")
        assertTrue(result.notes.contains("приходят сами"))
        assertTrue(result.pageUrl.contains("/releases/tag/"))
    }

    // ————— сравнение версий —————

    @Test
    fun versionsCompareByParts() {
        assertTrue(UpdateChecker.compareVersions("v4.0.3", "4.0.2") > 0)
        assertTrue(UpdateChecker.compareVersions("4.0.2", "v4.0.2") == 0)
        assertTrue(UpdateChecker.compareVersions("4.0", "4.0.1") < 0)
        assertTrue(UpdateChecker.compareVersions("4.1", "4.0.9") > 0)
        assertTrue(UpdateChecker.compareVersions("4.0.3-beta", "4.0.3") == 0)
    }

    @Test
    fun brokenVersionNeverClaimsAnUpdate() {
        assertTrue(UpdateChecker.compareVersions("мусор", "4.0.2") < 0)
        assertTrue(UpdateChecker.compareVersions("", "4.0.2") < 0)
    }

    // ————— политика напоминаний —————

    private val now = 1_000_000_000L

    @Test
    fun panelAsksItselfWhenUpdateIsWaiting() {
        assertTrue(
            MainViewModel.UpdatePolicy.shouldPrompt(
                available = true, acked = false, postponedUntil = 0L,
                lastPromptAt = now - 21 * 60_000L, now = now
            )
        )
    }

    @Test
    fun panelKeepsQuietRightAfterShowing() {
        assertFalse(
            MainViewModel.UpdatePolicy.shouldPrompt(
                available = true, acked = false, postponedUntil = 0L,
                lastPromptAt = now - 30_000L, now = now
            )
        )
    }

    @Test
    fun postponedPanelWaits() {
        assertFalse(
            MainViewModel.UpdatePolicy.shouldPrompt(
                available = true, acked = false,
                postponedUntil = now + 5 * 60_000L,
                lastPromptAt = 0L, now = now
            )
        )
        assertTrue(
            MainViewModel.UpdatePolicy.shouldPrompt(
                available = true, acked = false,
                postponedUntil = now - 1L,
                lastPromptAt = 0L, now = now
            )
        )
    }

    @Test
    fun skippedVersionNeverInterruptsAgain() {
        // Это главное отличие нового поведения: «пропустить» уважается, а обычное
        // закрытие панели — нет, и напоминания продолжаются.
        assertFalse(
            MainViewModel.UpdatePolicy.shouldPrompt(
                available = true, acked = true, postponedUntil = 0L, lastPromptAt = 0L, now = now
            )
        )
        assertFalse(
            MainViewModel.UpdatePolicy.shouldRemind(available = true, acked = true, lastCapsuleAt = 0L, now = now)
        )
    }

    @Test
    fun capsuleRemindsLessOftenThanPanel() {
        assertFalse(
            MainViewModel.UpdatePolicy.shouldRemind(
                available = true, acked = false, lastCapsuleAt = now - 5 * 60_000L, now = now
            )
        )
        assertTrue(
            MainViewModel.UpdatePolicy.shouldRemind(
                available = true, acked = false, lastCapsuleAt = now - 16 * 60_000L, now = now
            )
        )
    }

    @Test
    fun nothingIsPromptedWhenThereIsNoUpdate() {
        assertFalse(
            MainViewModel.UpdatePolicy.shouldPrompt(
                available = false, acked = false, postponedUntil = 0L, lastPromptAt = 0L, now = now
            )
        )
    }

    // ————— паузы между проверками —————

    @Test
    fun healthyCheckRunsEveryMinute() {
        assertEquals(60_000L, MainViewModel.UpdatePolicy.nextDelayMs(0, 0L, now))
    }

    @Test
    fun failuresBackOffStepByStep() {
        assertEquals(2 * 60_000L, MainViewModel.UpdatePolicy.nextDelayMs(1, 0L, now))
        assertEquals(5 * 60_000L, MainViewModel.UpdatePolicy.nextDelayMs(2, 0L, now))
        assertEquals(10 * 60_000L, MainViewModel.UpdatePolicy.nextDelayMs(5, 0L, now))
    }

    @Test
    fun rateLimitIsRespected() {
        val reset = now + 12 * 60_000L
        val delay = MainViewModel.UpdatePolicy.nextDelayMs(3, reset, now)
        assertTrue(delay > 12 * 60_000L)
        assertTrue(delay <= 30 * 60_000L + 5_000L)
        // Срок прошёл — возвращаемся к обычному кругу.
        assertEquals(60_000L, MainViewModel.UpdatePolicy.nextDelayMs(0, now - 1L, now))
    }

    @Test
    fun statusLineTellsWhatHappened() {
        assertEquals("ещё не проверял", MainViewModel.UpdatePolicy.describe(0L, now, now))
        assertEquals(
            "проверено 30 с назад · следующая через 5 мин",
            MainViewModel.UpdatePolicy.describe(now - 30_000L, now + 5 * 60_000L, now)
        )
        assertTrue(
            MainViewModel.UpdatePolicy.describe(now - 2 * 60 * 60_000L, now + 40 * 60_000L, now)
                .contains("2 ч")
        )
    }

    // ————— фоновая проверка —————

    @Test
    fun backgroundCheckWaitsHalfAnHour() {
        // Android не разрешает чаще 15 минут; полчаса — компромисс между свежестью и батареей.
        assertEquals(30 * 60 * 1000L, com.gsynk.app.core.UpdateJobs.INTERVAL_MS)
        assertTrue(com.gsynk.app.core.UpdateJobs.INTERVAL_MS >= 15 * 60 * 1000L)
    }

    @Test
    fun notificationComesOnlyOncePerVersion() {
        val jobs = com.gsynk.app.core.UpdateJobs
        assertTrue(jobs.shouldNotify(available = true, tag = "v4.0.3", notifiedTag = "", skippedTag = ""))
        // Второй раз о той же версии не сообщаем: человека нельзя дёргать одинаково.
        assertFalse(jobs.shouldNotify(available = true, tag = "v4.0.3", notifiedTag = "v4.0.3", skippedTag = ""))
        // Версию пропустили вручную — уведомление тоже молчит.
        assertFalse(jobs.shouldNotify(available = true, tag = "v4.0.3", notifiedTag = "", skippedTag = "v4.0.3"))
        // Новая версия после пропущенной — снова сообщаем.
        assertTrue(jobs.shouldNotify(available = true, tag = "v4.0.4", notifiedTag = "v4.0.3", skippedTag = "v4.0.3"))
        assertFalse(jobs.shouldNotify(available = false, tag = "v4.0.3", notifiedTag = "", skippedTag = ""))
        assertFalse(jobs.shouldNotify(available = true, tag = "", notifiedTag = "", skippedTag = ""))
    }

    @Test
    fun notificationOpensTheUpdatePanel() {
        // Нажатие на уведомление обязано открывать панель: иначе непонятно, куда идти.
        val activity = java.io.File("src/main/java/com/gsynk/app/ui/MainActivity.kt").readText()
        assertTrue(activity.contains("EXTRA_OPEN_UPDATE"))
        assertTrue(activity.contains("Router.go(\"panel:update\")"))
        val service = java.io.File("src/main/java/com/gsynk/app/core/UpdateJobService.kt").readText()
        assertTrue(service.contains("putExtra(MainActivity.EXTRA_OPEN_UPDATE, true)"))
        // Задача обязана переживать перезагрузку телефона и ждать сети.
        assertTrue(service.contains("setPersisted(true)"))
        assertTrue(service.contains("NETWORK_TYPE_ANY"))
    }

    @Test
    fun serviceAndScheduleAreDeclared() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("UpdateJobService"))
        assertTrue(manifest.contains("BIND_JOB_SERVICE"))
        val app = java.io.File("src/main/java/com/gsynk/app/GSYNKApp.kt").readText()
        assertTrue(app.contains("UpdateJobs.schedule(this)"))
    }

    // ————— сверка скачанного файла —————

    @Test
    fun digestIsComputedTheSameWayAsGitHub() {
        // Считаем тот же sha256, которым проверяется скачанный APK, на известном тексте.
        val file = java.io.File.createTempFile("gsynk-digest", ".txt")
        file.writeText("GSYNK")
        val digest = UpdateChecker.digestOf(file)
        file.delete()
        assertEquals("4452290fef41a2e4d9d4a72a4720069c3b4e5e9feafcad06f3103fc4e0159e44", digest)
        assertEquals(64, digest.length)
    }
}
