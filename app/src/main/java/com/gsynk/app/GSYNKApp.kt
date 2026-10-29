package com.gsynk.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.gsynk.app.core.AppLog

/**
 * GSYNK — мультитул: расписание яркости и громкости, шифры и анонимность,
 * Grape Link по NFC, локальная ИИ-модель, сцены, мини-игры и хакерский набор
 * инструментов. Все данные хранятся на устройстве; интернет нужен только для
 * проверки обновлений.
 */
class GSYNKApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createChannels()
        // приём сбоев и журнал: включаются раньше всего остального
        AppLog.install(this)
        // глубокое чтение железа (память, тепловые зоны) работает без Compose —
        // поэтому держим ссылку на приложение в одном месте
        com.gsynk.app.core.AppContextHolder.install(this)
        com.gsynk.app.core.Skin.load(this)
        // Первый запуск уже установленной новой версии: скачанный ею же APK больше
        // не нужен и не должен лежать в кэше. Уборка в отдельном потоке, чтобы не
        // тормозить старт приложения чтением архивного манифеста.
        Thread {
            runCatching { com.gsynk.app.core.UpdateChecker.cleanupDownloadedUpdates(this) }
        }.apply {
            name = "gsynk-update-cleanup"
            start()
        }
        // Фоновая проверка обновлений: система будит приложение раз в полчаса,
        // чтобы новая версия находила телефон даже с закрытым приложением.
        com.gsynk.app.core.UpdateJobs.schedule(this)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return

        // Применение правил
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_APPLY,
                getString(R.string.notif_channel_apply),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_apply_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )

        // Обновления приложения
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPDATES,
                getString(R.string.notif_channel_updates),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notif_channel_updates_desc)
                setShowBadge(true)
                enableVibration(true)
                setSound(null, null)
            }
        )
    }

    companion object {
        const val CHANNEL_APPLY = "gsynk_apply"
        const val CHANNEL_UPDATES = "gsynk_updates"
    }
}
