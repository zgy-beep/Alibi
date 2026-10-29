package app.myzel394.alibi

import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil

/** Captures sensor results, never the optimistic CameraInfo.zoomState/UI state. */
internal class SensorZoomTrace(private val source: String) {
    private data class Frame(val timeNanos: Long, val ratio: Float?, val cropWidth: Int?)
    private val frames = mutableListOf<Frame>()
    private var referenceCropWidth: Int? = null

    @Synchronized fun add(origin: String, result: TotalCaptureResult) {
        if (origin != source) return
        val time = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
        if (frames.lastOrNull()?.timeNanos == time) return
        val ratio = if (Build.VERSION.SDK_INT >= 30) result.get(CaptureResult.CONTROL_ZOOM_RATIO) else null
        val crop = result.get(CaptureResult.SCALER_CROP_REGION)?.width()
        if (referenceCropWidth == null && crop != null) referenceCropWidth = crop
        frames.add(Frame(time, ratio, crop))
    }

    @Synchronized fun hasFrames() = frames.size >= 5
    @Synchronized fun clear() = frames.clear()

    @Synchronized fun lastRatio(): Float? {
        val last = frames.lastOrNull() ?: return null
        // Legacy Camera2 zoom uses crop regions, including some API 30+ OEMs.
        val changedRatio = frames.mapNotNull { it.ratio }.distinct().size > 1
        if (changedRatio) return last.ratio
        return last.cropWidth?.takeIf { it > 0 }?.let { (referenceCropWidth ?: it).toFloat() / it }
            ?: last.ratio
    }

    /** Ignore the stationary beginning/end; measure the moving portion of the real frames. */
    @Synchronized fun assertContinuousAndSave(name: String) {
        val sorted = frames.distinctBy { it.timeNanos }.sortedBy { it.timeNanos }
        val reportsRatio = sorted.mapNotNull { it.ratio }.distinct().size > 2
        val widestCrop = referenceCropWidth?.toFloat() ?: sorted.mapNotNull { it.cropWidth }.maxOrNull()?.toFloat() ?: 1f
        val measured = sorted.mapNotNull { frame ->
            val ratio = if (reportsRatio) frame.ratio else frame.cropWidth?.let { widestCrop / it }
            ratio?.let { frame.timeNanos to it }
        }
        val changed = measured.filterIndexed { index, sample ->
            index == 0 || abs(sample.second - measured[index - 1].second) > 0.001f
        }
        // The first baseline-to-motion gap includes pipeline startup; stationary
        // settling tails are excluded by measuring only the central 80% of motion.
        val low = changed.minOfOrNull { it.second } ?: 1f
        val high = changed.maxOfOrNull { it.second } ?: low
        val moving = changed.filter { it.second > low + (high - low) * 0.1f &&
            it.second < low + (high - low) * 0.9f }
        val gaps = moving.zipWithNext().map { (a, b) -> (b.first - a.first) / 1_000_000.0 }.sorted()
        val p95 = gaps.getOrNull((ceil(gaps.size * 0.95).toInt() - 1).coerceAtLeast(0)) ?: Double.POSITIVE_INFINITY
        val maxStep = moving.zipWithNext().maxOfOrNull { (a, b) -> abs(b.second / a.second - 1f) } ?: 1f
        val summary = "$name frames=${sorted.size} changes=${changed.size} centralChanges=${moving.size} " +
            "p95GapMs=$p95 maxRelativeStep=$maxStep first=$low last=$high"
        val folder = requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("zoom-tests")).apply { mkdirs() }
        File(folder, "$name.csv").writeText("sensorTimestampNanos,zoomRatio\n" +
            measured.joinToString("\n") { "${it.first},${it.second}" })
        File(folder, "$name.txt").writeText(summary)
        Log.i("SensorZoomTrace", summary)
        assertTrue("Too few real zoom steps: $summary", moving.size >= 12)
        assertTrue("Real zoom pauses between steps: $summary", p95 <= 150.0)
        assertTrue("Real zoom jumps instead of easing: $summary", maxStep < 0.15f)
    }
}
