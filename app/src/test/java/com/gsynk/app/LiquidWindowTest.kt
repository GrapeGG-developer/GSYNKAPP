package com.gsynk.app

import com.gsynk.app.ui.LIQUID_MINIMIZE_RECT
import com.gsynk.app.ui.LiquidWindowOrigin
import com.gsynk.app.ui.liquidWindowContentAlpha
import com.gsynk.app.ui.liquidWindowCornerDp
import com.gsynk.app.ui.liquidWindowRect
import com.gsynk.app.ui.liquidWindowSeedAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidWindowTest {
    private val tile = LiquidWindowOrigin(
        route = "tool:test",
        title = "тест",
        left = 0.62f,
        top = 0.48f,
        width = 0.20f,
        height = 0.09f
    )

    @Test
    fun windowStartsExactlyInTileAndEndsFullscreen() {
        val start = liquidWindowRect(0f, tile)
        assertEquals(tile.left, start.left, 0.0001f)
        assertEquals(tile.top, start.top, 0.0001f)
        assertEquals(tile.width, start.width, 0.0001f)
        assertEquals(tile.height, start.height, 0.0001f)

        val end = liquidWindowRect(1f, tile)
        assertEquals(0f, end.left, 0.0001f)
        assertEquals(0f, end.top, 0.0001f)
        assertEquals(1f, end.width, 0.0001f)
        assertEquals(1f, end.height, 0.0001f)
    }

    @Test
    fun widthFlowsBeforeHeightSoMorphFeelsLiquid() {
        val middle = liquidWindowRect(0.42f, tile)
        val widthProgress = (middle.width - tile.width) / (1f - tile.width)
        val heightProgress = (middle.height - tile.height) / (1f - tile.height)
        assertTrue(widthProgress > heightProgress + 0.03f)

        var previous = liquidWindowRect(0f, tile)
        for (step in 1..20) {
            val current = liquidWindowRect(step / 20f, tile)
            assertTrue(current.width >= previous.width)
            assertTrue(current.height >= previous.height)
            previous = current
        }
    }

    @Test
    fun tileSeedHandsOverToRealWindowAndCornersSettle() {
        assertEquals(0f, liquidWindowContentAlpha(0f), 0.0001f)
        assertEquals(1f, liquidWindowSeedAlpha(0f), 0.0001f)
        assertEquals(1f, liquidWindowContentAlpha(1f), 0.0001f)
        assertEquals(0f, liquidWindowSeedAlpha(1f), 0.0001f)
        assertTrue(liquidWindowCornerDp(0f) > liquidWindowCornerDp(1f))
        assertEquals(3f, liquidWindowCornerDp(1f), 0.0001f)
    }

    @Test
    fun minimizedTargetStaysInsideRootWindow() {
        assertTrue(LIQUID_MINIMIZE_RECT.left >= 0f)
        assertTrue(LIQUID_MINIMIZE_RECT.top >= 0f)
        assertTrue(LIQUID_MINIMIZE_RECT.left + LIQUID_MINIMIZE_RECT.width <= 1f)
        assertTrue(LIQUID_MINIMIZE_RECT.top + LIQUID_MINIMIZE_RECT.height <= 1f)
    }
}
