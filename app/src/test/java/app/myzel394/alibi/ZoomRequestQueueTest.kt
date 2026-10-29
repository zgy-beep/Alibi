package app.myzel394.alibi

import app.myzel394.alibi.services.SmoothZoom
import app.myzel394.alibi.services.ZoomRequestQueue
import org.junit.Assert.*
import org.junit.Test

class ZoomRequestQueueTest {
    @Test fun slowOrMissingHardwareAcknowledgementsNeverBlockAnimation() {
        val queue = ZoomRequestQueue()
        // No completion is delivered, modelling the phone's 500–700 ms future.
        val sent = (0..60).mapNotNull { frame ->
            queue.submit(1f + frame / 60f, frame * 16L)?.let { frame * 16L to it }
        }
        assertEquals(31, sent.size)
        assertTrue(sent.zipWithNext().all { (a, b) -> b.first - a.first == 32L })
        assertEquals(2f, sent.last().second, 0f)
    }

    @Test fun samplesInsideOneFrameAreReplacedWithTheLatestRatio() {
        val queue = ZoomRequestQueue()
        assertEquals(1.2f, queue.submit(1.2f, 0))
        assertNull(queue.submit(1.4f, 8))
        assertNull(queue.submit(1.8f, 16))
        assertEquals(2.4f, queue.submit(2.4f, 32))
    }

    @Test fun finalFrameBypassesPacingAndCannotBeStranded() {
        val queue = ZoomRequestQueue()
        assertEquals(1.2f, queue.submit(1.2f, 0))
        assertNull(queue.submit(1.4f, 16))
        assertEquals(1.5f, queue.submit(1.5f, 17, force = true))
        assertNull(queue.submit(1.5f, 100))
    }

    @Test fun absoluteInputAndDirectionReversalReplaceEarlierTargetsImmediately() {
        val queue = ZoomRequestQueue()
        assertEquals(3f, queue.submit(3f, 0))
        assertNull(queue.submit(4f, 8))
        assertEquals(1f, queue.submit(1f, 9, force = true))
        assertNull(queue.submit(1f, 64))
        assertEquals(0.8f, queue.submit(0.8f, 65))
    }

    @Test fun resetAdmitsSameRatioImmediatelyForTheNewCamera() {
        val queue = ZoomRequestQueue()
        queue.submit(2f, 0)
        queue.reset()
        assertEquals(2f, queue.submit(2f, 1))
        assertNull(queue.submit(3f, 2))
    }

    @Test fun invalidInputsDoNotConsumeTheNextFrame() {
        val queue = ZoomRequestQueue()
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f)) {
            assertNull(queue.submit(invalid, 0, force = true))
        }
        assertEquals(1f, queue.submit(1f, 0))
    }

    @Test fun pacedEasingRemainsContinuousAndReachesTheExactTarget() {
        val zoom = SmoothZoom().apply { setBounds(1f, 10f) }
        val queue = ZoomRequestQueue()
        val sent = mutableListOf<Float>()
        repeat(120) { frame ->
            if (frame < 90) zoom.scaleBy(1.01f)
            val ratio = zoom.advance(16f)
            queue.submit(ratio, frame * 16L, force = zoom.isSettled)?.let(sent::add)
        }
        assertTrue(sent.size >= 45)
        assertTrue(sent.zipWithNext().all { (a, b) -> b >= a && b / a < 1.05f })
        assertEquals(zoom.targetRatio, sent.last(), 0.0001f)
    }
}
