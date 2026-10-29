package com.gsynk.app.core

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gsynk.app.GSYNKApp
import com.gsynk.app.R
import com.gsynk.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Служба применения правила.
 *
 * Foreground-служба типа specialUse — единственный способ гарантированно менять
 * системную громкость из фона (в Android 17 фоновые вызовы игнорируются).
 * Записывает событие в журнал и пересобирает расписание.
 */
class ApplyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ruleId = intent?.getStringExtra(EXTRA_RULE_ID)
        val foregroundStarted = runCatching { startForegroundCompat() }.isSuccess

        scope.launch {
            try {
                val repository = RuleRepository(applicationContext)
                val settings = repository.currentSettings()
                val rules = repository.currentRules()

                // «Уведомления» выключены — сразу прячем уведомление из шторки
                if (!settings.notifications && foregroundStarted) {
                    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                }

                if (settings.autopilot) {
                    val rule = rules.firstOrNull { it.id == ruleId }
                    if (rule != null) {
                        val result = RuleEngine.apply(applicationContext, rule)
                        // пишем в журнал: из этих записей собирается «Хроника дня»
                        AppLog.event(
                            "Правило",
                            "«${rule.name}»" +
                                (if (result.brightness) ", яркость ${rule.brightness}%" else "") +
                                (if (result.volume) ", звук ${rule.volume}%" else "") +
                                (if (result.silent) (if (rule.silentMode == 1) ", тишина включена" else ", тишина выключена") else "")
                        )
                        // разовое правило (с конкретными датами) отработало — выключаем его,
                        // чтобы оно не срабатывало в эти же дни недели каждую неделю
                        if (rule.isOneTime) {
                            val updated = rules.map { if (it.id == rule.id) it.copy(enabled = false) else it }
                            repository.saveRules(updated)
                        }
                    }
                    RuleScheduler.scheduleAll(applicationContext, repository.currentRules())
                }
            } catch (_: Throwable) {
                // одна неудачная попытка не должна ронять службу
            } finally {
                // Короткая пауза, чтобы система успела завершить foreground-запуск.
                delay(120L)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, GSYNKApp.CHANNEL_APPLY)
            .setSmallIcon(R.drawable.ic_stat_gsynk)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_applying))
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val EXTRA_RULE_ID = "rule_id"

        fun start(context: Context, ruleId: String?) {
            val intent = Intent(context, ApplyService::class.java)
                .putExtra(EXTRA_RULE_ID, ruleId)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
