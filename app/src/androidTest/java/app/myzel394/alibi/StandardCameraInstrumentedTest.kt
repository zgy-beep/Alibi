package app.myzel394.alibi

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.enums.RecorderState
import app.myzel394.alibi.services.PhotoCaptureMode
import app.myzel394.alibi.services.PhotoCaptureService
import app.myzel394.alibi.services.PhotoCaptureStatus
import app.myzel394.alibi.ui.models.VideoRecorderModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Device acceptance for the 0.6.8 standard camera mode services:
 *  - the photo service supports a visible STANDARD session with tap-to-focus,
 *    published zoom bounds and runtime front/back switching;
 *  - the video service keeps recording while its viewfinder surface is detached
 *    and accepts live zoom changes that reach the camera.
 */
@RunWith(AndroidJUnit4::class)
class StandardCameraInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    private companion object {
        const val TAG = "StandardCameraTest"
    }

    @Test
    fun standardPhotoSessionSupportsFocusZoomAndLensSwitch() {
        grantCameraPermission()
        val service = bindPhotoService()
        try {
            ContextCompat.startForegroundService(
                context,
                PhotoCaptureService.createStartIntent(
                    context = context,
                    settings = AppSettings.getDefaultInstance(),
                    mode = PhotoCaptureMode.STANDARD,
                ),
            )
            assertTrue(
                "STANDARD photo session should become ready: ${service.state}",
                waitUntil(20_000) {
                    service.state.mode == PhotoCaptureMode.STANDARD &&
                        service.state.status == PhotoCaptureStatus.READY
                },
            )

            val focusResult = AtomicReference<Boolean?>(null)
            service.onFocusChanged = { focusResult.set(it) }
            instrumentation.runOnMainSync {
                service.setMeteringPointFactory(
                    SurfaceOrientedMeteringPointFactory(1080f, 2424f)
                )
                service.requestFocus(540f, 1212f)
            }
            assertTrue(
                "Tap-to-focus should report a result",
                waitUntil(10_000) { focusResult.get() != null },
            )
            assertTrue(
                "Focus range should be published: ${service.state.zoomMinRatio}.." +
                    "${service.state.zoomMaxRatio}",
                service.state.zoomMinRatio > 0f &&
                    service.state.zoomMaxRatio >= service.state.zoomMinRatio,
            )
            assertTrue(
                "Current zoom should stay inside the published range: " +
                    "${service.state.zoomRatio}",
                service.state.zoomRatio >= service.state.zoomMinRatio &&
                    service.state.zoomRatio <= service.state.zoomMaxRatio,
            )

            val cameras = app.myzel394.alibi.ui.utils.CameraInfo.queryAvailableCameras(context)
            val hasFrontCamera = cameras.any {
                it.lens == app.myzel394.alibi.ui.utils.CameraInfo.Lens.FRONT
            }
            if (hasFrontCamera) {
                instrumentation.runOnMainSync {
                    service.reconfigure(lensFacing = CameraSelector.LENS_FACING_FRONT)
                }
                assertTrue(
                    "Lens switch should rebind the front camera: " +
                        "lens=${service.state.lensFacing} status=${service.state.status}",
                    waitUntil(20_000) {
                        service.state.status == PhotoCaptureStatus.READY &&
                            service.state.lensFacing == CameraSelector.LENS_FACING_FRONT
                    },
                )
            } else {
                // Single-camera devices hide the switch; exercise the flash
                // reconfigure path of the same runtime-rebind machinery instead.
                Log.i(TAG, "single-camera device: exercising flash reconfigure instead")
                instrumentation.runOnMainSync {
                    service.reconfigure(flashMode = app.myzel394.alibi.db.PhotoFlashMode.ON)
                }
                assertTrue(
                    "Flash reconfigure should rebind: status=${service.state.status} " +
                        "flash=${service.state.flashMode}",
                    waitUntil(20_000) {
                        service.state.status == PhotoCaptureStatus.READY &&
                            service.state.flashMode == app.myzel394.alibi.db.PhotoFlashMode.ON
                    },
                )
            }
            Log.i(
                TAG,
                "standard photo focus=${focusResult.get()} " +
                    "lens=${service.state.lensFacing} zoom=${service.state.zoomRatio} " +
                    "range=${service.state.zoomMinRatio}..${service.state.zoomMaxRatio}",
            )
        } finally {
            instrumentation.runOnMainSync { service.stopSession() }
            waitUntil(10_000) { !service.state.isActive }
            context.unbindService(photoConnection)
        }
    }

    @Test
    fun recordingKeepsRunningWithoutPreviewAndAcceptsLiveZoom() {
        grantCameraPermission()
        val model = VideoRecorderModel()
        instrumentation.runOnMainSync {
            model.init(context)
            model.onPreviewZoomChanged(1f, 1f, 10f)
            model.startRecording(context, AppSettings.getDefaultInstance())
        }
        try {
            assertTrue(
                "Video camera should become ready",
                waitUntil(30_000) { model.recorderService?.cameraControl != null },
            )
            val service = requireNotNull(model.recorderService)

            // Covert/standard transition: detaching the viewfinder must not stop
            // the recording.
            instrumentation.runOnMainSync { service.setPreviewSurfaceProvider(null) }

            val before = service.cameraZoomRatio
            instrumentation.runOnMainSync { service.zoomBy(1.6f) }
            assertTrue(
                "Live zoom should reach the recording camera: before=$before " +
                    "after=${service.cameraZoomRatio}",
                waitUntil(10_000) {
                    abs(service.cameraZoomRatio - before) > 0.2f
                },
            )
            assertTrue(
                "Recording should still be running: state=${service.state}",
                waitUntil(5_000) { service.state == RecorderState.RECORDING },
            )
            assertTrue(
                "Camera-side zoom should follow the recording zoom",
                waitUntil(10_000) {
                    val cameraZoom = service.cameraControl?.camera
                        ?.cameraInfo?.zoomState?.value?.zoomRatio
                    cameraZoom != null && abs(cameraZoom - before) > 0.2f
                },
            )
            val cameraZoom = service.cameraControl?.camera
                ?.cameraInfo?.zoomState?.value?.zoomRatio
            Log.i(
                TAG,
                "live recording zoom before=$before after=${service.cameraZoomRatio} " +
                    "cameraZoom=$cameraZoom",
            )
        } finally {
            runCatching { runBlocking { model.stopRecording(context) } }
            runCatching { model.destroyService(context) }
        }
    }

    private lateinit var photoConnection: android.content.ServiceConnection

    private fun bindPhotoService(): PhotoCaptureService {
        val connected = CountDownLatch(1)
        val serviceRef = AtomicReference<PhotoCaptureService>()
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                serviceRef.set((binder as PhotoCaptureService.PhotoBinder).getService())
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        photoConnection = connection
        val bindIntent = Intent(context, PhotoCaptureService::class.java)
        assertTrue(context.bindService(bindIntent, connection, Context.BIND_AUTO_CREATE))
        assertTrue(connected.await(5, TimeUnit.SECONDS))
        return requireNotNull(serviceRef.get())
    }

    private fun grantCameraPermission() {
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.CAMERA,
        )
    }

    private fun waitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean): Boolean {
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
