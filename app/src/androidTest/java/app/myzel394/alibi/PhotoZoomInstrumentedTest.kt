package app.myzel394.alibi

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myzel394.alibi.dataStore
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.services.PhotoCaptureMode
import app.myzel394.alibi.services.PhotoCaptureService
import app.myzel394.alibi.services.PhotoCaptureStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import android.util.Log
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.pow

@RunWith(AndroidJUnit4::class)
class PhotoZoomInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var photoConnection: android.content.ServiceConnection

    private companion object {
        const val ZOOM_TEST_TAG = "PhotoZoomTest"
    }

    @Test
    fun rapidZoomInputsAreCoalescedIntoBoundedCameraRequests() {
        grantCameraPermission()
        val service = bindPhotoService()
        try {
            startSession(PhotoCaptureMode.BLACK_SCREEN)
            assertTrue(
                "Camera should become ready: ${service.state}",
                waitUntil(20_000) { service.state.status == PhotoCaptureStatus.READY },
            )

            repeat(60) {
                instrumentation.runOnMainSync { service.zoomBy(1.03f) }
                Thread.sleep(16)
            }
            Thread.sleep(1_000)

            Log.i(
                ZOOM_TEST_TAG,
                "coalescing inputs=${service.zoomInputCount} " +
                    "applies=${service.zoomApplyCount} " +
                    "coalesced=${service.zoomCoalescedCount} " +
                    "finalRatio=${service.state.zoomRatio} " +
                    "stalenessMs=${service.lastZoomStalenessMillis}",
            )
            val expected = (1.03f.toDouble().pow(60)).toFloat()
            assertTrue(
                "Zoom should approach the accumulated gesture target: " +
                    "expected=$expected actual=${service.state.zoomRatio}",
                abs(service.state.zoomRatio - expected) < 0.1f,
            )
            assertTrue(
                "Gesture samples should be recorded: ${service.zoomInputCount}",
                service.zoomInputCount >= 40,
            )
            assertTrue(
                "CameraX requests must be fewer than gesture samples: " +
                    "inputs=${service.zoomInputCount} applies=${service.zoomApplyCount}",
                service.zoomApplyCount < service.zoomInputCount,
            )
            assertTrue(
                "Intermediate zoom targets should be dropped: " +
                    "coalesced=${service.zoomCoalescedCount}",
                service.zoomCoalescedCount > 0,
            )
            assertTrue(
                "Newest applied request should stay close to the gesture: " +
                    "staleness=${service.lastZoomStalenessMillis}ms",
                service.lastZoomStalenessMillis < 1_000,
            )
        } finally {
            stopServiceOnMainThread(service)
            context.unbindService(photoConnection)
        }
    }

    @Test
    fun pinchGestureOnBlackScreenChangesZoomRatio() {
        grantCameraPermission()
        grantNotificationPermission()
        runBlocking {
            context.dataStore.updateData { it.setHasSeenOnboarding(true) }
        }

        val photoLabel = context.getString(R.string.photo_home_label)
        val enterBlackLabel = context.getString(R.string.photo_setup_start_black)
        val scenario = ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java),
        )
        try {
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithText(photoLabel).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithText(photoLabel).onFirst().performClick()
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithText(enterBlackLabel).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithText(enterBlackLabel).onFirst().performClick()

            val service = bindPhotoService()
            try {
                assertTrue(
                    "Black screen session should become ready: ${service.state}",
                    waitUntil(20_000) {
                        service.state.mode == PhotoCaptureMode.BLACK_SCREEN &&
                            service.state.status == PhotoCaptureStatus.READY
                    },
                )
                val before = service.state.zoomRatio
                composeRule.onRoot().performTouchInput {
                    val c = center
                    pinch(
                        start0 = c + Offset(-120f, 0f),
                        end0 = c + Offset(-420f, 0f),
                        start1 = c + Offset(120f, 0f),
                        end1 = c + Offset(420f, 0f),
                    )
                }
                // The newest pinched ratio is applied right after the previous camera
                // request finishes; make sure at least one coalesced request is applied.
                assertTrue(
                    "Pinch should apply a coalesced camera request: " +
                        "inputs=${service.zoomInputCount} applies=${service.zoomApplyCount}",
                    waitUntil(3_000) { service.zoomApplyCount >= 2 },
                )
                Log.i(
                    ZOOM_TEST_TAG,
                    "pinch before=$before after=${service.state.zoomRatio} " +
                        "inputs=${service.zoomInputCount} applies=${service.zoomApplyCount} " +
                        "stalenessMs=${service.lastZoomStalenessMillis}",
                )
                assertTrue(
                    "Pinch should increase the zoom ratio: before=$before " +
                        "after=${service.state.zoomRatio}",
                    service.state.zoomRatio > before + 0.05f,
                )
            } finally {
                stopServiceOnMainThread(service)
                context.unbindService(photoConnection)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun floatingOverlayAcceptsZoomAdjustments() {
        grantCameraPermission()
        grantOverlayPermission()
        val defaultSettings = AppSettings.getDefaultInstance()
        val settings = defaultSettings.setPhotoCaptureSettings(
            defaultSettings.photoCaptureSettings.setRememberZoom(true),
        )
        val service = bindPhotoService()
        try {
            startSession(PhotoCaptureMode.FLOATING, startInBackground = true, settings = settings)
            assertTrue(
                "Floating session should become ready: ${service.state}",
                waitUntil(20_000) {
                    service.state.mode == PhotoCaptureMode.FLOATING &&
                        service.state.status == PhotoCaptureStatus.READY
                },
            )
            instrumentation.runOnMainSync { service.setZoomRatio(1.8f) }
            Log.i(ZOOM_TEST_TAG, "floating overlay zoom requested ratio=1.8")
            assertTrue(
                "Floating overlay should apply zoom: ${service.state.zoomRatio}",
                waitUntil(3_000) { abs(service.state.zoomRatio - 1.8f) < 0.05f },
            )
            assertTrue(
                "Floating zoom should use the coalesced camera path",
                service.zoomApplyCount > 0,
            )
            // rememberZoom writes the latest ratio after a short debounce.
            Thread.sleep(900)
            val storedZoom = runBlocking {
                context.dataStore.data.first().photoCaptureSettings.lastZoomRatio
            }
            Log.i(ZOOM_TEST_TAG, "floating overlay rememberZoom stored=$storedZoom")
            assertTrue(
                "Floating zoom should be remembered when rememberZoom is enabled: $storedZoom",
                abs(storedZoom - 1.8f) < 0.1f,
            )
        } finally {
            stopServiceOnMainThread(service)
            context.unbindService(photoConnection)
        }
    }

    private fun stopServiceOnMainThread(service: PhotoCaptureService) {
        instrumentation.runOnMainSync { service.stopSession() }
        waitUntil(10_000) { !service.state.isActive }
    }

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

    private fun startSession(
        mode: PhotoCaptureMode,
        startInBackground: Boolean = false,
        settings: AppSettings = AppSettings.getDefaultInstance(),
    ) {
        ContextCompat.startForegroundService(
            context,
            PhotoCaptureService.createStartIntent(
                context = context,
                settings = settings,
                mode = mode,
                startInBackground = startInBackground,
            ),
        )
    }

    private fun grantCameraPermission() {
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.CAMERA,
        )
    }

    private fun grantNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    private fun grantOverlayPermission() {
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow",
            ),
        ).use { it.readBytes() }
    }

    private fun waitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }
}
