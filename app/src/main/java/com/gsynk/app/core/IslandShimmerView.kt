package com.gsynk.app.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import androidx.core.graphics.withTranslation

/**
 * Живое оформление острова: по прямоугольной капсуле бежит узкий блик, по нижней
 * кромке скользит линия, а рамка мягко дышит яркостью.
 *
 * Только прямые линии и градиенты — никаких кругов и частиц.
 */
class IslandShimmerView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val startedAt = System.currentTimeMillis()
    private var sweep: LinearGradient? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val time = (System.currentTimeMillis() - startedAt) / 1000f
        val bandWidth = (w * 0.30f).coerceAtLeast(1f)

        // 1) бегущий блик: узкая светлая полоса проходит капсулу за 2,4 секунды.
        // Градиент создаётся один раз, положение задаётся сдвигом холста —
        // так в кадре не появляется новых объектов.
        if (sweep == null) {
            sweep = LinearGradient(
                0f, 0f, bandWidth, h,
                intArrayOf(Color.TRANSPARENT, 0x26FFFFFF, Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val travel = (time % 2.4f) / 2.4f
        val bandX = -bandWidth + travel * (w + bandWidth)
        paint.style = Paint.Style.FILL
        paint.shader = sweep
        canvas.withTranslation(bandX, 0f) {
            drawRect(-bandX, 0f, w - bandX, h, paint)
        }
        paint.shader = null

        // 2) нижняя кромка: тонкая линия, скользящая слева направо
        val lineX = ((time % 3.6f) / 3.6f) * w
        paint.color = 0x66C9A8FF.toInt()
        canvas.drawRect(lineX, h - dp(1.5f), (lineX + w * 0.22f).coerceAtMost(w), h, paint)

        // 3) дыхание рамки
        val breath = (0.30f + 0.30f * kotlin.math.sin(time * 1.7f)).coerceIn(0f, 1f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = Color.argb((breath * 255).toInt(), 168, 85, 247)
        canvas.drawRect(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f), paint)

        postInvalidateOnAnimation()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
