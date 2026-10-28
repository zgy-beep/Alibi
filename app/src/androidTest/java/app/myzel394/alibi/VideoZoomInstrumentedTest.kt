package app.myzel394.alibi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.ui.components.RecorderScreen.atoms.CameraPreview
import app.myzel394.alibi.ui.models.VideoRecorderModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Device acceptance for the video-mode zoom work:
 *  - a pinch on the live viewfinder reports a zoom ratio (preview path), and
 *  - the ratio chosen in the preview is applied to the camera that records the
 *    video file (preview -> intent -> service path).
 */
@RunWith(AndroidJUnit4::class)
class VideoZoomInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    private companion object {
        const val ZOOM_TEST_TAG = "VideoZoomTest"
        const val REQUESTED_ZOOM = 3f
    }

    @Test
    fun pinchOnVideoPreviewReportsZoomChanges() {
        grantCameraPermission()

        val lastRatio = AtomicReference(1f)
        val bound = AtomicBoolean(false)
        val zoomed = AtomicBoolean(false)

        composeRule.setContent {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onZoomChanged = { ratio, minRatio, maxRatio ->
                    lastRatio.set(ratio)
                    if (minRatio > 0f && maxRatio >= minRatio) {
                        bound.set(true)
                    }
                    if (ratio > 1.05f) {
                        zoomed.set(true)
                    }
                },
            )
        }

        assertTrue(
            "The video preview should bind the camera: bound=${bound.get()}",
            waitUntil(20_000) { bound.get() },
        )
        val before = lastRatio.get()
        composeRule.onRoot().performTouchInput {
            val center = center
            pinch(
                start0 = center + Offset(-120f, 0f),
                end0 = center + Offset(-420f, 0f),
                start1 = center + Offset(120f, 0f),
                end1 = center + Offset(420f, 0f),
            )
        }

        assertTrue(
            "Pinch should raise the reported zoom ratio: before=$before " +
                "after=${lastRatio.get()}",
            waitUntil(5_000) { zoomed.get() },
        )
        captureScreenshot("video-preview-pinch")
        Log.i(
            ZOOM_TEST_TAG,
            "preview pinch before=$before after=${lastRatio.get()}",
        )
    }

    @Test
    fun videoRecordingStartsWithChosenZoomRatio() {
        grantCameraPermission()

        val model = VideoRecorderModel()
        instrumentation.runOnMainSync {
            model.init(context)
            model.onPreviewZoomChanged(REQUESTED_ZOOM, 1f, 10f)
        }
        assertTrue(
            "Preview zoom should be stored in the model: ${model.zoomRatio}",
            abs(model.zoomRatio - REQUESTED_ZOOM) < 0.001f,
        )

        instrumentation.runOnMainSync {
            model.startRecording(context, AppSettings.getDefaultInstance())
        }
        try {
            assertTrue(
                "The recording service should bind the camera",
                waitUntil(30_000) { model.recorderService?.cameraControl != null },
            )
            assertTrue(
                "The chosen preview zoom should be applied to the recording camera: " +
                    "applied=${model.recorderService?.cameraZoomRatio}",
                waitUntil(10_000) {
                    val service = model.recorderService ?: return@waitUntil false
                    abs(service.cameraZoomRatio - REQUESTED_ZOOM) < 0.2f
                },
            )
            val service = requireNotNull(model.recorderService)
            Log.i(
                ZOOM_TEST_TAG,
                "recording zoom applied=${service.cameraZoomRatio} " +
                    "range=${service.cameraControl?.zoomMinRatio}.." +
                    "${service.cameraControl?.zoomMaxRatio}",
            )
            assertTrue(
                "Camera-side zoom state should follow the recording zoom: " +
                    "${service.cameraControl?.camera?.cameraInfo?.zoomState?.value?.zoomRatio}",
                waitUntil(10_000) {
                    val ratio = service.cameraControl?.camera
                        ?.cameraInfo?.zoomState?.value?.zoomRatio
                    ratio != null && abs(ratio - REQUESTED_ZOOM) < 0.2f
                },
            )
        } finally {
            runCatching { runBlocking { model.stopRecording(context) } }
            runCatching { model.destroyService(context) }
        }
    }

    private fun grantCameraPermission() {
        repeat(3) {
            val descriptor = instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} ${Manifest.permission.CAMERA}"
            )
            // Drain the command output so `pm grant` has finished before the check.
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            descriptor.close()
            if (waitUntil(2_000) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                }
            ) {
                return
            }
        }
        assertTrue(
            "Camera permission must be granted",
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    /**
     * Saves a device screenshot through the shell user. The PNG must outlive the
     * test APK uninstall, so it goes to /sdcard instead of the app sandbox.
     */
    private fun captureScreenshot(name: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(
            "screencap -p /sdcard/$name.png"
        )
        java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        descriptor.close()
        Log.i(ZOOM_TEST_TAG, "screenshot saved to /sdcard/$name.png")
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return true
            }
            Thread.sleep(50)
        }
        return condition()
    }
}
