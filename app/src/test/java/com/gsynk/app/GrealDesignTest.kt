package com.gsynk.app

import com.gsynk.app.ui.Greal
import com.gsynk.app.ui.grealOrbDiameterDp
import com.gsynk.app.ui.grealDepthTransform
import com.gsynk.app.ui.grealWindowBlurDp
import com.gsynk.app.ui.grealWindowRollDegrees
import com.gsynk.app.ui.liquidMinimizeRect
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrealDesignTest {

    @Test
    fun grealIsTheDesignSystem() {
        assertEquals("GREAL — Grape Realism Engine", Greal.VERSION)
        assertEquals(Greal.VERSION, com.gsynk.app.ui.Gioui.UI_VERSION)
    }

    @Test
    fun depthPanelLooksStraightInCenterAndTurnsStronglyAtEdges() {
        val center = grealDepthTransform(0f)
        assertEquals(0f, center.rotationX, 0.0001f)
        assertEquals(1f, center.scale, 0.0001f)
        assertEquals(1f, center.alpha, 0.0001f)

        val past = grealDepthTransform(-1f)
        val future = grealDepthTransform(1f)
        assertTrue(past.rotationX > 34f)
        assertTrue(future.rotationX < -34f)
        assertTrue(past.scale < center.scale && future.scale < center.scale)
        assertTrue(past.alpha < center.alpha && future.alpha < center.alpha)
        assertTrue(past.pivotY > 0.72f && future.pivotY < 0.28f)
        assertTrue(past.translationXD > 0f && future.translationXD < 0f)
        assertTrue(grealDepthTransform(-8f).scale >= 0.78f)
        assertTrue(grealDepthTransform(8f).alpha >= 0.58f)
    }

    @Test
    fun cassetteLinesAndCirclesAreGone() {
        val greal = File("src/main/java/com/gsynk/app/ui/GrealDesign.kt").readText()
        assertFalse("старый кассетный фон вернулся", greal.contains("GrealTapeBackdrop"))
        val backdropStart = greal.indexOf("fun GrealDepthBackdrop(")
        val backdropEnd = greal.indexOf("Объёмная сфера GREAL", backdropStart)
        assertTrue(backdropStart >= 0 && backdropEnd > backdropStart)
        val backdrop = greal.substring(backdropStart, backdropEnd)
        assertFalse("в заднем плане снова линии", backdrop.contains("drawLine("))
        assertFalse("в заднем плане снова круги/овалы кассеты", backdrop.contains("drawCircle(") || backdrop.contains("drawOval("))
        assertFalse("в заднем плане снова контуры", backdrop.contains("drawPath("))
        val textureStart = greal.indexOf("fun GrealTexture(")
        val textureEnd = greal.indexOf("fun GrealActiveCorners(", textureStart)
        assertTrue(textureStart >= 0 && textureEnd > textureStart)
        assertFalse("текстура снова рисует серые линии", greal.substring(textureStart, textureEnd).contains("drawLine("))
    }

    @Test
    fun windowIsBlurredAndRolledOnlyWhileFlying() {
        assertEquals(22f, grealWindowBlurDp(0f), 0.0001f)
        assertEquals(0f, grealWindowBlurDp(1f), 0.0001f)
        assertTrue(grealWindowBlurDp(0.35f) > grealWindowBlurDp(0.75f))
        assertTrue(grealWindowRollDegrees(0.5f) > grealWindowRollDegrees(0f))
        assertTrue(grealWindowRollDegrees(0.5f) > grealWindowRollDegrees(1f))
        assertEquals(0f, grealWindowRollDegrees(1f), 0.0001f)
    }

    @Test
    fun orbAndBottomLabelAlwaysFitInsideTheirCell() {
        listOf(48 to 48, 72 to 72, 84 to 84, 156 to 78, 78 to 156, 240 to 240).forEach { (w, h) ->
            val diameter = grealOrbDiameterDp(w, h)
            assertTrue("сфера шире ячейки $w x $h", diameter <= w)
            assertTrue("сфере и подписи тесно в $w x $h", diameter + 27 <= h || diameter == 30)
            assertTrue(diameter in 30..96)
        }
        assertTrue(grealOrbDiameterDp(156, 78) < grealOrbDiameterDp(156, 156))
    }

    @Test
    fun minimizedWindowStackStaysOnScreen() {
        var previousTop = -1f
        for (index in 0..3) {
            val rect = liquidMinimizeRect(index)
            assertTrue(rect.left >= 0f && rect.top >= 0f)
            assertTrue(rect.left + rect.width <= 1f)
            assertTrue(rect.top + rect.height <= 1f)
            assertTrue(rect.top > previousTop)
            previousTop = rect.top
        }
        assertEquals(liquidMinimizeRect(3), liquidMinimizeRect(99))
    }
}
