package com.gsynk.app.core

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.app.NotificationCompat
import com.gsynk.app.GSYNKApp
import com.gsynk.app.ui.MainActivity
import com.gsynk.app.R

/**
 * Фоновая проверка обновлений.
 *
 * Зачем: пока приложение открыто, оно проверяет версию само раз в минуту. Но человек
 * не держит GSYNK открытым сутками — а обновление должно найти телефон и без этого.
 * Поэтому система сама будит приложение раз в полчаса, оно тихо спрашивает GitHub
 * и, если версия новая, показывает уведомление: нажал — открылось приложение и панель.
 *
 * Никакой своей службы, которая висит и ест батарею: используется JobScheduler —
 * он встроен в Android, умеет ждать сети и сам переживает перезагрузку телефона.
 */
class UpdateJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        // JobService работает в главном потоке — сеть уводим в отдельный поток.
        val thread = Thread {
            val ok = runCatching { UpdateJobs.runCheck(this) }.getOrDefault(false)
            // jobFinished(false) — задача сделана; true означало бы «повторите позже».
            jobFinished(params, !ok)
        }
        thread.name = "gsynk-update-check"
        thread.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}

/** Планирование фоновой проверки и сама проверка с уведомлением. */
object UpdateJobs {

    /** Номер задачи: один и тот же, поэтому повторный запуск не плодит копии. */
    const val JOB_ID = 4210

    /**
     * Как часто будить приложение. Android разрешает не чаще 15 минут; берём полчаса —
     * обновление всё равно попадается на глаза за день, а батарею это не трогает.
     */
    const val INTERVAL_MS = 30 * 60 * 1000L

    private const val NOTIFICATION_ID = 7301

    /** Пауза между фоновыми попытками скачать один и тот же APK. */
    const val AUTO_DOWNLOAD_RETRY_MS = 20 * 60 * 1000L

    /** Нужно ли вообще что-то показывать: не показываем то, что уже показали или пропустили. */
    fun shouldNotify(available: Boolean, tag: String, notifiedTag: String, skippedTag: String): Boolean {
        if (!available || tag.isBlank()) return false
        if (tag == notifiedTag) return false
        if (tag == skippedTag) return false
        return true
    }

    /**
     * Фон может сам скачать APK, но не должен жечь мобильный трафик и качать одну
     * версию бесконечно. Запуск системного установщика из фона Android не гарантирует,
     * поэтому готовый файл подхватит foreground при открытии приложения/уведомления.
     */
    fun shouldDownloadInBackground(
        available: Boolean,
        webOnly: Boolean,
        hasApk: Boolean,
        unmetered: Boolean,
        tag: String,
        skippedTag: String,
        lastDownloadTag: String,
        lastDownloadAt: Long,
        now: Long
    ): Boolean {
        if (!available || webOnly || !hasApk || !unmetered) return false
        if (tag.isBlank() || tag == skippedTag) return false
        if (tag != lastDownloadTag) return true
        return now - lastDownloadAt >= AUTO_DOWNLOAD_RETRY_MS
    }

    /** Поставить задачу в очередь системы: сеть обязательна, перезагрузка не страшна. */
    fun schedule(context: Context) {
        runCatching {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val component = ComponentName(context, UpdateJobService::class.java)
            val info = JobInfo.Builder(JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(INTERVAL_MS)
                .build()
            scheduler.schedule(info)
        }
    }

    /** Убрать задачу — например, если человек отключил автопроверку. */
    fun cancel(context: Context) {
        runCatching {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            scheduler.cancel(JOB_ID)
        }
    }

    /**
     * Одна фоновая проверка целиком: спросить GitHub, решить, стоит ли беспокоить
     * человека, и показать уведомление. Возвращает true, если проверка удалась.
     */
    fun runCheck(context: Context): Boolean {
        UpdateChecker.cleanupDownloadedUpdates(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val repo = prefs.getString(KEY_REPO, "").orEmpty().ifBlank { Keys.REPO }
        val auto = prefs.getBoolean(KEY_AUTO, true)
        if (!auto) return true

        val result = UpdateChecker.check(context, repo, Keys.REPORT_TOKEN)
        if (!result.ok) {
            AppLog.event("Обновление", "фоновая проверка не удалась: " + result.message)
            return false
        }
        val notified = prefs.getString(KEY_NOTIFIED, "").orEmpty()
        val skipped = prefs.getString(KEY_SKIPPED, "").orEmpty()

        var downloadedReady = false
        if (result.updateAvailable && result.latestTag != skipped) {
            downloadedReady = UpdateChecker.downloadedUpdate(
                context = context,
                expectedTag = result.latestTag,
                sha256 = result.sha256
            ) != null
            val now = System.currentTimeMillis()
            val shouldDownload = !downloadedReady && shouldDownloadInBackground(
                available = result.updateAvailable,
                webOnly = result.webOnly,
                hasApk = result.apkUrl.isNotBlank(),
                unmetered = isOnUnmeteredNetwork(context),
                tag = result.latestTag,
                skippedTag = skipped,
                lastDownloadTag = prefs.getString(KEY_BG_DOWNLOAD_TAG, "").orEmpty(),
                lastDownloadAt = prefs.getLong(KEY_BG_DOWNLOAD_AT, 0L),
                now = now
            )
            if (shouldDownload) {
                prefs.edit()
                    .putString(KEY_BG_DOWNLOAD_TAG, result.latestTag)
                    .putLong(KEY_BG_DOWNLOAD_AT, now)
                    .apply()
                val download = UpdateChecker.download(
                    context = context,
                    url = result.apkUrl,
                    expectedTag = result.latestTag,
                    sha256 = result.sha256
                )
                downloadedReady = download.file != null
                AppLog.event(
                    "Обновление",
                    if (downloadedReady) {
                        "APK " + result.latestTag + " скачан в фоне и ждёт подтверждения установки"
                    } else {
                        "фоновая загрузка " + result.latestTag + " не удалась: " + download.message
                    }
                )
            }
        }

        if (!shouldNotify(result.updateAvailable, result.latestTag, notified, skipped)) {
            return true
        }
        prefs.edit().putString(KEY_NOTIFIED, result.latestTag).apply()
        notify(context, result, downloadedReady)
        AppLog.event("Обновление", "фоновая проверка нашла " + result.latestTag)
        return true
    }

    private fun isOnUnmeteredNetwork(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching false
        val network = manager.activeNetwork ?: return@runCatching false
        val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }.getOrDefault(false)

    /** Настройки, которые ставит приложение: репозиторий и включена ли автопроверка. */
    fun remember(context: Context, repo: String, auto: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_REPO, repo).putBoolean(KEY_AUTO, auto).apply()
        if (auto) schedule(context) else cancel(context)
    }

    /** Человек пропустил версию в приложении — не напоминаем и уведомлением. */
    fun rememberSkipped(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SKIPPED, tag).apply()
    }

    /** Уведомление «вышла новая версия» — нажатие открывает приложение и панель обновления. */
    private fun notify(context: Context, result: UpdateChecker.Result, downloadedReady: Boolean = false) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_UPDATE, true)
        val pending = PendingIntent.getActivity(
            context,
            7,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = when {
            downloadedReady -> "APK " + result.latestTag + " уже скачан — нажмите, чтобы подтвердить установку"
            result.webOnly -> "Новая версия " + result.latestTag + " ждёт на странице релиза"
            else -> "Новая версия " + result.latestTag + " — нажмите, чтобы обновить"
        }
        val notification = NotificationCompat.Builder(context, GSYNKApp.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle("GSYNK: обновление " + result.latestTag)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + "\n" + result.notes.take(300)))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private const val PREFS = "gsynk_update"
    private const val KEY_REPO = "repo"
    private const val KEY_AUTO = "auto"
    private const val KEY_NOTIFIED = "notified_tag"
    private const val KEY_SKIPPED = "skipped_tag"
    private const val KEY_BG_DOWNLOAD_TAG = "bg_download_tag"
    private const val KEY_BG_DOWNLOAD_AT = "bg_download_at"
}
