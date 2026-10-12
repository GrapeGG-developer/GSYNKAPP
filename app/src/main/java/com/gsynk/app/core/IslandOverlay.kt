package com.gsynk.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import androidx.core.view.isVisible
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gsynk.app.R

/**
 * «Динамический остров» GSYNK — компактная капсула в стиле HyperOS.
 *
 * Системный остров HyperOS третьим приложениям недоступен (внутренний API MIUI),
 * поэтому GSYNK рисует свой: одна строка текста в две строки мелким кеглем,
 * минимальные отступы, анимация «пружиной» и полоса прогресса.
 *
 * Требуется разрешение «Отображение поверх других приложений».
 */
@SuppressLint("StaticFieldLeak")
object IslandOverlay {

    private const val TAG_TITLE = "gsynk_island_title"
    private const val TAG_SUBTITLE = "gsynk_island_subtitle"
    private const val TAG_ICON = "gsynk_island_icon"
    private const val TAG_PROGRESS = "gsynk_island_progress"
    private const val TAG_EXPANDED = "gsynk_island_expanded"

    private const val COLOR_VIOLET = 0xFF7C3AED.toInt()
    private const val COLOR_GREEN = 0xFF22C55E.toInt()
    private const val COLOR_CAPSULE = 0xFF12101C.toInt()
    private const val COLOR_TEXT = 0xFFF7F3FF.toInt()
    private const val COLOR_TEXT_DIM = 0xFFC6B9DD.toInt()

    private val handler = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var windowManager: WindowManager? = null
    private var hideTask: Runnable? = null
    private var persistent = false

    /** Есть ли разрешение рисовать поверх других приложений. */
    fun isAvailable(context: Context): Boolean = runCatching {
        Settings.canDrawOverlays(context)
    }.getOrDefault(false)

    /** Показать остров. Если он уже виден — текст обновится, таймер перезапустится. */
    fun show(
        context: Context,
        iconRes: Int,
        title: String,
        subtitle: String,
        position: Int = IslandPosition.TOP,
        durationMs: Long = 3400L
    ) {
        val app = context.applicationContext
        if (!isAvailable(app)) return
        handler.post { runCatching { showInternal(app, iconRes, title, subtitle, position, durationMs) } }
    }

    /** Скрыть остров немедленно. */
    fun hide() {
        handler.post { runCatching { hideInternal() } }
    }

    /**
     * Постоянный остров: висит на экране всегда, обновляется методами updatePersistent,
     * по нажатию раскрывается и показывает дополнительную строку.
     */
    fun showPersistent(context: Context, title: String, subtitle: String, expandedText: String, position: Int) {
        val app = context.applicationContext
        if (!isAvailable(app)) return
        persistent = true
        handler.post {
            runCatching { showInternal(app, 0, title, subtitle, position, 0L, persistent = true, expandedText = expandedText, keepIcons = true) }
        }
    }

    /** Обновить тексты постоянного острова (без повторной анимации появления). */
    fun updatePersistent(context: Context, title: String, subtitle: String, expandedText: String, position: Int) {
        if (!persistent) return
        val app = context.applicationContext
        handler.post {
            runCatching { showInternal(app, 0, title, subtitle, position, 0L, persistent = true, expandedText = expandedText, keepIcons = true) }
        }
    }

    /** Выключить постоянный режим и убрать капсулу. */
    fun hidePersistent() {
        persistent = false
        hide()
    }

    /** Показывается ли сейчас постоянная капсула. */
    fun isShownPersistent(): Boolean = persistent && root?.parent != null

    // ————————————————————————————— внутреннее —————————————————————————————

    private fun showInternal(
        context: Context,
        iconRes: Int,
        title: String,
        subtitle: String,
        position: Int,
        durationMs: Long,
        persistent: Boolean = false,
        expandedText: String = "",
        keepIcons: Boolean = false
    ) {
        hideTask?.let { handler.removeCallbacks(it) }
        hideTask = null

        val existing = root
        if (existing != null && existing.parent != null) {
            existing.findViewWithTag<TextView>(TAG_TITLE)?.text = title
            existing.findViewWithTag<TextView>(TAG_SUBTITLE)?.text = subtitle
            if (expandedText.isNotBlank()) {
                existing.findViewWithTag<TextView>(TAG_EXPANDED)?.text = expandedText
            }
            if (iconRes != 0 && !keepIcons) {
                existing.findViewWithTag<ImageView>(TAG_ICON)?.setImageResource(iconRes)
            }
            if (persistent) {
                // постоянный режим: только обновляем текст, никаких «прыжков»
                return
            }
            windowManager?.let { wm ->
                val params = existing.layoutParams as? WindowManager.LayoutParams
                if (params != null) {
                    applyPosition(context, params, position)
                    runCatching { wm.updateViewLayout(existing, params) }
                }
            }
            existing.animate().cancel()
            existing.scaleX = 1f
            existing.scaleY = 1f
            existing.animate()
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(130)
                .withEndAction {
                    existing.animate().scaleX(1f).scaleY(1f).setDuration(170)
                        .setInterpolator(OvershootInterpolator(1.4f)).start()
                }
                .start()
            if (!persistent) {
                restartProgress(existing, durationMs)
                scheduleHide(durationMs)
            }
            return
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val view = buildView(context, iconRes, title, subtitle, expandedText, persistent)
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        // обычную капсулу не хочется ловить пальцем, а постоянную — наоборот, нужно
        if (!persistent) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        applyPosition(context, params, position)

        runCatching { wm.addView(view, params) }.onFailure { return }

        root = view
        windowManager = wm

        // появление: пружина с той стороны, откуда выезжает капсула
        val fromY = if (position == IslandPosition.BOTTOM) dp(context, 18f) else -dp(context, 18f)
        view.alpha = 0f
        view.scaleX = 0.72f
        view.scaleY = 0.72f
        view.translationY = fromY
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(360)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        restartProgress(view, durationMs)
        if (persistent) {
            // постоянный остров живёт до явного выключения
        } else {
            scheduleHide(durationMs)
        }
    }

    private fun applyPosition(context: Context, params: WindowManager.LayoutParams, position: Int) {
        when (position) {
            IslandPosition.BOTTOM -> {
                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                params.y = navBarHeight(context) + dp(context, 12f).toInt()
            }

            IslandPosition.CENTER -> {
                params.gravity = Gravity.CENTER
                params.y = 0
            }

            else -> {
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.y = statusBarHeight(context) + dp(context, 4f).toInt()
            }
        }
    }

    private fun hideInternal() {
        val view = root ?: return
        root = null
        hideTask?.let { handler.removeCallbacks(it) }
        hideTask = null
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationY(-dp(view.context, 14f))
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(240)
            .withEndAction {
                runCatching { windowManager?.removeView(view) }
                windowManager = null
            }
            .start()
    }

    private fun scheduleHide(durationMs: Long) {
        val task = Runnable { runCatching { hideInternal() } }
        hideTask = task
        handler.postDelayed(task, durationMs + 240L)
    }

    private fun restartProgress(view: View, durationMs: Long) {
        val progress = view.findViewWithTag<View>(TAG_PROGRESS) ?: return
        progress.animate().cancel()
        progress.pivotX = 0f
        progress.scaleX = 1f
        progress.animate()
            .scaleX(0f)
            .setDuration(durationMs)
            .setInterpolator(LinearInterpolator())
            .start()
    }

    private fun buildView(
        context: Context,
        iconRes: Int,
        title: String,
        subtitle: String,
        expandedText: String = "",
        persistent: Boolean = false
    ): View {
        // внешняя рамка — градиент фиолет → зелёный
        val outer = FrameLayout(context)
        outer.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(COLOR_VIOLET, COLOR_GREEN)
        ).apply { cornerRadius = dp(context, 26f) }

        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(COLOR_CAPSULE)
                cornerRadius = dp(context, 23f)
            }
            setPadding(
                dp(context, 13f).toInt(),
                dp(context, 6f).toInt(),
                dp(context, 15f).toInt(),
                dp(context, 6f).toInt()
            )
        }
        outer.addView(
            inner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val margin = dp(context, 2.5f).toInt().coerceAtLeast(1)
                setMargins(margin, margin, margin, margin)
            }
        )

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(context).apply {
            tag = TAG_ICON
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(if (iconRes != 0) iconRes else R.drawable.ic_brightness)
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(context, 18f).toInt(), dp(context, 18f).toInt()))

        val spacer = View(context)
        row.addView(spacer, LinearLayout.LayoutParams(dp(context, 8f).toInt(), 1))

        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val titleView = TextView(context).apply {
            tag = TAG_TITLE
            text = title
            setTextColor(COLOR_TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isSingleLine = true
            includeFontPadding = false
        }
        val subtitleView = TextView(context).apply {
            tag = TAG_SUBTITLE
            text = subtitle
            setTextColor(COLOR_TEXT_DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            isSingleLine = true
            includeFontPadding = false
        }
        texts.addView(titleView)
        texts.addView(subtitleView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(context, 0.5f).toInt() })
        row.addView(texts)

        inner.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // тонкая полоса прогресса
        val progress = View(context).apply {
            tag = TAG_PROGRESS
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(COLOR_VIOLET, COLOR_GREEN)
            ).apply { cornerRadius = dp(context, 2f) }
        }
        inner.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 2.5f).toInt().coerceAtLeast(1)
            ).apply { topMargin = dp(context, 5f).toInt() }
        )

        // раскрытая строка (для постоянного острова)
        val expandedView = TextView(context).apply {
            tag = TAG_EXPANDED
            text = expandedText
            setTextColor(COLOR_TEXT_DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            isSingleLine = true
            includeFontPadding = false
            visibility = View.GONE
        }
        inner.addView(
            expandedView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 5f).toInt() }
        )

        if (persistent) {
            // касание: пружинистая реакция + раскрытие информации
            outer.isClickable = true
            outer.setOnClickListener { tapped ->
                tapped.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                tapped.animate().cancel()
                tapped.animate()
                    .scaleX(1.07f).scaleY(1.07f)
                    .setDuration(110)
                    .withEndAction {
                        tapped.animate().scaleX(1f).scaleY(1f)
                            .setDuration(180)
                            .setInterpolator(OvershootInterpolator(1.5f))
                            .start()
                    }
                    .start()

                val expanded = tapped.findViewWithTag<TextView>(TAG_EXPANDED)
                if (expanded != null) {
                    if (expanded.isVisible) {
                        expanded.animate().alpha(0f).setDuration(140).withEndAction {
                            expanded.visibility = View.GONE
                        }.start()
                    } else {
                        expanded.visibility = View.VISIBLE
                        expanded.alpha = 0f
                        expanded.translationY = -dp(context, 4f)
                        expanded.animate().alpha(1f).translationY(0f).setDuration(220).start()
                    }
                }
            }
        }

        return outer
    }

    private fun dp(context: Context, value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        context.resources.displayMetrics
    )

    private fun statusBarHeight(context: Context): Int {
        val top = runCatching {
            context.getSystemService(WindowManager::class.java)
                ?.currentWindowMetrics
                ?.windowInsets
                ?.getInsets(WindowInsets.Type.statusBars())
                ?.top
        }.getOrNull() ?: 0
        return if (top > 0) top else dp(context, 24f).toInt()
    }

    private fun navBarHeight(context: Context): Int {
        val bottom = runCatching {
            context.getSystemService(WindowManager::class.java)
                ?.currentWindowMetrics
                ?.windowInsets
                ?.getInsets(WindowInsets.Type.navigationBars())
                ?.bottom
        }.getOrNull() ?: 0
        return if (bottom > 0) bottom else dp(context, 24f).toInt()
    }
}
