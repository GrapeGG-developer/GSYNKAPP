package com.gsynk.app.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Профиль устройства: какие функции доступны на этом телефоне.
 *
 * Набор определяется **автоматически по железу** и не переключается вручную:
 * это исключает ситуации «включил полный режим на старом телефоне и всё сломалось».
 *
 *  - **Полный набор** — современный Android (14+) с NFC: все модули приложения.
 *  - **Базовый набор** — старые устройства: расписание яркости/громкости, фонарик,
 *    NFC-обмен, мини-игры, скринсейвер.
 *
 * Дополнительно распознаются известные модели (Poco X8 Pro Max и родственные),
 * чтобы даже на граничных версиях Android включался полный набор.
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
        /** Почему выбран этот набор — понятным языком. */
        val reason: String,
        val tier: String
    ) {
        val isFull: Boolean get() = tier == TIER_FULL
        val summary: String
            get() = if (isFull) "Все функции" else "Базовый набор"
    }

    /** Модели, которым полный набор положен по праву железа. */
    private val KNOWN_FULL = listOf("x8 pro", "x8 pro max", "x7 pro", "f7", "f6", "x6 pro")

    fun detect(context: Context): Info {
        val model = Build.MODEL ?: "неизвестно"
        val brand = (Build.BRAND ?: "").replaceFirstChar { it.uppercase() }
        val sdk = Build.VERSION.SDK_INT
        val hasNfc = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
        val overlay = RegionSupport.overlayAllowed(context)

        val known = KNOWN_FULL.any { model.lowercase().contains(it) }
        val modern = sdk >= 34

        val (tier, reason) = when {
            known && hasNfc -> TIER_FULL to "Модель $model распознана: полный набор"
            modern && hasNfc -> TIER_FULL to "Android ${androidLabel(sdk)} и NFC: полный набор"
            !hasNfc -> TIER_LEGACY to "Нет модуля NFC: базовый набор"
            else -> TIER_LEGACY to "Android ${androidLabel(sdk)}: базовый набор"
        }

        return Info(
            model = model,
            brand = brand,
            androidSdk = sdk,
            androidLabel = androidLabel(sdk),
            hasNfc = hasNfc,
            supportsOverlay = overlay,
            reason = reason,
            tier = tier
        )
    }

    /** Доступна ли конкретная функция на этом устройстве. */
    fun feature(info: Info, featureId: String): Boolean {
        if (info.isFull) return true
        return featureId in LEGACY_FEATURES
    }

    /** Что доступно в базовом режиме (Poco X3 Pro и подобные). */
    val LEGACY_FEATURES = setOf(
        Feature.RULES,       // яркость и громкость
        Feature.NFC_LINK,    // обмен по NFC
        Feature.GAMES,       // мини-игры
        Feature.FLASHLIGHT,  // фонарик
        Feature.SCREENSAVER  // скринсейвер
    )

    object Feature {
        const val RULES = "rules"
        const val NFC_LINK = "nfc_link"
        const val GAMES = "games"
        const val FLASHLIGHT = "flashlight"
        const val TOOLS = "tools"
        const val ISLAND = "island"
        const val AI_CHAT = "ai_chat"
        const val GRAPE_UI = "grape_ui"
        const val UPDATE = "update"
        const val SCREENSAVER = "screensaver"
        const val PROFILES = "profiles"

        val TITLES = linkedMapOf(
            RULES to "Яркость и громкость по расписанию",
            PROFILES to "Профили (одним касанием)",
            FLASHLIGHT to "Фонарик и световые инструменты",
            NFC_LINK to "Обмен по NFC",
            GAMES to "Мини-игры",
            SCREENSAVER to "Скринсейвер",
            TOOLS to "Инструменты и датчики",
            ISLAND to "Остров у камеры",
            AI_CHAT to "Локальный ИИ-чат",
            GRAPE_UI to "GRAPEUI",
            UPDATE to "Обновление по воздуху"
        )
    }

    private fun androidLabel(sdk: Int): String = when {
        sdk >= 36 -> "16"
        sdk == 35 -> "15"
        sdk == 34 -> "14"
        sdk == 33 -> "13"
        sdk == 32 -> "12L"
        sdk == 31 -> "12"
        sdk == 30 -> "11"
        else -> sdk.toString()
    }
}

/** Доступы, которые проверяются на месте. */
object RegionSupport {
    fun overlayAllowed(context: Context): Boolean = runCatching {
        android.provider.Settings.canDrawOverlays(context)
    }.getOrDefault(false)

    fun dndAccess(context: Context): Boolean = runCatching {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.isNotificationPolicyAccessGranted
    }.getOrDefault(false)
}
