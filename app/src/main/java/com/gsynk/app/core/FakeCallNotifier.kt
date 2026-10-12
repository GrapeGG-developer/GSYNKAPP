package com.gsynk.app.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.gsynk.app.GSYNKApp
import com.gsynk.app.R

/**
 * «Фейковый звонок» — шуточная функция: на экране появляется уведомление о вымышленном
 * входящем звонке (в стиле системного), после чего можно озвучить любую фразу.
 * Никаких реальных звонков и никакого доступа к вызовам не требуется.
 */
object FakeCallNotifier {

    fun show(context: Context, callerName: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(nm, context)

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), // нейтральный экран, без «ответа»
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(context, GSYNKApp.CHANNEL_FAKE_CALL)
            .setSmallIcon(R.drawable.ic_stat_gsynk)
            .setContentTitle("Входящий вызов")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openIntent)
            .build()

        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }

    fun hide(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.cancel(NOTIFICATION_ID) }
    }

    private fun ensureChannel(nm: NotificationManager, context: Context) {
        val channel = NotificationChannel(
            GSYNKApp.CHANNEL_FAKE_CALL,
            context.getString(R.string.notif_channel_fake_call),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_fake_call_desc)
        }
        nm.createNotificationChannel(channel)
    }

    private const val NOTIFICATION_ID = 2002
}
