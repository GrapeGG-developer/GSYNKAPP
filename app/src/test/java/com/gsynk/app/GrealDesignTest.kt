package com.gsynk.app

import com.gsynk.app.ui.Greal
import com.gsynk.app.ui.grealOrbDiameterDp
import com.gsynk.app.ui.grealTapeTransform
import com.gsynk.app.ui.grealWindowBlurDp
import com.gsynk.app.ui.grealWindowRollDegrees
import com.gsynk.app.ui.liquidMinimizeRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrealDesignTest {

    @Test
    fun grealIsTheDesignSystem() {
        assertEquals("GREAL — Grape Realism Engine", Greal.VERSION)
        assertEquals(Greal.VERSION, com.gsynk.app.ui.Gioui.UI_VERSION)
    }

    @Test
    fun tapePanelLooksStraightInCenterAndCurvesAwayAtEdges() {
        val center = grealTapeTransform(0f)
        assertEquals(0f, center.rotationX, 0.0001f)
        assertEquals(1f, center.scale, 0.0001f)
        assertEquals(1f, center.alpha, 0.0001f)

        val past = grealTapeTransform(-1f)
        val future = grealTapeTransform(1f)
        assertTrue(past.rotationX > 20f)
        assertTrue(future.rotationX < -20f)
        assertTrue(past.scale < center.scale && future.scale < center.scale)
        assertTrue(past.alpha < center.alpha && future.alpha < center.alpha)
        assertTrue(grealTapeTransform(-8f).scale >= 0.84f)
        assertTrue(grealTapeTransform(8f).alpha >= 0.68f)
    }

    @Test
    fun windowIsBlurredAndRolledOnlyWhileFlying() {
        assertEquals(15f, grealWindowBlurDp(0f), 0.0001f)
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
