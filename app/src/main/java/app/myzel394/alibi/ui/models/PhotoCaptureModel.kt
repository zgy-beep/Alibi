package app.myzel394.alibi.ui.models

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.db.PhotoFlashMode
import app.myzel394.alibi.dataStore
import app.myzel394.alibi.services.PhotoCaptureMode
import app.myzel394.alibi.services.PhotoCaptureService
import app.myzel394.alibi.services.PhotoCaptureSessionState
import kotlinx.coroutines.launch
import kotlin.math.abs

@SuppressLint("StaticFieldLeak")
class PhotoCaptureModel : ViewModel() {
    var state by mutableStateOf(PhotoCaptureSessionState())
        private set

    /**
     * Zoom-only state, kept separate from [state] so a continuous pinch does not
     * invalidate every composable that reads the session state.
     */
    var zoomRatio by mutableFloatStateOf(1f)
        private set

    /**
     * Result of the last [requestFocus], delivered asynchronously by the service
     * on the main thread. `null` means "no focus pass in flight" and is reset by
     * [requestFocus] and [clearFocusResult]; the standard camera screen uses it
     * to show/hide its focus ring.
     */
    var focusResult by mutableStateOf<Boolean?>(null)
        private set

    val isInSession: Boolean
        get() = state.isActive

    private var service: PhotoCaptureService? = null
    private var boundContext: Context? = null
    private var appVisible = true

    // Cached because the service may not be connected yet when the standard
    // camera screen attaches its viewfinder; they are replayed on connect.
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null
    private var meteringPointFactory: MeteringPointFactory? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? PhotoCaptureService.PhotoBinder)?.getService()
            service?.onStateChanged = { newState ->
                state = newState
                publishZoomRatio(newState.zoomRatio)
            }
            service?.onZoomChanged = { newZoomRatio ->
                publishZoomRatio(newZoomRatio)
            }
            service?.onFocusChanged = { focused ->
                focusResult = focused
            }
            service?.setPreviewSurfaceProvider(previewSurfaceProvider)
            service?.setMeteringPointFactory(meteringPointFactory)
            state = service?.state ?: PhotoCaptureSessionState()
            publishZoomRatio(state.zoomRatio)
            service?.setAppVisible(appVisible)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service?.onStateChanged = null
            service?.onZoomChanged = null
            service?.onFocusChanged = null
            service = null
            state = PhotoCaptureSessionState()
            zoomRatio = 1f
            focusResult = null
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
        service?.onZoomChanged = null
        service?.onFocusChanged = null
        runCatching { context.unbindService(connection) }
        boundContext = null
        service = null
        state = PhotoCaptureSessionState()
        zoomRatio = 1f
        focusResult = null
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

        val intent = PhotoCaptureService.createStartIntent(
            context = appContext,
            settings = settings,
            mode = mode,
        )

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

    /**
     * Idempotent: the standard camera screen may call this on every state
     * change. The provider is cached so it still reaches the service when the
     * binder connects after the screen's viewfinder is created.
     */
    fun setPreviewSurfaceProvider(provider: Preview.SurfaceProvider?) {
        previewSurfaceProvider = provider
        service?.setPreviewSurfaceProvider(provider)
    }

    /**
     * Idempotent coordinate mapper for [requestFocus], obtained from
     * `PreviewView.getMeteringPointFactory()`. Cached for pre-connection calls.
     */
    fun setMeteringPointFactory(factory: MeteringPointFactory?) {
        meteringPointFactory = factory
        service?.setMeteringPointFactory(factory)
    }

    /**
     * Starts AF+AE at the given viewfinder pixel coordinates. Clears
     * [focusResult] first; the service reports the outcome through
     * `onFocusChanged`. A missing service reports a failed focus so the UI
     * cannot hang.
     */
    fun requestFocus(xPixels: Float, yPixels: Float) {
        focusResult = null
        val currentService = service
        if (currentService == null) {
            focusResult = false
            return
        }
        currentService.requestFocus(xPixels, yPixels)
    }

    /** Clears [focusResult] once the screen has faded out its focus ring. */
    fun clearFocusResult() {
        focusResult = null
    }

    /**
     * Front/back switch or flash toggle without leaving the session. The service
     * drops the request while a capture/save is in flight; the screen just keeps
     * its toggle in the old state until [state] reports the new lens/flash.
     */
    fun reconfigure(lensFacing: Int? = null, flashMode: PhotoFlashMode? = null) {
        service?.reconfigure(lensFacing, flashMode)
    }

    fun zoomBy(scaleFactor: Float) {
        if (scaleFactor > 0f && scaleFactor != 1f) {
            service?.zoomBy(scaleFactor)
        }
    }

    /** Absolute zoom input (slider/step buttons); snaps instead of easing. */
    fun zoomTo(target: Float) {
        if (target.isFinite() && target > 0f) {
            service?.setZoomRatio(target)
        }
    }

    /**
     * Publishes a zoom ratio to Compose. Skipping sub-perceptual changes keeps a
     * ~16 ms easing ticker from invalidating zoom readers on every display
     * frame; the settled value always differs by more than the epsilon.
     */
    private fun publishZoomRatio(value: Float) {
        if (!value.isFinite() || abs(value - zoomRatio) < ZOOM_RATIO_EPSILON) {
            return
        }
        zoomRatio = value
    }

    fun rememberCurrentZoom() {
        val context = boundContext ?: return
        val currentZoom = zoomRatio
        viewModelScope.launch {
            context.dataStore.updateData { settings ->
                settings.setPhotoCaptureSettings(
                    settings.photoCaptureSettings.setLastZoomRatio(currentZoom)
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

    private companion object {
        /** Zoom changes below this are not worth a recomposition. */
        const val ZOOM_RATIO_EPSILON = 0.001f
    }
}
