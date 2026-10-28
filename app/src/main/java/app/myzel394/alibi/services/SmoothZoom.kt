package app.myzel394.alibi.services

import kotlin.math.abs
import kotlin.math.exp

/**
 * Frame-rate independent easing for pinch-to-zoom.
 *
 * Camera hardware applies a zoom request as a discrete jump: sending the raw
 * gesture target on every touch sample makes the preview step between ratios
 * and feel like it trails the fingers. This helper keeps a *target* ratio
 * (which is what the gesture accumulates into) and a *current* ratio that eases
 * toward it every frame, so callers can submit small intermediate ratios while
 * the fingers move and still end exactly on the gesture target.
 *
 * The easing is exponential and expressed in elapsed milliseconds, so the same
 * numbers come out of a 60 Hz and a 120 Hz display: two 8 ms steps add up to a
 * single 16 ms step as far as the resulting ratio is concerned.
 *
 * The class is deliberately free of Android and CameraX types so the math can
 * be unit tested on the JVM.
 */
class SmoothZoom(
    initialRatio: Float = 1f,
    private val timeConstantMillis: Float = DEFAULT_TIME_CONSTANT_MILLIS,
    private val snapEpsilon: Float = DEFAULT_SNAP_EPSILON,
) {
    var minRatio: Float = 1f
        private set

    var maxRatio: Float = 1f
        private set

    /** Ratio the user is asking for; accumulated by [scaleBy]. */
    var targetRatio: Float = initialRatio
        private set

    /** Ratio that was last reported to the camera / UI. */
    var currentRatio: Float = initialRatio
        private set

    val isSettled: Boolean
        get() = abs(targetRatio - currentRatio) <= snapEpsilon

    /**
     * Updates the device supported range. Called once the bound camera reports
     * its [androidx.camera.core.ZoomState].
     */
    fun setBounds(min: Float, max: Float) {
        val lower = min.takeIf { it.isFinite() && it > 0f } ?: 1f
        val upper = max.takeIf { it.isFinite() && it >= lower } ?: lower
        minRatio = lower
        maxRatio = upper
        targetRatio = targetRatio.coerceIn(lower, upper)
        currentRatio = currentRatio.coerceIn(lower, upper)
    }

    /**
     * Applies a relative pinch factor on top of the *target* ratio, so a fast
     * pinch accumulates instead of being clamped by the lagging current ratio.
     *
     * @return the new target ratio.
     */
    fun scaleBy(factor: Float): Float {
        if (!factor.isFinite() || factor <= 0f) {
            return targetRatio
        }
        targetRatio = (targetRatio * factor).coerceIn(minRatio, maxRatio)
        return targetRatio
    }

    /** Sets an absolute target ratio; the next [advance] calls ease toward it. */
    fun setTarget(ratio: Float): Float {
        if (!ratio.isFinite()) {
            return targetRatio
        }
        targetRatio = ratio.coerceIn(minRatio, maxRatio)
        return targetRatio
    }

    /**
     * Jumps to [ratio] immediately. Used for absolute inputs (sliders, step
     * buttons, restored zoom) where easing would feel unresponsive.
     */
    fun snapTo(ratio: Float): Float {
        setTarget(ratio)
        currentRatio = targetRatio
        return currentRatio
    }

    /**
     * Advances the easing by [deltaMillis] and returns the new current ratio.
     * The result never passes the target; when the remaining distance is below
     * [snapEpsilon] the current ratio snaps onto the target.
     */
    fun advance(deltaMillis: Float): Float {
        if (!deltaMillis.isFinite() || deltaMillis <= 0f || isSettled) {
            if (isSettled) {
                currentRatio = targetRatio
            }
            return currentRatio
        }
        val tau = timeConstantMillis.coerceAtLeast(1f)
        val remainingFraction = exp(-deltaMillis / tau)
        val next = targetRatio + (currentRatio - targetRatio) * remainingFraction
        currentRatio = if (abs(targetRatio - next) <= snapEpsilon) {
            targetRatio
        } else {
            next.coerceIn(minRatio, maxRatio)
        }
        return currentRatio
    }

    fun reset(ratio: Float = minRatio) {
        targetRatio = ratio.coerceIn(minRatio, maxRatio)
        currentRatio = targetRatio
    }

    companion object {
        /**
         * Easing time constant. With ~60 ms a pinch crosses the remaining
         * distance almost fully in about three frames, which reads as instant
         * while still hiding single-frame zoom steps.
         */
        const val DEFAULT_TIME_CONSTANT_MILLIS = 60f

        /** Ratios closer than this to the target are applied as-is. */
        const val DEFAULT_SNAP_EPSILON = 0.01f
    }
}
