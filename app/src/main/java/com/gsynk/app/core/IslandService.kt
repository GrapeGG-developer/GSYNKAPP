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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Постоянный остров: держит компактную капсулу у фронтальной камеры.
 * Показывает выбранную в настройках информацию и обновляется раз в 15 секунд.
 */
class IslandService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var lastPosition = IslandPosition.TOP_CENTER

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (runCatching { startForegroundCompat() }.isFailure) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!IslandOverlay.isAvailable(this)) {
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

                val widgets = settings.islandWidgets
                val now = ZonedDateTime.now()

                val title = buildList {
                    if (IslandWidget.CLOCK in widgets) {
                        add(now.format(DateTimeFormatter.ofPattern("HH:mm")))
                    }
                    if (IslandWidget.BATTERY in widgets) {
                        val battery = batteryPercent()
                        add("$battery%")
                        if (battery in 1..20) add("⚠")
                    }
                }.joinToString("  ").ifBlank { "GSYNK" }

                val rules = repository.currentRules()
                val next = rules.asSequence()
                    .mapNotNull { rule -> RuleScheduler.nextOccurrence(rule, now)?.let { time -> time to rule } }
                    .minByOrNull { pair -> pair.first }

                val subtitle = buildList {
                    if (IslandWidget.NEXT_RULE in widgets) {
                        val upcoming = next
                        if (upcoming != null) {
                            add(
                                upcoming.first.format(DateTimeFormatter.ofPattern("HH:mm")) + " " +
                                    upcoming.second.name.take(14)
                            )
                        } else {
                            add("правил нет")
                        }
                    }
                    if (IslandWidget.NFC_COUNT in widgets) add("NFC ${settings.nfcTouches}")
                    if (IslandWidget.RECORD in widgets) {
                        val record = GameScores(applicationContext).snake.first()
                        if (record > 0) add("рекорд $record")
                    }
                }.joinToString("  ").ifBlank { if (settings.autopilot) "автопилот" else "пауза" }

                val expanded = buildString {
                    append(DeviceProfile.detect(this@IslandService).model.ifEmpty { "GSYNK" })
                    append(" • Android ").append(Build.VERSION.RELEASE)
                    append(" • правил: ").append(rules.size)
                    append(" • ").append(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                }

                lastPosition = settings.islandPosition
                handler.post {
                    if (IslandOverlay.isShownPersistent()) {
                        IslandOverlay.updatePersistent(
                            applicationContext, title, subtitle, expanded, lastPosition,
                            settings.islandCompact,
                            settings.islandOffsetY,
                            settings.islandWide
                        )
                    } else {
                        IslandOverlay.showPersistent(
                            applicationContext, title, subtitle, expanded, lastPosition,
                            settings.islandCompact, settings.islandOffsetY, settings.islandWide
                        )
                    }
                }
            } catch (_: Throwable) {
                // сервис не должен падать
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
        private const val REFRESH_MS = 15_000L

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
