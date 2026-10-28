package app.myzel394.alibi.helpers

import app.myzel394.alibi.db.PhotoCaptureExtensionMode

/**
 * Resolves the vendor camera extension to use for a photo session.
 *
 * AUTO prefers the night extension (best noise reduction in low light), then
 * HDR. Explicit modes only fall back to the standard pipeline when the vendor
 * does not expose that extension, so callers can always bind the standard
 * camera instead of failing.
 */
fun resolvePhotoCaptureExtension(
    requested: PhotoCaptureExtensionMode,
    nightAvailable: Boolean,
    hdrAvailable: Boolean,
): PhotoCaptureExtensionMode {
    return when (requested) {
        PhotoCaptureExtensionMode.AUTO -> when {
            nightAvailable -> PhotoCaptureExtensionMode.NIGHT
            hdrAvailable -> PhotoCaptureExtensionMode.HDR
            else -> PhotoCaptureExtensionMode.OFF
        }

        PhotoCaptureExtensionMode.NIGHT ->
            if (nightAvailable) PhotoCaptureExtensionMode.NIGHT else PhotoCaptureExtensionMode.OFF

        PhotoCaptureExtensionMode.HDR ->
            if (hdrAvailable) PhotoCaptureExtensionMode.HDR else PhotoCaptureExtensionMode.OFF

        PhotoCaptureExtensionMode.OFF -> PhotoCaptureExtensionMode.OFF
    }
}
