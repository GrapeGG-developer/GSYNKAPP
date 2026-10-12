package com.gsynk.app.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

/**
 * Профиль устройства и режимы работы GSYNK.
 *
 * Идея «MAIN»-режима: на современных аппаратах (например Poco X8 Pro Max, Android 16 / HyperOS 3)
 * доступен весь набор функций, а на старых (например Poco X3 Pro, Android 13 / MIUI 14) —
 * базовый набор: яркость, громкость, обмен по NFC и мини-игры. Определение — по возможностям
 * системы, а не по названию модели, плюс ручное переключение в настройках.
 */
object DeviceProfile {

    /** Полный набор функций. */
    const val TIER_FULL = "full"

    /** Базовый набор (старые Android/MIUI). */
    const val TIER_LEGACY = "legacy"

    data class Info(
        val model: String,
        val brand: String,
        val androidSdk: Int,
        val androidLabel: String,
        val hasNfc: Boolean,
        val supportsOverlay: Boolean,
        val autoTier: String,
        /** Итоговый режим (с учётом ручного переключения). */
        val tier: String,
        val mainModeManual: Boolean
    ) {
        val isFull: Boolean get() = tier == TIER_FULL
        val summary: String
            get() = if (isFull) "MAIN — все функции" else "Базовый набор"
    }

    fun detect(context: Context, settings: AppSettings): Info {
        val model = Build.MODEL ?: "неизвестно"
        val brand = Build.BRAND ?: ""
        val sdk = Build.VERSION.SDK_INT
        val hasNfc = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
        val overlay = RegionSupport.overlayAllowed(context)

        // «Полный» режим: свежий Android + NFC + возможность рисовать поверх окон.
        val auto = if (sdk >= 34 && hasNfc && overlay) TIER_FULL else TIER_LEGACY
        val tier = when (settings.mainMode) {
            MainMode.AUTO -> auto
            MainMode.ON -> TIER_FULL
            else -> TIER_LEGACY
        }
        return Info(
            model = model,
            brand = brand.replaceFirstChar { it.uppercase(Locale.ROOT) },
            androidSdk = sdk,
            androidLabel = androidLabel(sdk),
            hasNfc = hasNfc,
            supportsOverlay = overlay,
            autoTier = auto,
            tier = tier,
            mainModeManual = settings.mainMode != MainMode.AUTO
        )
    }

    /** Доступна ли конкретная функция в текущем режиме. */
    fun feature(info: Info, featureId: String): Boolean {
        if (info.isFull) return true
        return featureId in LEGACY_FEATURES
    }

    /** Что доступно в базовом режиме (Poco X3 Pro и подобные). */
    val LEGACY_FEATURES = setOf(
        Feature.RULES,          // яркость и громкость
        Feature.NFC_LINK,       // обмен по NFC
        Feature.GAMES,          // мини-игры
        Feature.FLASHLIGHT,     // фонарик
        Feature.SCREENSAVER     // скринсейвер
    )

    object Feature {
        const val RULES = "rules"
        const val NFC_LINK = "nfc_link"
        const val GAMES = "games"
        const val FLASHLIGHT = "flashlight"
        const val DND = "dnd"
        const val TASKS = "tasks"
        const val PERSISTENT_ISLAND = "persistent_island"
        const val AI_CHAT = "ai_chat"
        const val GRAPE_UI = "grape_ui"
        const val UPDATE = "update"
        const val SCREENSAVER = "screensaver"
        const val SHIZUKU = "shizuku"

        val TITLES = mapOf(
            RULES to "Яркость и громкость по расписанию",
            NFC_LINK to "Обмен по NFC",
            GAMES to "Мини-игры",
            FLASHLIGHT to "Фонарик",
            DND to "Не беспокоить",
            TASKS to "Задачи: звонок и озвучка",
            PERSISTENT_ISLAND to "Постоянный остров",
            AI_CHAT to "ИИ-ассистент",
            GRAPE_UI to "GRAPE UI",
            UPDATE to "Обновление по воздуху",
            SCREENSAVER to "Скринсейвер",
            SHIZUKU to "Shizuku-режим"
        )
    }

    private fun androidLabel(sdk: Int): String = when {
        sdk >= 36 -> "Android 16"
        sdk == 35 -> "Android 15"
        sdk == 34 -> "Android 14"
        sdk == 33 -> "Android 13"
        sdk == 32 -> "Android 12L"
        sdk == 31 -> "Android 12"
        sdk == 30 -> "Android 11"
        else -> "Android $sdk"
    }
}

/** Проверки специальных доступов, которые нужны модулям. */
object RegionSupport {

    fun overlayAllowed(context: Context): Boolean = runCatching {
        android.provider.Settings.canDrawOverlays(context)
    }.getOrDefault(false)

    fun dndAccess(context: Context): Boolean = runCatching {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .isNotificationPolicyAccessGranted
    }.getOrDefault(false)
}
