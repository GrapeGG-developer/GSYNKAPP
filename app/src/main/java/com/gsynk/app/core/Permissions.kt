package com.gsynk.app.core

import android.annotation.SuppressLint
import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/** Разрешения и специальные доступы GSYNK. */
object Permissions {

    const val ID_EXACT = "exact"
    const val ID_WRITE = "write"
    const val ID_NOTIF = "notif"
    const val ID_BATTERY = "battery"
    const val ID_OVERLAY = "overlay"
    const val ID_AUTOSTART = "autostart"
    const val ID_MIC = "mic"
    const val ID_NEARBY_BT = "nearby-bt"
    const val ID_NEARBY_WIFI = "nearby-wifi"

    data class Item(
        val id: String,
        val title: String,
        val granted: Boolean,
        /** Необязательный доступ: нужен только отдельным визуальным функциям. */
        val optional: Boolean = false
    )

    data class Snapshot(val items: List<Item>) {
        val missing: List<Item> get() = items.filter { !it.granted && !it.optional }
        val optionalMissing: List<Item> get() = items.filter { !it.granted && it.optional }
        val allGranted: Boolean get() = missing.isEmpty()
    }

    fun canWriteSettings(context: Context): Boolean = runCatching {
        Settings.System.canWrite(context)
    }.getOrDefault(false)

    fun canDrawOverlays(context: Context): Boolean = runCatching {
        Settings.canDrawOverlays(context)
    }.getOrDefault(false)

    fun canScheduleExact(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
            }.getOrDefault(false)
        } else {
            true
        }

    fun hasNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /** Разрешение на микрофон (шумомер в «хакерском наборе»). */
    fun hasMicrophone(context: Context): Boolean = runCatching {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun batteryExempt(context: Context): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }.getOrDefault(false)

    /** Доступен ли экран автозапуска (HyperOS/MIUI и родственные прошивки). */
    fun autostartAvailable(context: Context): Boolean = runCatching {
        val component = ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
        context.packageManager.resolveActivity(Intent().setComponent(component), 0) != null
    }.getOrDefault(false)

    /**
     * Включён ли автозапуск. Напрямую прочитать это нельзя, поэтому смотрим,
     * выдан ли заодно доступ к фоновой работе: HyperOS включает автозапуск
     * вместе с ним.
     */
    fun autostartEnabled(context: Context): Boolean = runCatching {
        batteryExempt(context) && canDrawOverlays(context)
    }.getOrDefault(false)

    /** Заголовки уже выданных доступов — короткая строка для отчётов. */
    fun grantedTitles(context: Context): List<String> =
        runCatching { snapshot(context).items.filter { it.granted }.map { it.title } }.getOrDefault(emptyList())

    fun snapshot(context: Context): Snapshot = Snapshot(
        listOf(
            Item(ID_EXACT, "Точные будильники", canScheduleExact(context)),
            Item(ID_WRITE, "Изменение системных настроек", canWriteSettings(context)),
            Item(ID_BATTERY, "Без ограничений батареи", batteryExempt(context)),
            Item(ID_NOTIF, "Уведомления", hasNotifications(context)),
            Item(ID_OVERLAY, "Слой поверх приложений (затемнение и NFC-показ)", canDrawOverlays(context), optional = true),
            Item(ID_MIC, "Микрофон (шумомер)", hasMicrophone(context), optional = true)
        )
    )

    /**
     * Открывает системный экран нужного разрешения.
     *
     * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS используется осознанно: HyperOS усыпляет фоновые
     * приложения и будильники опаздывают. Разрешение запрашивается только по действию
     * пользователя (нажатие «Без ограничений батареи»).
     */
    @SuppressLint("BatteryLife")
    fun open(context: Context, id: String) {
        val uri = "package:${context.packageName}".toUri()
        val intent: Intent = when (id) {
            ID_EXACT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri)
            } else {
                appDetails(context)
            }

            ID_WRITE -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, uri)

            ID_NOTIF -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

            ID_BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri)

            ID_OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)

            ID_AUTOSTART -> Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            )

            else -> appDetails(context)
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(appDetails(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
}
