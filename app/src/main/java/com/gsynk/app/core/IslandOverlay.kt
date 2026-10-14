package com.gsynk.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible

/**
 * Капсула GSYNK поверх всех приложений — «свой Dynamic Island».
 *
 * Два режима: короткая вспышка о сработавшем правиле и постоянный остров с
 * выбранной информацией (часы, заряд, ближайшее правило, счётчик NFC, рекорд).
 * Размер компактный: капсула не должна спорить с системным вырезом камеры.
 */
@SuppressLint("StaticFieldLeak")
object IslandOverlay {

    private val handler = Handler(Looper.getMainLooper())

    private const val TAG_TITLE = "gsynk_island_title"
    private const val TAG_SUBTITLE = "gsynk_island_subtitle"
    private const val TAG_PROGRESS = "gsynk_island_progress"
    private const val TAG_EXPANDED = "gsynk_island_expanded"
    private const val TAG_ICON = "gsynk_island_icon"

    private const val COLOR_BG = 0xF20E0B1A.toInt()
    private const val COLOR_BG_WARM = 0xF21B1030.toInt()
    private const val COLOR_BG_COOL = 0xF2081430.toInt()
    private const val COLOR_BORDER = 0x4DA855F7.toInt()
    private const val COLOR_TEXT = 0xFFF3F0FF.toInt()
    private const val COLOR_TEXT_DIM = 0xFF9C8FB5.toInt()
    private const val COLOR_ACCENT = 0xFFC9A8FF.toInt()
    private const val COLOR_GREEN = 0xFF4ADE80.toInt()

    private var root: View? = null
    private var windowManager: WindowManager? = null
    private var hideTask: Runnable? = null
    private var persistent = false
    /** Анимация живого стекла капсулы: гасим её вместе с окном. */
    private var glassAnimator: android.animation.ValueAnimator? = null
    /** Остров 2.0: капсула садится вплотную под фронтальную камеру. */
    var hugCamera: Boolean = true
    /**
     * Ссылка на оконный менеджер — единственный «контекстный» объект в этом
     * синглтоне. Он берётся от application-контекста и переживает активности.
     */
    /** Что сейчас на экране: постоянная капсула или вспышка. */
    private var rootPersistent = false

    fun isAvailable(context: Context): Boolean = runCatching {
        Settings.canDrawOverlays(context)
    }.getOrDefault(false)

    /** Короткая капсула: показать и убрать через [durationMs]. */
    fun show(
        context: Context,
        iconRes: Int,
        title: String,
        subtitle: String,
        position: Int = IslandPosition.TOP_CENTER,
        durationMs: Long = 3200L,
        compact: Boolean = true,
        offsetY: Int = 0
    ) {
        val app = context.applicationContext
        if (!isAvailable(app)) return
        handler.post {
            runCatching {
                showInternal(app, iconRes, title, subtitle, position, durationMs, false, "", false, compact, offsetY)
            }
        }
    }

    /** Постоянный остров: живёт, пока не выключат. */
    fun showPersistent(
        context: Context,
        title: String,
        subtitle: String,
        expandedText: String,
        position: Int,
        compact: Boolean = true,
        offsetY: Int = 0
    ) {
        val app = context.applicationContext
        if (!isAvailable(app)) return
        persistent = true
        handler.post {
            runCatching {
                showInternal(app, 0, title, subtitle, position, 0L, true, expandedText, true, compact, offsetY)
            }
        }
    }

    fun updatePersistent(
        context: Context,
        title: String,
        subtitle: String,
        expandedText: String,
        position: Int,
        compact: Boolean = true,
        offsetY: Int = 0
    ) {
        if (!persistent) return
        val app = context.applicationContext
        handler.post {
            runCatching {
                showInternal(app, 0, title, subtitle, position, 0L, true, expandedText, true, compact, offsetY)
            }
        }
    }

    fun hidePersistent() {
        persistent = false
        val view = root
        if (view != null) removeNow(view)
    }

    fun isShownPersistent(): Boolean = persistent && root?.parent != null

    fun hide() {
        handler.post { runCatching { hideInternal() } }
    }

    private fun hideInternal() {
        hideTask?.let { handler.removeCallbacks(it) }
        hideTask = null
        val view = root ?: return
        root = null
        rootPersistent = false
        view.animate()
            .alpha(0f)
            .translationY(-dp(view.context, 8f))
            .setDuration(160)
            .withEndAction { runCatching { windowManager?.removeView(view) } }
            .start()
    }

    private fun showInternal(
        context: Context,
        iconRes: Int,
        title: String,
        subtitle: String,
        position: Int,
        durationMs: Long,
        persistent: Boolean,
        expandedText: String,
        keepIcons: Boolean,
        compact: Boolean,
        offsetY: Int
    ) {
        val existing = root
        if (existing != null && existing.parent != null) {
            // Постоянный остров и вспышка правила делят одно окно.
            // Вспышка поверх постоянного острова только обновляет текст и мигает —
            // иначе капсула исчезала бы на 15 секунд до следующего обновления службы.
            val flashOverPersistent = rootPersistent && !persistent
            if (rootPersistent != persistent && !flashOverPersistent) {
                removeNow(existing)
            } else {
                existing.findViewWithTag<TextView>(TAG_TITLE)?.text = title
                existing.findViewWithTag<TextView>(TAG_SUBTITLE)?.text = subtitle
                if (expandedText.isNotBlank()) {
                    existing.findViewWithTag<TextView>(TAG_EXPANDED)?.text = expandedText
                }
                if (iconRes != 0 && !keepIcons) {
                    existing.findViewWithTag<ImageView>(TAG_ICON)?.setImageResource(iconRes)
                }
                applyPosition(existing, position, offsetY)
                if (flashOverPersistent) {
                    // мягкий импульс: видно, что правило сработало, капсула остаётся
                    existing.animate().cancel()
                    existing.scaleX = 1.06f
                    existing.scaleY = 1.06f
                    existing.animate().scaleX(1f).scaleY(1f)
                        .setDuration(260)
                        .setInterpolator(OvershootInterpolator(1.8f))
                        .start()
                    schedulePersistentRestore()
                    return
                }
                if (!persistent) {
                    restartProgress(existing, durationMs)
                    scheduleHide(durationMs)
                }
                return
            }
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val view = buildView(context, iconRes, title, subtitle, expandedText, persistent, compact)

        // Остров никогда не перехватывает касания: окно крошечное (по содержимому)
        // и помечено как «не для пальца». Иначе полоса поверх экрана блокировала бы
        // нажатия — именно это и случилось в 2.2.0.
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        // Капсула ровно по содержимому: ничего лишнего на экране не висит.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        applyLayout(view, params, position, offsetY)

        view.alpha = 0f
        view.translationY = -dp(context, 12f)
        view.scaleX = 0.92f
        runCatching { wm.addView(view, params) }
        root = view
        rootPersistent = persistent
        windowManager = wm

        view.pivotX = view.width / 2f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .setDuration(260)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
            .start()

        if (!persistent) {
            restartProgress(view, durationMs)
            scheduleHide(durationMs)
        }
    }

    /**
     * Живое стекло: два оттенка медленно перетекают друг в друга. Это не картинка
     * и не фейк — оттенок меняется по-настоящему, кадр за кадром.
     */
    private fun startLiveGlass(context: Context, drawable: GradientDrawable) {
        stopLiveGlass()
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 7_400L
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            addUpdateListener { animation ->
                val t = animation.animatedValue as Float
                runCatching {
                    drawable.colors = intArrayOf(
                        blend(COLOR_BG, COLOR_BG_COOL, t),
                        blend(COLOR_BG_WARM, COLOR_BG, t)
                    )
                    drawable.alpha = (226 + (24 * t).toInt()).coerceAtMost(255)
                }
            }
            start()
        }
        glassAnimator = animator
    }

    private fun stopLiveGlass() {
        runCatching { glassAnimator?.cancel() }
        glassAnimator = null
    }

    /** Смешивание двух цветов — для живого стекла и мягких переходов. */
    private fun blend(from: Int, to: Int, t: Float): Int {
        val a = (android.graphics.Color.alpha(from) * (1 - t) + android.graphics.Color.alpha(to) * t).toInt()
        val r = (android.graphics.Color.red(from) * (1 - t) + android.graphics.Color.red(to) * t).toInt()
        val g = (android.graphics.Color.green(from) * (1 - t) + android.graphics.Color.green(to) * t).toInt()
        val b = (android.graphics.Color.blue(from) * (1 - t) + android.graphics.Color.blue(to) * t).toInt()
        return android.graphics.Color.argb(a, r, g, b)
    }

    /** Убрать окно немедленно (без анимации) — при смене режима. */
    private fun removeNow(view: View) {
        hideTask?.let { handler.removeCallbacks(it) }
        hideTask = null
        stopLiveGlass()
        root = null
        runCatching { windowManager?.removeView(view) }
    }

    /**
     * Через несколько секунд возвращаем текст постоянного острова:
     * служба острова обновляет его раз в 15 секунд, а вспышка правила могла
     * подменить подпись — без этого капсула «залипала» бы с текстом правила.
     */
    private fun schedulePersistentRestore() {
        hideTask?.let { handler.removeCallbacks(it) }
        val task = Runnable {
            if (persistent && root?.parent != null) {
                val view = root ?: return@Runnable
                view.findViewWithTag<TextView>(TAG_TITLE)?.text =
                    view.context.getString(com.gsynk.app.R.string.island_default_title)
                view.findViewWithTag<TextView>(TAG_SUBTITLE)?.text =
                    view.context.getString(com.gsynk.app.R.string.island_refreshing)
            }
        }
        hideTask = task
        handler.postDelayed(task, 3_400L)
    }

    private fun scheduleHide(durationMs: Long) {
        hideTask?.let { handler.removeCallbacks(it) }
        val task = Runnable { runCatching { hideInternal() } }
        hideTask = task
        handler.postDelayed(task, durationMs)
    }

    private fun restartProgress(view: View, durationMs: Long) {
        val progress = view.findViewWithTag<View>(TAG_PROGRESS) ?: return
        progress.animate().cancel()
        progress.pivotX = 0f
        progress.scaleX = 1f
        progress.animate().scaleX(0f).setDuration(durationMs).start()
    }

    // ————— позиционирование —————

    private fun applyPosition(view: View, position: Int, offsetY: Int) {
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        applyLayout(view, params, position, offsetY)
    }

    private fun applyLayout(view: View, params: WindowManager.LayoutParams, position: Int, offsetY: Int) {
        val (gravity, marginTop, marginBottom) = when (position) {
            IslandPosition.TOP_LEFT -> Triple(Gravity.TOP or Gravity.START, 12, 0)
            IslandPosition.TOP_CENTER -> Triple(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 6, 0)
            IslandPosition.TOP_RIGHT -> Triple(Gravity.TOP or Gravity.END, 12, 0)
            IslandPosition.MID_LEFT -> Triple(Gravity.CENTER_VERTICAL or Gravity.START, 0, 0)
            IslandPosition.MID_RIGHT -> Triple(Gravity.CENTER_VERTICAL or Gravity.END, 0, 0)
            IslandPosition.BOTTOM_LEFT -> Triple(Gravity.BOTTOM or Gravity.START, 0, 90)
            IslandPosition.BOTTOM_CENTER -> Triple(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 76)
            else -> Triple(Gravity.BOTTOM or Gravity.END, 0, 90)
        }
        params.gravity = gravity
        val density = view.context.resources.displayMetrics.density
        val cutout = cutoutPx(view.context)
        val base = if (hugCamera && cutout > 0) {
            cutout + (1 * density).toInt()
        } else {
            (statusBarPx(view.context) + 2 * density).toInt()
        }
        params.y = (if (gravity and Gravity.TOP != 0) base else 0) + (offsetY * density).toInt()
        params.x = when {
            gravity and Gravity.START != 0 -> (6 * density).toInt()
            gravity and Gravity.END != 0 -> (6 * density).toInt()
            else -> 0
        }
        val bottomGap = (marginBottom * density).toInt()
        if (gravity and Gravity.BOTTOM != 0) {
            params.y = -bottomGap + (offsetY * density).toInt()
        }
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    /**
     * Низ выреза под фронтальную камеру. Именно к нему садится остров 2.0:
     * раньше база считалась от строки состояния, и капсула висела на 100 px ниже.
     */
    private fun cutoutPx(context: Context): Int = runCatching {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val insets = windowManager.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.displayCutout())
        insets.top
    }.getOrDefault(0)

    /** Сколько пикселей занимает вырез камеры. Публично: это показывает экран острова. */
    fun cutoutHeight(context: Context): Int = cutoutPx(context)

    /** Высота строки состояния из системных инсетов (без чтения скрытых ресурсов). */
    private fun statusBarPx(context: Context): Int = runCatching {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val insets = windowManager.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars())
        insets.top.takeIf { it > 0 } ?: dp(context, 28f).toInt()
    }.getOrDefault(dp(context, 28f).toInt())

    // ————— построение вида —————

    /**
     * Широкая низкая капсула: в одну строку иконка, название и значение справа.
     * Снизу — тонкая полоса активности, она бежит строго внутри капсулы.
     */
    private fun buildView(
        context: Context,
        iconRes: Int,
        title: String,
        subtitle: String,
        expandedText: String,
        persistent: Boolean,
        compact: Boolean
    ): View {
        val padH = dp(context, if (compact) 12f else 15f).toInt()
        val padV = dp(context, if (compact) 4f else 5f).toInt()
        val iconSize = dp(context, if (compact) 14f else 17f).toInt()
        val titleSp = if (compact) 11.5f else 13f
        val valueSp = if (compact) 10.5f else 12f
        val expandedSp = if (compact) 9f else 10f

        // Остров — самый скруглённый элемент приложения: капсула целиком скруглена.
        val radius = dp(context, if (persistent) 26f else 22f)
        val glass = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(COLOR_BG, COLOR_BG_WARM)
        ).apply {
            cornerRadius = radius
            setStroke(dp(context, 1f).toInt().coerceAtLeast(1), COLOR_BORDER)
        }
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = glass
            setPadding(padH, padV, padH, padV)
            elevation = dp(context, 8f)
        }
        if (persistent) startLiveGlass(context, glass)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(context).apply {
            tag = TAG_ICON
            if (iconRes != 0) setImageResource(iconRes)
            setColorFilter(COLOR_ACCENT)
        }
        if (iconRes != 0) {
            row.addView(
                icon,
                LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginEnd = dp(context, if (compact) 7f else 9f).toInt()
                }
            )
        }

        val titleView = TextView(context).apply {
            tag = TAG_TITLE
            text = title
            setTextColor(COLOR_TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSp)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        // ширина капсулы — по содержимому: название ограничиваем,
        // а не растягиваем весом (иначе окно снова станет во весь экран)
        val maxText = (context.resources.displayMetrics.widthPixels * 0.42f).toInt()
        titleView.maxWidth = maxText
        row.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // значение справа — то, ради чего остров и нужен: заряд, часы, счётчик
        val subtitleView = TextView(context).apply {
            tag = TAG_SUBTITLE
            text = subtitle
            setTextColor(COLOR_ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, valueSp)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isSingleLine = true
            includeFontPadding = false
        }
        row.addView(
            subtitleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(context, 8f).toInt() }
        )

        outer.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // (раскрытая строка оставлена в разметке только для совместимости тегов)
        val expandedView = TextView(context).apply {
            tag = TAG_EXPANDED
            text = expandedText
            setTextColor(COLOR_TEXT_DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, expandedSp)
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            isVisible = false
        }
        outer.addView(
            expandedView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 3f).toInt() }
        )

        // Полоса внизу капсулы: у вспышки она показывает время, у постоянного
        // острова — просто бежит внутри своей же полосы (за границы не выходит,
        // поэтому никаких «рамок» и «прямоугольников» на экране не появляется).
        val progress = View(context).apply {
                tag = TAG_PROGRESS
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(0xFFA855F7.toInt(), COLOR_GREEN)
                ).apply { cornerRadius = 0f }
            }
        outer.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 2f).toInt().coerceAtLeast(1)
            ).apply { topMargin = dp(context, 3f).toInt() }
        )

        if (persistent) {
            // постоянный остров: полоса мягко гуляет по капсуле, показывая, что он жив
            progress.post(object : Runnable {
                private var direction = 1f
                override fun run() {
                    // окно могли убрать (правило кончилось) — тогда цикл не продлеваем
                    if (progress.parent == null) return
                    val width = progress.width.toFloat()
                    if (width > 0f) {
                        progress.translationX += direction * 2.2f * context.resources.displayMetrics.density
                        if (progress.translationX > width * 0.55f) direction = -1f
                        if (progress.translationX < 0f) direction = 1f
                    }
                    progress.postDelayed(this, 16L)
                }
            })
            progress.scaleX = 0.45f
            progress.pivotX = 0f
        }

        return outer
    }

    private fun dp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density
}
