package com.gsynk.app.core

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Затемнение экрана ниже системного минимума.
 *
 * Штатная яркость Android не опускается ниже примерно 10 процентов, а ночью хочется
 * темнее. Слой-затемнитель рисует поверх всего полупрозрачную чёрную заливку — на
 * AMOLED-экране это ещё и экономит батарею, потому что чёрные пиксели не светятся.
 *
 * Окно сквозное для касаний и без фокуса: оно ничего не блокирует, просто гасит свет.
 * При уходе с экрана слой снимается сам — «залипнуть» он не может.
 */
object ScreenDimmer {

    private var view: View? = null
    private var windowManager: WindowManager? = null

    /** Насколько темнить: 0 — выключено, 90 — почти чёрный экран. */
    fun apply(context: Context, percent: Int) {
        val value = percent.coerceIn(0, 90)
        if (value == 0) {
            hide()
            return
        }
        if (!RegionSupport.overlayAllowed(context)) return

        val wm = windowManager ?: (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return
        val current = view
        if (current == null) {
            val layer = FrameLayout(context).apply {
                setBackgroundColor(Color.BLACK)
                alpha = value / 100f
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            val added = runCatching { wm.addView(layer, params) }.isSuccess
            if (!added) return
            view = layer
            windowManager = wm
        } else {
            current.alpha = value / 100f
        }
    }

    fun hide() {
        val current = view ?: return
        runCatching { windowManager?.removeView(current) }
        view = null
        windowManager = null
    }

    fun isActive(): Boolean = view != null
}
