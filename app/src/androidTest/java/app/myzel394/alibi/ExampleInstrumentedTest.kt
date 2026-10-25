package app.myzel394.alibi

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.enums.RecorderState
import app.myzel394.alibi.helpers.AudioBatchesFolder
import app.myzel394.alibi.services.AudioRecorderService
import app.myzel394.alibi.services.RecorderService
import app.myzel394.alibi.services.PhotoCaptureMode
import app.myzel394.alibi.services.PhotoCaptureService

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.zgybeep.alibi.debug", appContext.packageName)
    }

    @Test
    fun mainActivityLaunchesWithStaticShortcuts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        assertNotNull(activity)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcutIds = instrumentation.targetContext
                .getSystemService(ShortcutManager::class.java)
                .manifestShortcuts
                .map { it.id }
                .toSet()
            assertEquals(
                setOf(
                    AppShortcuts.RECORD_AUDIO_ID,
                    AppShortcuts.TAKE_PHOTO_ID,
                ),
                shortcutIds,
            )
        }
        activity.finish()
    }

    @Test
    fun photoShortcutSwitchesBlackSessionToFloating() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.CAMERA,
        )
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow",
            )
        ).use { it.readBytes() }

        val connected = CountDownLatch(1)
        val serviceRef = AtomicReference<PhotoCaptureService>()
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                serviceRef.set((binder as PhotoCaptureService.PhotoBinder).getService())
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val bindIntent = Intent(context, PhotoCaptureService::class.java)
        assertTrue(context.bindService(bindIntent, connection, Context.BIND_AUTO_CREATE))
        assertTrue(connected.await(5, TimeUnit.SECONDS))
        val service = serviceRef.get()
        assertNotNull(service)

        try {
            val settings = AppSettings.getDefaultInstance()
            ContextCompat.startForegroundService(
                context,
                PhotoCaptureService.createStartIntent(
                    context = context,
                    settings = settings,
                    mode = PhotoCaptureMode.BLACK_SCREEN,
                ),
            )
            assertTrue(waitUntil { service!!.state.isActive })

            ContextCompat.startForegroundService(
                context,
                PhotoCaptureService.createStartIntent(
                    context = context,
                    settings = settings,
                    mode = PhotoCaptureMode.FLOATING,
                    startInBackground = true,
                ),
            )
            assertTrue(waitUntil { service!!.state.mode == PhotoCaptureMode.FLOATING })
            assertEquals(PhotoCaptureMode.FLOATING, service!!.state.mode)
        } finally {
            serviceRef.get()?.stopSession()
            runCatching { context.unbindService(connection) }
        }
    }

    @Test
    fun audioStartIsIdempotentAndReportsReadyAfterRecorderStarts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.RECORD_AUDIO,
        )

        val connected = CountDownLatch(1)
        val serviceRef = AtomicReference<AudioRecorderService>()
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                serviceRef.set(
                    (binder as RecorderService.RecorderBinder).getService() as AudioRecorderService
                )
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val intent = Intent(context, AudioRecorderService::class.java).apply {
            action = "init"
        }
        ContextCompat.startForegroundService(context, intent)
        assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
        assertTrue(connected.await(5, TimeUnit.SECONDS))
        val service = requireNotNull(serviceRef.get())

        try {
            service.settings = AppSettings.getDefaultInstance()
            service.batchesFolder = AudioBatchesFolder.viaInternalFolder(context)
            val started = runBlocking { service.startRecording() }

            assertTrue(started)
            assertEquals(RecorderState.RECORDING, service.state)
            assertNotNull(service.recorder)

            var secondStartPreparedFolder = false
            val reused = runBlocking {
                service.startRecording(beforeStart = { secondStartPreparedFolder = true })
            }
            assertTrue(reused)
            assertFalse(secondStartPreparedFolder)
            assertEquals(RecorderState.RECORDING, service.state)
        } finally {
            runCatching { runBlocking { service.stopRecording() } }
            service.destroy()
            runCatching { context.unbindService(connection) }
        }
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
