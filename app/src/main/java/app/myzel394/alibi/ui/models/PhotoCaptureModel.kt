package app.myzel394.alibi.ui.models

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.camera.core.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.dataStore
import app.myzel394.alibi.services.PhotoCaptureMode
import app.myzel394.alibi.services.PhotoCaptureService
import app.myzel394.alibi.services.PhotoCaptureSessionState
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
class PhotoCaptureModel : ViewModel() {
    var state by mutableStateOf(PhotoCaptureSessionState())
        private set

    val isInSession: Boolean
        get() = state.isActive

    private var service: PhotoCaptureService? = null
    private var boundContext: Context? = null
    private var appVisible = true

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? PhotoCaptureService.PhotoBinder)?.getService()
            service?.onStateChanged = { newState ->
                state = newState
            }
            state = service?.state ?: PhotoCaptureSessionState()
            service?.setAppVisible(appVisible)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service?.onStateChanged = null
            service = null
            state = PhotoCaptureSessionState()
        }
    }

    fun bindToService(context: Context) {
        if (boundContext != null) {
            return
        }

        val appContext = context.applicationContext
        val bound = appContext.bindService(
            Intent(appContext, PhotoCaptureService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        if (bound) {
            boundContext = appContext
        }
    }

    fun unbindFromService() {
        val context = boundContext ?: return
        service?.onStateChanged = null
        runCatching { context.unbindService(connection) }
        boundContext = null
        service = null
        state = PhotoCaptureSessionState()
    }

    fun startSession(
        context: Context,
        settings: AppSettings,
        mode: PhotoCaptureMode,
    ): Boolean {
        val appContext = context.applicationContext

        if (state.isActive && service != null) {
            service?.setMode(mode)
            return true
        }

        val intent = Intent(appContext, PhotoCaptureService::class.java).apply {
            action = PhotoCaptureService.ACTION_START
            putExtra(PhotoCaptureService.EXTRA_MODE, mode.name)
            putExtra(
                PhotoCaptureService.EXTRA_LENS_FACING,
                settings.photoCaptureSettings.lensFacing,
            )
            putExtra(
                PhotoCaptureService.EXTRA_FEEDBACK_ENABLED,
                settings.photoCaptureSettings.feedbackEnabled,
            )
            putExtra(
                PhotoCaptureService.EXTRA_FAST_CAPTURE_ENABLED,
                settings.photoCaptureSettings.fastCaptureEnabled,
            )
            putExtra(
                PhotoCaptureService.EXTRA_CAPTURE_STRATEGY,
                settings.photoCaptureSettings
                    .getEffectiveCaptureStrategy()
                    .name,
            )
            putExtra(
                PhotoCaptureService.EXTRA_HAPTIC_FEEDBACK,
                settings.photoCaptureSettings
                    .getEffectiveHapticFeedback()
                    .name,
            )
            putExtra(
                PhotoCaptureService.EXTRA_RESOLUTION_PREFERENCE,
                settings.photoCaptureSettings.resolutionPreference.name,
            )
            putExtra(
                PhotoCaptureService.EXTRA_ASPECT_RATIO,
                settings.photoCaptureSettings.aspectRatio.name,
            )
            putExtra(
                PhotoCaptureService.EXTRA_FLASH_MODE,
                settings.photoCaptureSettings.flashMode.name,
            )
            putExtra(
                PhotoCaptureService.EXTRA_OPTICAL_STABILIZATION,
                settings.photoCaptureSettings.opticalStabilizationEnabled,
            )
            putExtra(
                PhotoCaptureService.EXTRA_CONTINUOUS_AUTO_FOCUS,
                settings.photoCaptureSettings.continuousAutoFocusEnabled,
            )
            putExtra(
                PhotoCaptureService.EXTRA_MIRROR_FRONT_CAMERA,
                settings.photoCaptureSettings.mirrorFrontCamera,
            )
            putExtra(
                PhotoCaptureService.EXTRA_SHOW_PREVIEW_ON_LAUNCH,
                settings.photoCaptureSettings.showPreviewOnLaunch,
            )
            putExtra(
                PhotoCaptureService.EXTRA_OVERLAY_OPACITY,
                settings.photoCaptureSettings.overlayOpacity,
            )
            putExtra(
                PhotoCaptureService.EXTRA_INITIAL_ZOOM,
                if (settings.photoCaptureSettings.rememberZoom) {
                    settings.photoCaptureSettings.lastZoomRatio
                } else {
                    1f
                },
            )
            settings.saveFolder?.let {
                putExtra(PhotoCaptureService.EXTRA_SAVE_FOLDER, it)
            }
        }

        return try {
            ContextCompat.startForegroundService(appContext, intent)
            bindToService(appContext)
            true
        } catch (error: Exception) {
            state = PhotoCaptureSessionState(
                status = app.myzel394.alibi.services.PhotoCaptureStatus.ERROR,
                mode = mode,
                error = error.message,
            )
            false
        }
    }

    fun setMode(mode: PhotoCaptureMode) {
        service?.setMode(mode)
    }

    fun setAppVisible(visible: Boolean) {
        appVisible = visible
        service?.setAppVisible(visible)
    }

    fun capture() {
        service?.requestCapture()
    }

    fun setPreviewSurfaceProvider(provider: Preview.SurfaceProvider?) {
        service?.setPreviewSurfaceProvider(provider)
    }

    fun zoomBy(scaleFactor: Float) {
        if (scaleFactor > 0f && scaleFactor != 1f) {
            service?.zoomBy(scaleFactor)
        }
    }

    fun rememberCurrentZoom() {
        val context = boundContext ?: return
        val zoomRatio = state.zoomRatio
        viewModelScope.launch {
            context.dataStore.updateData { settings ->
                settings.setPhotoCaptureSettings(
                    settings.photoCaptureSettings.setLastZoomRatio(zoomRatio)
                )
            }
        }
    }

    fun retryLastSave() {
        service?.retryLastSave()
    }

    fun stopSession() {
        val currentService = service
        if (currentService != null) {
            currentService.stopSession()
            return
        }

        boundContext?.let { context ->
            runCatching {
                context.startService(
                    Intent(context, PhotoCaptureService::class.java).apply {
                        action = PhotoCaptureService.ACTION_STOP
                    }
                )
            }
        }
    }

    override fun onCleared() {
        unbindFromService()
        super.onCleared()
    }
}
