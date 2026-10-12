package com.gsynk.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZonedDateTime

/**
 * Планировщик: считает ближайшее (и последнее прошедшее) время срабатывания
 * правила и ставит точный будильник AlarmManager.
 */
object RuleScheduler {

    const val ACTION_FIRE = "com.gsynk.app.action.FIRE_RULE"
    const val EXTRA_RULE_ID = "rule_id"

    /**
     * Ближайшее срабатывание правила или null.
     *
     * Если у правила выбраны точные даты — оно «разовое»: срабатывает только в эти даты
     * и никогда не повторяется по неделе (иначе срабатывало бы каждую неделю в тот же день).
     */
    fun nextOccurrence(rule: Rule, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        if (!rule.enabled) return null

        if (rule.hasDates) {
            return rule.validDates.asSequence()
                .map { date -> date.atTime(rule.hour, rule.minute).atZone(now.zone) }
                .filter { it.isAfter(now) }
                .minOrNull()
        }

        val days = rule.effectiveDays()
        if (days.isEmpty()) return null
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (days.contains(date.dayOfWeek.value)) {
                val candidate = date.atTime(rule.hour, rule.minute).atZone(now.zone)
                if (candidate.isAfter(now)) return candidate
            }
        }
        return null
    }

    /** Последнее прошедшее срабатывание правила (для синхронизации после перезагрузки). */
    fun lastOccurrence(rule: Rule, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        if (!rule.enabled) return null

        if (rule.hasDates) {
            return rule.validDates.asSequence()
                .map { date -> date.atTime(rule.hour, rule.minute).atZone(now.zone) }
                .filter { !it.isAfter(now) }
                .maxOrNull()
        }

        val days = rule.effectiveDays()
        if (days.isEmpty()) return null
        for (offset in 0..7) {
            val date = now.toLocalDate().minusDays(offset.toLong())
            if (days.contains(date.dayOfWeek.value)) {
                val candidate = date.atTime(rule.hour, rule.minute).atZone(now.zone)
                if (!candidate.isAfter(now)) return candidate
            }
        }
        return null
    }

    fun pendingIntent(context: Context, ruleId: String): PendingIntent {
        val intent = Intent(context, ApplyReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_RULE_ID, ruleId)
        return PendingIntent.getBroadcast(
            context,
            ruleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canScheduleExact(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
        } else {
            true
        }

    fun schedule(context: Context, rule: Rule): ZonedDateTime? {
        val at = nextOccurrence(rule) ?: return null
        val am = context.getSystemService(AlarmManager::class.java) ?: return null
        val pi = pendingIntent(context, rule.id)
        val triggerAt = at.toInstant().toEpochMilli()
        runCatching {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
        return at
    }

    fun cancel(context: Context, ruleId: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(pendingIntent(context, ruleId)) }
    }

    /** Пересобирает будильники для всех правил, возвращает ближайшее время. */
    fun scheduleAll(context: Context, rules: List<Rule>): ZonedDateTime? {
        var earliest: ZonedDateTime? = null
        rules.forEach { rule ->
            if (rule.enabled) {
                val at = schedule(context, rule)
                val currentEarliest = earliest
                if (at != null && (currentEarliest == null || at.isBefore(currentEarliest))) {
                    earliest = at
                }
            } else {
                cancel(context, rule.id)
            }
        }
        return earliest
    }
}
