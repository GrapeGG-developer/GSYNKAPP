package com.gsynk.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Сторож острова.
 *
 * В 2.2.0 остров разъехался: окно было шириной 90 % экрана, а под капсулой
 * оставалась длинная прозрачная полоса. Экран перекрывался, пол телефона
 * «отваливался» — кнопки внизу не нажимались. Этот тест держит две вещи:
 * окно острова всегда по размеру содержимого и всегда сквозное для пальца.
 */
class IslandGuardTest {

    private fun islandSource(): String {
        val candidates = listOf(
            File("src/main/java/com/gsynk/app/core/IslandOverlay.kt"),
            File("app/src/main/java/com/gsynk/app/core/IslandOverlay.kt"),
            File("../app/src/main/java/com/gsynk/app/core/IslandOverlay.kt")
        )
        val file = candidates.firstOrNull { it.isFile }
        assertTrue("не нашёл IslandOverlay.kt", file != null)
        return file!!.readText()
    }

    @Test
    fun islandWindowIsWrappedNotFullWidth() {
        val source = islandSource()
        assertTrue(
            "окно острова снова растянуто: ширина должна считаться по содержимому",
            source.contains("WindowManager.LayoutParams.WRAP_CONTENT,")
        )
        assertTrue(
            "окно острова нельзя считать долями экрана — иначе снова получится рамка дисплея",
            !source.contains("screenWidth * 0.9") && !source.contains("widthPixels * 0.9")
        )
    }

    @Test
    fun islandNeverSwallowsTouches() {
        val source = islandSource()
        assertTrue(
            "остров обязан пропускать касания: без FLAG_NOT_TOUCHABLE он перекроет экран",
            source.contains("WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE")
        )
        assertTrue(
            "полноэкранный слой блика возвращать нельзя — он и рисовал невидимый прямоугольник",
            !source.contains("IslandShimmerView")
        )
    }
}
