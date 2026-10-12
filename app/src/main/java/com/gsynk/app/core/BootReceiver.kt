package com.gsynk.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * Перезагрузка телефона, обновление приложения, смена времени или часового пояса,
 * а также выдача доступа к точным будильникам — во всех этих случаях расписание
 * и отложенные задачи нужно пересобрать.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val supported = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        if (!supported) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = RuleRepository(appContext)
                val settings = repository.currentSettings()
                val rules = repository.currentRules()

                // ————— задачи (звонок, озвучка, розыгрыш) —————
                // Будильники не выживают перезагрузку и обновление приложения:
                // актуальные ставим заново, просроченные убираем из списка.
                val taskRepository = TaskRepository(appContext)
                val tasks = taskRepository.current()
                if (tasks.isNotEmpty()) {
                    val plan = TaskScheduler.restore(tasks)
                    TaskScheduler.scheduleAll(appContext, plan.future)
                    if (plan.expired.isNotEmpty()) {
                        taskRepository.save(plan.future)
                    }
                }

                // ————— правила яркости и громкости —————
                if (!settings.autopilot) {
                    rules.forEach { RuleScheduler.cancel(appContext, it.id) }
                    return@launch
                }

                RuleScheduler.scheduleAll(appContext, rules)

                // «Применить последнее правило»: ищем правило, чьё время по графику
                // было позже всех, и приводим телефон к этому состоянию.
                if (action == Intent.ACTION_BOOT_COMPLETED && settings.syncAfterBoot) {
                    val now = ZonedDateTime.now()
                    val latest = rules.asSequence()
                        .filter { it.enabled }
                        .mapNotNull { rule -> RuleScheduler.lastOccurrence(rule, now)?.let { it to rule } }
                        .maxByOrNull { it.first }
                    if (latest != null) {
                        ApplyService.start(appContext, latest.second.id)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
