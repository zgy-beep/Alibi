package app.myzel394.alibi

import app.myzel394.alibi.services.ZoomUiUpdateThrottle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomUiUpdateThrottleTest {
    @Test
    fun firstFrameIsRenderedSoTheOverlayNeverShowsStaleZoom() {
        val throttle = ZoomUiUpdateThrottle()

        assertTrue(throttle.shouldRender(1.2f, nowMillis = 1_000L, force = false))
    }

    @Test
    fun subPerceptualChangesAreDropped() {
        val throttle = ZoomUiUpdateThrottle()
        throttle.markRendered(2f, nowMillis = 1_000L)

        // 0.2 % of 2.0 is below the ~0.5 % visibility threshold.
        assertFalse(throttle.shouldRender(2.004f, nowMillis = 2_000L, force = false))
    }

    @Test
    fun thresholdScalesWithTheRatio() {
        val throttle = ZoomUiUpdateThrottle()
        throttle.markRendered(8f, nowMillis = 1_000L)

        // 0.125 % at 8x is invisible, 6 % is a real change.
        assertFalse(throttle.shouldRender(8.01f, nowMillis = 2_000L, force = false))
        assertTrue(throttle.shouldRender(8.5f, nowMillis = 2_000L, force = false))
    }

    @Test
    fun visibleChangeIsDeferredUntilTheMinimumIntervalElapsed() {
        val throttle = ZoomUiUpdateThrottle()
        throttle.markRendered(1f, nowMillis = 1_000L)

        assertFalse(throttle.shouldRender(1.5f, nowMillis = 1_020L, force = false))
        assertTrue(throttle.shouldRender(1.5f, nowMillis = 1_050L, force = false))
    }

    @Test
    fun forceAlwaysRendersEvenForTinyChanges() {
        val throttle = ZoomUiUpdateThrottle()
        throttle.markRendered(3f, nowMillis = 1_000L)

        assertTrue(throttle.shouldRender(3.0001f, nowMillis = 1_001L, force = true))
    }

    @Test
    fun markRenderedMovesBothTheRatioAndTheIntervalReference() {
        val throttle = ZoomUiUpdateThrottle()
        throttle.markRendered(1f, nowMillis = 1_000L)
        assertTrue(throttle.shouldRender(1.5f, nowMillis = 1_100L, force = false))
        throttle.markRendered(1.5f, nowMillis = 1_100L)

        // Too small for the default 0.5 % threshold and too soon for the interval.
        assertFalse(throttle.shouldRender(1.505f, nowMillis = 1_200L, force = false))
        assertFalse(throttle.shouldRender(1.8f, nowMillis = 1_120L, force = false))
        assertTrue(throttle.shouldRender(1.8f, nowMillis = 1_150L, force = false))
    }

    @Test
    fun invalidRatiosAreNeverRendered() {
        val throttle = ZoomUiUpdateThrottle()

        assertFalse(throttle.shouldRender(Float.NaN, nowMillis = 1_000L, force = false))
        assertFalse(throttle.shouldRender(Float.POSITIVE_INFINITY, 1_000L, force = false))
        throttle.markRendered(Float.NaN, nowMillis = 1_000L)

        // Nothing was rendered yet, so the next real frame is shown immediately.
        assertTrue(throttle.shouldRender(1f, nowMillis = 1_000L, force = false))
    }

    @Test
    fun resetAllowsTheNextFrameToRenderImmediately() {
        val throttle = ZoomUiUpdateThrottle()
        throttle.markRendered(2f, nowMillis = 1_000L)
        throttle.reset()

        assertTrue(throttle.shouldRender(2f, nowMillis = 1_050L, force = false))
    }
}
