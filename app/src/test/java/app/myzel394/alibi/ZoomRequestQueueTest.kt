package app.myzel394.alibi

import app.myzel394.alibi.services.ZoomRequestQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoomRequestQueueTest {
    @Test
    fun firstRequestStartsImmediately() {
        val queue = ZoomRequestQueue()
        assertEquals(1.5f, queue.submit(1.5f))
    }

    @Test
    fun requestsWhileInFlightKeepOnlyNewest() {
        val queue = ZoomRequestQueue()
        assertEquals(1.2f, queue.submit(1.2f))
        assertNull(queue.submit(1.4f))
        assertNull(queue.submit(1.8f))
        assertNull(queue.submit(2.4f))

        assertEquals(2.4f, queue.complete())
    }

    @Test
    fun completeWithoutPendingRequestDoesNotStartAnother() {
        val queue = ZoomRequestQueue()
        assertEquals(1.2f, queue.submit(1.2f))
        assertNull(queue.complete())
    }

    @Test
    fun queueAcceptsANewRequestOnlyAfterCompletion() {
        val queue = ZoomRequestQueue()
        assertEquals(1.1f, queue.submit(1.1f))
        assertNull(queue.complete())
        assertEquals(2.4f, queue.submit(2.4f))
    }

    @Test
    fun resetDropsPendingRequestAndInFlightFlag() {
        val queue = ZoomRequestQueue()
        assertEquals(1.2f, queue.submit(1.2f))
        assertNull(queue.submit(2.2f))
        queue.reset()

        assertNull(queue.complete())
        assertEquals(3.2f, queue.submit(3.2f))
    }
}
