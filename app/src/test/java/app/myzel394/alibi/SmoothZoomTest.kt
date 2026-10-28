package app.myzel394.alibi

import app.myzel394.alibi.services.SmoothZoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmoothZoomTest {
    @Test
    fun scaleByAccumulatesOnTargetNotOnCurrentRatio() {
        val zoom = SmoothZoom(initialRatio = 1f)
        zoom.setBounds(1f, 10f)

        zoom.scaleBy(1.5f)
        zoom.scaleBy(1.5f)

        assertEquals(2.25f, zoom.targetRatio, 0.0001f)
        // The current ratio only moves when a frame is advanced.
        assertEquals(1f, zoom.currentRatio, 0.0001f)
    }

    @Test
    fun advanceEasesTowardTargetAndSettles() {
        val zoom = SmoothZoom(initialRatio = 1f)
        zoom.setBounds(1f, 10f)
        zoom.setTarget(2f)

        val first = zoom.advance(16f)
        assertTrue("first step should move", first > 1f)
        assertTrue("first step must not overshoot", first < 2f)

        var ratio = first
        repeat(60) { ratio = zoom.advance(16f) }

        assertEquals(2f, ratio, SmoothZoom.DEFAULT_SNAP_EPSILON)
        assertTrue(zoom.isSettled)
    }

    @Test
    fun easingIsFrameRateIndependent() {
        val sixty = SmoothZoom(initialRatio = 1f)
        sixty.setBounds(1f, 10f)
        sixty.setTarget(3f)
        repeat(4) { sixty.advance(16f) }

        val oneTwenty = SmoothZoom(initialRatio = 1f)
        oneTwenty.setBounds(1f, 10f)
        oneTwenty.setTarget(3f)
        repeat(8) { oneTwenty.advance(8f) }

        assertEquals(sixty.currentRatio, oneTwenty.currentRatio, 0.01f)
    }

    @Test
    fun boundsAreClamped() {
        val zoom = SmoothZoom(initialRatio = 1f)
        zoom.setBounds(1f, 4f)

        zoom.scaleBy(100f)
        assertEquals(4f, zoom.targetRatio, 0.0001f)

        zoom.scaleBy(0.0001f)
        assertEquals(1f, zoom.targetRatio, 0.0001f)

        zoom.snapTo(9f)
        assertEquals(4f, zoom.currentRatio, 0.0001f)
    }

    @Test
    fun snapToAppliesImmediately() {
        val zoom = SmoothZoom(initialRatio = 1f)
        zoom.setBounds(1f, 10f)

        zoom.snapTo(2.5f)

        assertEquals(2.5f, zoom.currentRatio, 0.0001f)
        assertTrue(zoom.isSettled)
    }

    @Test
    fun invalidInputsAreIgnored() {
        val zoom = SmoothZoom(initialRatio = 1.5f)
        zoom.setBounds(1f, 10f)

        zoom.scaleBy(0f)
        zoom.scaleBy(-2f)
        zoom.scaleBy(Float.NaN)
        zoom.setTarget(Float.POSITIVE_INFINITY)
        zoom.advance(0f)
        zoom.advance(-16f)

        assertEquals(1.5f, zoom.targetRatio, 0.0001f)
        assertEquals(1.5f, zoom.currentRatio, 0.0001f)
    }

    @Test
    fun setBoundsPullsRatiosIntoTheSupportedRange() {
        val zoom = SmoothZoom(initialRatio = 1f)
        zoom.setBounds(1f, 10f)
        zoom.snapTo(8f)
        zoom.setBounds(1f, 4f)

        assertEquals(4f, zoom.targetRatio, 0.0001f)
        assertEquals(4f, zoom.currentRatio, 0.0001f)
        assertFalse(zoom.targetRatio > zoom.maxRatio)
    }
}
