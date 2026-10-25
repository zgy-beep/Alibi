package app.myzel394.alibi.services

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import app.myzel394.alibi.NotificationHelper
import app.myzel394.alibi.enums.RecorderState
import app.myzel394.alibi.ui.utils.PermissionHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit


abstract class RecorderService : LifecycleService() {
    private val binder = RecorderBinder()

    private var isPaused: Boolean = false
    lateinit var recordingStart: LocalDateTime
        private set
    private var recordingTimeTimer: ScheduledExecutorService? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var notificationDetails: RecorderNotificationHelper.NotificationDetails? = null
    private val startMutex = Mutex()

    var state = RecorderState.IDLE
        private set

    var onStateChange: ((RecorderState) -> Unit)? = null
    var onError: () -> Unit = {}
    var onRecordingTimeChange: ((Long) -> Unit)? = null

    var recordingTime = 0L
        private set

    protected open fun start() {
        createRecordingTimeTimer()
    }

    protected open fun pause() {
        isPaused = true

        stopRecordingTimeTimer()
    }

    protected open fun resume() {
        createRecordingTimeTimer()
    }

    protected open suspend fun stop() {
        stopRecordingTimeTimer()
    }

    protected abstract fun startForegroundService()

    protected open suspend fun awaitReady() = Unit

    protected open fun onStartAborted() {
        stopRecordingTimeTimer()
    }

    protected fun failRecording(error: Throwable) {
        error.printStackTrace()
        stopRecordingTimeTimer()
        changeState(RecorderState.ERROR)
        onError()
    }

    suspend fun startRecording(
        beforeStart: suspend () -> Unit = {},
    ): Boolean = startMutex.withLock {
        if (state == RecorderState.RECORDING || state == RecorderState.PAUSED) {
            return@withLock true
        }

        recordingStart = LocalDateTime.now()
        changeState(RecorderState.STARTING)

        try {
            withContext(Dispatchers.Main.immediate) {
                startForegroundService()
            }
            withContext(Dispatchers.IO) {
                beforeStart()
                currentCoroutineContext().ensureActive()
                start()
            }
            awaitReady()
            currentCoroutineContext().ensureActive()
            if (state == RecorderState.ERROR) {
                return@withLock false
            }
            changeState(RecorderState.RECORDING)
            true
        } catch (error: CancellationException) {
            runCatching { onStartAborted() }
            changeState(RecorderState.ERROR)
            throw error
        } catch (error: Exception) {
            error.printStackTrace()
            runCatching { onStartAborted() }
            changeState(RecorderState.ERROR)
            if (error !is AvoidErrorDialogError) {
                onError()
            }
            false
        }
    }

    suspend fun stopRecording() {
        changeState(RecorderState.STOPPED)
        stop()
    }

    fun pauseRecording() {
        changeState(RecorderState.PAUSED)
    }

    fun resumeRecording() {
        changeState(RecorderState.RECORDING)
    }

    fun destroy() {
        stopRecordingTimeTimer()
        NotificationManagerCompat.from(this)
            .cancel(NotificationHelper.RECORDER_CHANNEL_NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        stopRecordingTimeTimer()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "init" -> {
                notificationDetails = intent.getStringExtra("notificationDetails")?.let {
                    Json.decodeFromString(
                        RecorderNotificationHelper.NotificationDetails.serializer(),
                        it
                    )
                }
            }

            "changeState" -> {
                val newState = intent.getStringExtra("newState")?.let {
                    RecorderState.valueOf(it)
                } ?: RecorderState.STOPPED
                changeState(newState)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    inner class RecorderBinder : Binder() {
        fun getService(): RecorderService = this@RecorderService
    }

    private fun createRecordingTimeTimer() {
        stopRecordingTimeTimer()
        recordingTimeTimer = Executors.newSingleThreadScheduledExecutor().also {
            it.scheduleWithFixedDelay(
                {
                    recordingTime += 1
                    val currentTime = recordingTime
                    mainHandler.post {
                        onRecordingTimeChange?.invoke(currentTime)
                    }
                },
                0,
                1,
                TimeUnit.SECONDS
            )
        }
    }

    private fun stopRecordingTimeTimer() {
        recordingTimeTimer?.shutdownNow()
        recordingTimeTimer = null
    }

    // Used to change the state of the service
    // will internally call start() / pause() / resume() / stop()
    // Immediately after creating the service make sure to call `changeState(RecorderState.RECORDING)`
    @SuppressLint("MissingPermission")
    fun changeState(newState: RecorderState) {
        if (state == newState) {
            return
        }

        state = newState
        when (newState) {
            RecorderState.RECORDING -> {
                if (isPaused) {
                    resume()
                    isPaused = false
                }
                // `start` is handled by `startRecording`
            }

            RecorderState.PAUSED -> pause()

            else -> {}
        }

        // Update notification
        if (
            arrayOf(
                RecorderState.RECORDING,
                RecorderState.PAUSED
            ).contains(newState) &&
            PermissionHelper.hasGranted(this, android.Manifest.permission.POST_NOTIFICATIONS)
        ) {
            val notification = buildNotification()
            NotificationManagerCompat.from(this).notify(
                NotificationHelper.RECORDER_CHANNEL_NOTIFICATION_ID,
                notification
            )
        }

        onStateChange?.invoke(newState)
    }

    protected fun getNotificationHelper(): RecorderNotificationHelper {
        return RecorderNotificationHelper(this, notificationDetails)
    }

    private fun buildNotification(): Notification {
        val notificationHelper = getNotificationHelper()

        return when (state) {
            RecorderState.RECORDING -> {
                notificationHelper.buildRecordingNotification(recordingTime)
            }

            RecorderState.PAUSED -> {
                notificationHelper.buildPausedNotification(recordingStart)
            }

            else -> {
                throw IllegalStateException("Notification can't be built in state $state")
            }
        }
    }


    // Throw this error if you show a dialog yourself.
    // This will prevent the service from showing their generic error dialog.
    class AvoidErrorDialogError : RuntimeException()
}
