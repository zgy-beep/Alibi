package app.myzel394.alibi.services

/**
 * Latest-wins queue for CameraX zoom requests.
 *
 * CameraX applies every [androidx.camera.core.CameraControl.setZoomRatio] request
 * through its camera control pipeline. Sending one request per touch sample
 * makes that pipeline fall behind the fingers and the preview visibly lags the
 * gesture. This queue keeps at most one request in flight and always applies
 * the newest requested ratio next, so the camera never accumulates a backlog.
 */
class ZoomRequestQueue {
    private var pending: Float? = null
    private var inFlight = false

    /** Returns the ratio that should be applied now, or null when an earlier request is still in flight. */
    @Synchronized
    fun submit(ratio: Float): Float? {
        if (inFlight) {
            pending = ratio
            return null
        }
        inFlight = true
        return ratio
    }

    /** Marks the in-flight request finished and returns the newest queued ratio, if any. */
    @Synchronized
    fun complete(): Float? {
        val next = pending
        pending = null
        inFlight = next != null
        return next
    }

    @Synchronized
    fun reset() {
        pending = null
        inFlight = false
    }
}
