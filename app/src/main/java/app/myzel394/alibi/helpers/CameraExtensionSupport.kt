package app.myzel394.alibi.helpers

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class CameraExtensionSupport(
    val night: Boolean,
    val hdr: Boolean,
) {
    val any: Boolean
        get() = night || hdr
}

/**
 * Queries the vendor camera extensions for a lens. Returns null when the
 * platform/device cannot be queried (for example before camera permission).
 */
suspend fun queryCameraExtensionSupport(
    context: Context,
    lensFacing: Int,
): CameraExtensionSupport? = withContext(Dispatchers.IO) {
    runCatching {
        val provider = ProcessCameraProvider
            .getInstance(context)
            .get(5, TimeUnit.SECONDS)
        val manager = ExtensionsManager
            .getInstanceAsync(context, provider)
            .get(5, TimeUnit.SECONDS)
        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
        CameraExtensionSupport(
            night = manager.isExtensionAvailable(selector, ExtensionMode.NIGHT),
            hdr = manager.isExtensionAvailable(selector, ExtensionMode.HDR),
        )
    }.getOrNull()
}
