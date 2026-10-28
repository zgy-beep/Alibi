package app.myzel394.alibi.helpers

/**
 * CameraX's software JPEG encoder is useful for deterministic JPEG quality, but
 * on devices with a high-resolution hardware JPEG path it can cap the capture
 * to a much smaller frame (measured: 50 MP hardware vs 8 MP software on a
 * Huawei Mate 70 Pro). Only request it when the hardware JPEG maximum is small
 * enough that no meaningful resolution is lost.
 */
const val MAX_HARDWARE_JPEG_PIXELS_FOR_SOFTWARE_ENCODER = 8_000_000L

fun shouldUseSoftwareJpegEncoder(
    qualityPriority: Boolean,
    maxHardwareJpegPixels: Long?,
): Boolean {
    if (!qualityPriority) {
        return false
    }
    val maxPixels = maxHardwareJpegPixels ?: return false
    return maxPixels <= MAX_HARDWARE_JPEG_PIXELS_FOR_SOFTWARE_ENCODER
}
