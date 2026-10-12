package com.gsynk.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

/**
 * GSYNK — мультитул: расписание яркости и громкости, постоянный остров,
 * NFC-связка, мини-игры, задачи со звонком и озвучкой.
 * Все данные хранятся на устройстве; интернет нужен только для OTA-обновления и ИИ-чата.
 */
class GSYNKApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_APPLY,
            getString(R.string.notif_channel_apply),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_apply_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)

        val fakeCall = NotificationChannel(
            CHANNEL_FAKE_CALL,
            getString(R.string.notif_channel_fake_call),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notif_channel_fake_call_desc)
            setShowBadge(false)
            enableVibration(true)
            setSound(null, null)
        }
        nm.createNotificationChannel(fakeCall)
    }

    companion object {
        const val CHANNEL_APPLY = "gsynk_apply"
        const val CHANNEL_FAKE_CALL = "gsynk_fake_call"
    }
}
