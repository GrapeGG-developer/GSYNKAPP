package com.gsynk.app.core

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gsynk.app.GSYNKApp
import com.gsynk.app.R
import com.gsynk.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Постоянный остров: живёт в шторке-фоне и держит капсулу рядом с фронтальной камерой.
 * Показывает часы, заряд и ближайшее правило; по касанию раскрывается.
 *
 * Это ровно та же технология, что и у системного Dynamic Island HyperOS —
 * системный остров сторонним приложениям недоступен, поэтому GSYNK рисует свой.
 */
class IslandService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var lastPosition = IslandPosition.TOP

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val foregroundStarted = runCatching { startForegroundCompat() }.isSuccess
        if (!foregroundStarted) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!IslandOverlay.isAvailable(this)) {
            // без разрешения «поверх других приложений» остров показать нельзя
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY
    }

    private fun refresh() {
        scope.launch {
            try {
                val repository = RuleRepository(applicationContext)
                val settings = repository.currentSettings()
                if (!settings.islandPersistent) {
                    stopSelf()
                    return@launch
                }
                val rules = repository.currentRules()
                lastPosition = settings.islandPosition

                val now = ZonedDateTime.now()
                val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
                val battery = batteryPercent()

                val next = rules.filter { it.enabled }
                    .mapNotNull { rule -> RuleScheduler.nextOccurrence(rule, now)?.let { it to rule } }
                    .minByOrNull { it.first }

                val subtitle = buildString {
                    append("🔋 ").append(battery).append('%')
                    if (next != null) {
                        append(" • ")
                        append(next.first.format(DateTimeFormatter.ofPattern("HH:mm")))
                        append(' ')
                        append(next.second.name)
                    } else {
                        append(" • правил нет")
                    }
                }
                val expanded = buildString {
                    append("Автопилот ")
                    append(if (settings.autopilot) "вкл" else "выкл")
                    append(" • правил: ").append(rules.size)
                    append(" • обновлено ")
                    append(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                }

                handler.post {
                    if (IslandOverlay.isShownPersistent()) {
                        IslandOverlay.updatePersistent(applicationContext, time, subtitle, expanded, lastPosition)
                    } else {
                        IslandOverlay.showPersistent(applicationContext, time, subtitle, expanded, lastPosition)
                    }
                }
            } catch (_: Throwable) {
                // тихо: сервис не должен падать
            }
        }
    }

    private fun batteryPercent(): Int = runCatching {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (level < 0 || scale <= 0) 0 else level * 100 / scale
    }.getOrDefault(0)

    private fun startForegroundCompat() {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, GSYNKApp.CHANNEL_APPLY)
            .setSmallIcon(R.drawable.ic_stat_gsynk)
            .setContentTitle(getString(R.string.notif_island_persistent))
            .setContentText(getString(R.string.notif_island_persistent_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        IslandOverlay.hidePersistent()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1003
        private const val REFRESH_MS = 20_000L

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, IslandService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, IslandService::class.java)) }
            IslandOverlay.hidePersistent()
        }
    }
}
