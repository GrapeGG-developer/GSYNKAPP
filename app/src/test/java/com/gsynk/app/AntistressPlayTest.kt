package com.gsynk.app

import com.gsynk.app.core.GrealPlay
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Математика сохранённого приложения «антистресс»: инерция, границы и эффекты. */
class AntistressPlayTest {

    @Test
    fun carouselWrapsForeverAndNeverLeavesTheScene() {
        assertEquals(0f, GrealPlay.wrapDegrees(360f), 0.0001f)
        assertEquals(1f, GrealPlay.wrapDegrees(721f), 0.0001f)
        assertEquals(359f, GrealPlay.wrapDegrees(-1f), 0.0001f)
        listOf(0f, 37f, 180f, 721f, 1080f).forEach { angle ->
            val poses = GrealPlay.carouselPoses(GrealPlay.CAROUSEL_TILES, angle, 20f)
            assertEquals(GrealPlay.CAROUSEL_TILES, poses.size)
            assertEquals("дальние плитки должны рисоваться раньше ближних", poses, poses.sortedBy { it.depth })
            poses.forEach { pose ->
                assertTrue("плитка ушла по X: ${pose.x}", pose.x >= -1f && pose.x <= 1f)
                assertTrue("плитка ушла по Y: ${pose.y}", pose.y >= -0.5f && pose.y <= 0.5f)
                assertTrue("масштаб сломался: ${pose.scale}", pose.scale in 0.4f..1.2f)
                assertTrue("прозрачность сломалась: ${pose.alpha}", pose.alpha in 0f..1f)
                assertTrue("глубина сломалась: ${pose.depth}", pose.depth >= -1.01f && pose.depth <= 1.01f)
            }
        }
    }

    @Test
    fun flingHasInertiaAndReturnsToIdle() {
        val dragged = GrealPlay.step(300f, 500f, 0.016f, 24f, dragging = true)
        assertEquals(300f, dragged.angle, 0.0001f)
        assertEquals(500f, dragged.velocity, 0.0001f)
        var state = GrealPlay.SpinState(10f, 700f)
        repeat(180) {
            state = GrealPlay.step(state.angle, state.velocity, 0.016f, 24f, dragging = false)
        }
        assertTrue("инерция не вернулась к фоновому вращению", abs(state.velocity - 24f) < 0.5f)
        assertTrue("угол вышел из бесконечного кольца", state.angle in 0f..360f)
        assertEquals(GrealPlay.MAX_FLING_DPS, GrealPlay.flingVelocity(100000f, 0.016f), 0.0001f)
        assertEquals(-GrealPlay.MAX_FLING_DPS, GrealPlay.flingVelocity(-100000f, 0.016f), 0.0001f)
        var tilt = 20f
        repeat(80) { tilt = GrealPlay.relaxTilt(tilt, 0.016f) }
        assertTrue("наклон не расслабился", tilt in 0f..10f)
        assertTrue(
            "быстрый темп должен быть быстрее спокойного",
            GrealPlay.idleSpeed(GrealPlay.Mode.CAROUSEL, 100, 2) >
                GrealPlay.idleSpeed(GrealPlay.Mode.CAROUSEL, 100, 0)
        )
    }

    @Test
    fun effectBudgetIsAdaptiveAndBounded() {
        val low = GrealPlay.effectBudget(0, false)
        val middle = GrealPlay.effectBudget(1, false)
        val high = GrealPlay.effectBudget(2, true)
        assertTrue(low >= 6 && low <= middle && middle <= high && high <= 22)
        assertTrue(GrealPlay.rippleLimit(999) <= 28)
        assertTrue(GrealPlay.carouselCount(0) >= 8 && GrealPlay.carouselCount(999) <= 16)
    }

    @Test
    fun bubblesAreStableHittableAndRespawn() {
        val first = GrealPlay.bubblePose(3, GrealPlay.BUBBLE_COUNT, 5.25f, 7331)
        assertEquals(first, GrealPlay.bubblePose(3, GrealPlay.BUBBLE_COUNT, 5.25f, 7331))
        assertTrue(first.x in 0.09f..0.91f)
        assertTrue(first.y in 0.09f..0.91f)
        assertTrue(first.radius in 0.05f..0.09f)
        assertTrue(first.tone in 0..4)
        assertTrue(GrealPlay.hitBubble(first, first.x, first.y, 400f, 800f))
        assertFalse(GrealPlay.hitBubble(first, 0f, 0f, 400f, 800f))
        assertTrue(GrealPlay.bubblePopScale(GrealPlay.POP_LIFETIME / 2f) > 1f)
        assertTrue(GrealPlay.bubblePopAlpha(0f) < 1f)
        assertEquals(1f, GrealPlay.bubblePopAlpha(GrealPlay.POP_LIFETIME * 2f), 0.0001f)
    }

    @Test
    fun ripplesDecayAndStayInsideBounds() {
        val now = 10f
        val ripples = listOf(
            GrealPlay.Ripple(1, 0.5f, 0.5f, 9f, 0),
            GrealPlay.Ripple(2, 0.2f, 0.2f, 8f, 1)
        )
        val live = GrealPlay.decayRipples(ripples, now)
        assertEquals(1, live.size)
        assertEquals(1L, live.first().id)
        assertEquals(1f, GrealPlay.rippleAlpha(0f), 0.0001f)
        assertEquals(0f, GrealPlay.rippleAlpha(2f), 0.0001f)
        assertTrue(GrealPlay.rippleRadius(2f, 100f) <= 100f)
    }

    @Test
    fun antistressIsOneDirectFullscreenAppOnClassicTiles() {
        val screen = File("src/main/java/com/gsynk/app/ui/GrealAntistressScreen.kt").readText()
        assertTrue(screen.contains("GrealInfiniteCarousel("))
        assertTrue(screen.contains("GrealBubbleField("))
        assertTrue(screen.contains("GrealRippleField("))
        assertTrue(screen.contains("PrgGlassSheen("))
        assertFalse("вложенный скролл в полноэкранном приложении запрещён", screen.contains("verticalScroll"))
        assertFalse(screen.contains("GrapeScreen("))
        val router = File("src/main/java/com/gsynk/app/ui/GiouiScreens.kt").readText()
        assertTrue(router.contains("\"antistress\" -> { GrealAntistressScreen(onBack); return true }"))
    }
}
