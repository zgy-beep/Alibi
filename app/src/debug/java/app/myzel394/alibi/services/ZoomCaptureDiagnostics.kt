package app.myzel394.alibi.services

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.ExtendableBuilder

/** Debug-only observation of real sensor results; ZoomState is an optimistic UI value. */
internal object ZoomCaptureDiagnostics {
    @Volatile var onResult: ((String, TotalCaptureResult) -> Unit)? = null

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun <T> attach(builder: ExtendableBuilder<T>, source: String) {
        Camera2Interop.Extender(builder).setSessionCaptureCallback(
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    onResult?.invoke(source, result)
                }
            }
        )
    }
}
