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

    /** Полный набор для устройств, где железо не тянет только самое тяжёлое. */
    const val TIER_STANDARD = "standard"

    /** Базовый набор (совсем слабые или без NFC). */
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
        val tier: String,
        val width: Int = 0,
        val height: Int = 0,
        val hasIr: Boolean = false,
        val isHyperOs: Boolean = false
    ) {
        val isFull: Boolean get() = tier == TIER_FULL

        val tierLabel: String
            get() = when (tier) {
                TIER_FULL -> "полный набор"
                TIER_STANDARD -> "расширенный набор"
                else -> "базовый набор"
            }

        val summary: String get() = tierLabel

        /** Доступна ли конкретная функция на этом железе. */
        fun enabled(feature: String): Boolean = when (feature) {
            // лаборатория и датчики безопасны везде: они ничего не ломают
            Feature.LAB, Feature.SENSORS -> true
            Feature.IR -> hasIr
            // шейдерное поле требует Android 13 и новее
            Feature.GPU -> androidSdk >= 33
            Feature.HYPEROS -> isHyperOs
            Feature.NFC_LINK -> hasNfc
            // максимальная нагрузка — только свежим устройствам, чтобы не жечь старые
            Feature.MAX_LOAD -> tier == TIER_FULL
            else -> true
        }
    }

    /** Модели, которым полный набор положен по праву железа. */
    private val KNOWN_FULL = listOf("x8 pro", "x8 pro max", "x7 pro", "f7", "f6", "x6 pro")

    /**
     * Старые, но крепкие устройства: им теперь тоже открыт расширенный набор —
     * лаборатория, датчики, индукция, ИК-пульт, скрытые настройки HyperOS.
     * Флагманские вещи (максимальная нагрузка) остаются за новыми моделями.
     */
    private val KNOWN_STANDARD = listOf(
        "m2102j20", "poco x3", "x3 pro", "x3 nfc", "redmi note 10", "redmi note 11",
        "redmi note 12", "poco x4", "poco m", "mi 10", "mi 11"
    )

    fun detect(context: Context): Info {
        val model = Build.MODEL ?: "неизвестно"
        val brand = (Build.BRAND ?: "").replaceFirstChar { it.uppercase() }
        val sdk = Build.VERSION.SDK_INT
        val hasNfc = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
        val overlay = RegionSupport.overlayAllowed(context)

        val known = KNOWN_FULL.any { model.lowercase().contains(it) }
        val modern = sdk >= 34

        val standard = KNOWN_STANDARD.any { model.lowercase().contains(it) }
        val metrics = context.resources.displayMetrics

        val (tier, reason) = when {
            known && hasNfc -> TIER_FULL to "Модель $model распознана: полный набор"
            modern && hasNfc -> TIER_FULL to "Android ${androidLabel(sdk)} и NFC: полный набор"
            standard && hasNfc ->
                TIER_STANDARD to "Модель $model: расширенный набор — всё, кроме максимальной нагрузки"

            sdk >= 30 && hasNfc ->
                TIER_STANDARD to "Android ${androidLabel(sdk)} и NFC: расширенный набор"

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
            tier = tier,
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            hasIr = runCatching {
                context.packageManager.hasSystemFeature("android.hardware.consumerir")
            }.getOrDefault(false),
            isHyperOs = DeviceLab.hyperOs(context)
        )
    }

    /**
     * Доступна ли конкретная функция на этом устройстве.
     *
     * Лаборатория и датчики безопасны и открыты всем, ИК — если есть излучатель,
     * шейдерное поле — с Android 13, максимальная нагрузка — только полному набору.
     */
    /**
     * Короткая строка о телефоне для отправки другу по связи.
     * Без секретов: только модель, система, набор функций и заряд.
     */
    fun shareLine(info: Info, batteryPercent: Int): String {
        val screen = if (info.width > 0 && info.height > 0) {
            ", экран " + info.width.toString() + "×" + info.height.toString()
        } else {
            ""
        }
        val battery = if (batteryPercent in 0..100) ", заряд " + batteryPercent.toString() + " %" else ""
        return "профиль: " + info.brand + " " + info.model +
            ", Android " + info.androidLabel +
            ", набор " + info.tierLabel +
            (if (info.hasNfc) ", NFC" else ", без NFC") +
            screen + battery
    }

    fun feature(info: Info, featureId: String): Boolean = when (featureId) {
        Feature.LAB, Feature.SENSORS -> true
        Feature.IR -> info.hasIr
        Feature.GPU -> info.androidSdk >= 33
        Feature.HYPEROS -> info.isHyperOs
        Feature.MAX_LOAD -> info.isFull
        Feature.NFC_LINK -> info.hasNfc
        else -> if (info.isFull) true else featureId in LEGACY_FEATURES
    }

    /** Что доступно в базовом режиме (Poco X3 Pro и подобные). */
    val LEGACY_FEATURES = setOf(
        Feature.LAB,
        Feature.SENSORS,
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
        const val AI_CHAT = "ai_chat"
        const val GRAPE_UI = "grape_ui"
        const val UPDATE = "update"
        const val SCREENSAVER = "screensaver"
        const val PROFILES = "profiles"
        // лаборатория и низкоуровневые инструменты
        const val LAB = "lab"
        const val SENSORS = "sensors"
        const val IR = "ir"
        const val GPU = "gpu"
        const val HYPEROS = "hyperos"
        const val MAX_LOAD = "max_load"

        val TITLES = linkedMapOf(
            RULES to "Яркость и громкость по расписанию",
            PROFILES to "Профили (одним касанием)",
            FLASHLIGHT to "Фонарик и световые инструменты",
            NFC_LINK to "Обмен по NFC",
            GAMES to "Мини-игры",
            SCREENSAVER to "Скринсейвер",
            TOOLS to "Инструменты и датчики",
            AI_CHAT to "Локальный ИИ-чат",
            GRAPE_UI to "GRAPEUI",
            UPDATE to "Обновление по воздуху",
            LAB to "Лаборатория: экран, катушки, скрытые настройки",
            SENSORS to "Живые датчики телефона",
            IR to "Инфракрасный пульт",
            GPU to "Поле на видеоускорителе",
            HYPEROS to "Разделы HyperOS",
            MAX_LOAD to "Максимальная нагрузка"
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
