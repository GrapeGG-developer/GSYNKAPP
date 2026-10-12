package com.gsynk.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Отложенная задача («автопилотные функции»): через N секунд/минут приложение,
 * например, звонит по номеру и произносит фразу синтезом речи, либо просто
 * озвучивает текст, либо имитирует входящий звонок.
 */
@Serializable
data class ScheduledTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Задача",
    /** Когда выполнить — абсолютное время в миллисекундах (RTC). */
    val triggerAt: Long = System.currentTimeMillis() + 60_000L,
    /** call | speak | fake_call */
    val kind: String = KIND_SPEAK,
    val number: String = "",
    val contactName: String = "",
    val phrase: String = "",
    /** Сказать фразу после начала звонка (через сколько мс от запуска задачи). */
    val speakDelayMs: Long = 6_000L,
    val enabled: Boolean = true
) {
    companion object {
        const val KIND_CALL = "call"
        const val KIND_SPEAK = "speak"
        const val KIND_FAKE_CALL = "fake_call"

        val KIND_TITLES = mapOf(
            KIND_CALL to "Звонок + озвучка",
            KIND_SPEAK to "Озвучить фразу",
            KIND_FAKE_CALL to "Фейковый звонок"
        )
    }
}

private val Context.taskStore by preferencesDataStore(name = "gsynk_tasks")

/** Хранилище задач + запуск будильников. */
class TaskRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("tasks")

    val tasks: Flow<List<ScheduledTask>> = appContext.taskStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        runCatching { json.decodeFromString(ListSerializer(ScheduledTask.serializer()), raw) }
            .getOrElse { emptyList() }
    }

    suspend fun current(): List<ScheduledTask> = tasks.first()

    suspend fun save(list: List<ScheduledTask>) {
        appContext.taskStore.edit { prefs ->
            prefs[key] = json.encodeToString(ListSerializer(ScheduledTask.serializer()), list)
        }
    }

    suspend fun add(task: ScheduledTask) {
        val list = current().toMutableList()
        list.add(task)
        save(list)
        TaskScheduler.schedule(appContext, task)
    }

    suspend fun remove(task: ScheduledTask) {
        save(current().filterNot { it.id == task.id })
        TaskScheduler.cancel(appContext, task)
    }
}

object TaskScheduler {

    fun pendingIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, TaskReceiver::class.java)
            .setAction(TaskReceiver.ACTION_RUN)
            .putExtra(TaskReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, task: ScheduledTask) {
        if (!task.enabled) return
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = task.triggerAt.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        val pi = pendingIntent(context, task.id)
        runCatching {
            if (RuleScheduler.canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    fun cancel(context: Context, task: ScheduledTask) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(pendingIntent(context, task.id)) }
    }

    fun scheduleAll(context: Context, tasks: List<ScheduledTask>) {
        val now = System.currentTimeMillis()
        tasks.filter { it.enabled && it.triggerAt > now }.forEach { schedule(context, it) }
    }

    /** Что делать со списком задач после перезагрузки или обновления приложения. */
    data class Restore(
        /** Задачи, которые ещё впереди — их надо заново поставить в будильник. */
        val future: List<ScheduledTask>,
        /** Просроченные и выключенные — их будильники уже не нужны. */
        val expired: List<ScheduledTask>
    )

    /**
     * Будильники не переживают перезагрузку телефона и обновление приложения,
     * поэтому при старте список делится на «ещё актуальные» и «отработанные».
     */
    fun restore(tasks: List<ScheduledTask>, now: Long = System.currentTimeMillis()): Restore {
        val future = tasks.filter { it.enabled && it.triggerAt > now }
        val expired = tasks.filterNot { it.enabled && it.triggerAt > now }
        return Restore(future = future, expired = expired)
    }
}

/** Выполняет задачу в назначенное время. */
class TaskReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN) return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val app = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = TaskRepository(app)
                val task = repo.current().firstOrNull { it.id == taskId } ?: return@launch

                when (task.kind) {
                    ScheduledTask.KIND_CALL -> {
                        val called = AutomationActions.call(app, task.number)
                        if (!called) AutomationActions.dial(app, task.number)
                        if (task.phrase.isNotBlank()) {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                AutomationActions.speak(app, task.phrase)
                            }, task.speakDelayMs.coerceAtLeast(0))
                        }
                    }

                    ScheduledTask.KIND_SPEAK -> AutomationActions.speak(app, task.phrase)

                    ScheduledTask.KIND_FAKE_CALL -> {
                        // имитация входящего звонка: сначала «звонит» уведомление, потом голос
                        FakeCallNotifier.show(app, task.contactName.ifBlank { "Неизвестный" })
                        if (task.phrase.isNotBlank()) {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                AutomationActions.speak(app, task.phrase)
                            }, task.speakDelayMs.coerceAtLeast(1_000L))
                        }
                    }
                }

                // одноразовая задача — убираем из хранилища
                repo.save(repo.current().filterNot { it.id == task.id })
            } catch (_: Throwable) {
                // задача не должна ронять процесс
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_RUN = "com.gsynk.app.action.RUN_TASK"
        const val EXTRA_TASK_ID = "task_id"
    }
}
