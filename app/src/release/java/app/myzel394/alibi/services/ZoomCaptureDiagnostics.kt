package app.myzel394.alibi.services

import androidx.camera.core.ExtendableBuilder

/** Release builds do not install capture-result diagnostics. */
internal object ZoomCaptureDiagnostics {
    @Suppress("UNUSED_PARAMETER")
    fun <T> attach(builder: ExtendableBuilder<T>, source: String) = Unit
}
