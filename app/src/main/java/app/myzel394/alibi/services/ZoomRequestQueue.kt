package app.myzel394.alibi.services

/**
 * Frame-paced, latest-wins admission for CameraX zoom requests.
 *
 * A CameraX zoom future waits for a repeating capture result, which can take
 * several hundred milliseconds. It must never gate the next animation frame.
 * Callers offer the current eased ratio on each ticker frame; samples inside
 * the admission interval are superseded by the next sample, not buffered.
 * CameraX itself supersedes the previous unfinished zoom operation.
 * Absolute inputs and the last animation frame bypass the interval. Futures
 * have no callback into this class, so stale completions cannot replay targets.
 */
class ZoomRequestQueue(private val intervalMillis: Long = 32L) {
    private var lastSubmittedAtMillis: Long? = null
    private var lastRatio = Float.NaN

    /** Uses a monotonic clock. Hardware acknowledgements never gate admission. */
    @Synchronized
    fun submit(ratio: Float, nowMillis: Long, force: Boolean = false): Float? {
        if (!ratio.isFinite() || ratio <= 0f || ratio == lastRatio) {
            return null
        }
        val lastTime = lastSubmittedAtMillis
        if (!force && lastTime != null && nowMillis - lastTime < intervalMillis) {
            return null
        }
        lastSubmittedAtMillis = nowMillis
        lastRatio = ratio
        return ratio
    }

    @Synchronized
    fun reset() {
        lastSubmittedAtMillis = null
        lastRatio = Float.NaN
    }
}
