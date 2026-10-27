package app.myzel394.alibi.helpers

import app.myzel394.alibi.db.PhotoAspectRatio
import app.myzel394.alibi.db.PhotoResolutionPreference

/**
 * Target sizes for CameraX [androidx.camera.core.ImageCapture].
 *
 * Photo resolution preferences used to request roughly 2–3 MP for the default
 * "recommended" option, which is much lower than what phone cameras normally
 * save and makes the result look soft when compared with the stock camera app.
 * The recommended profile now targets a 12 MP / 8 MP frame, while "quality"
 * asks the camera for the largest supported frame and enables higher-quality
 * capture processing.
 */
data class PhotoTargetSize(val width: Int, val height: Int)

fun photoTargetSize(
    preference: PhotoResolutionPreference,
    aspectRatio: PhotoAspectRatio,
): PhotoTargetSize {
    val fourThree = aspectRatio == PhotoAspectRatio.FOUR_THREE
    return when (preference) {
        PhotoResolutionPreference.SPEED -> if (fourThree) {
            PhotoTargetSize(1280, 960)
        } else {
            PhotoTargetSize(1280, 720)
        }

        PhotoResolutionPreference.RECOMMENDED -> if (fourThree) {
            PhotoTargetSize(4000, 3000)
        } else {
            PhotoTargetSize(3840, 2160)
        }

        // A very large target with FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
        // selects the largest supported size for the requested aspect ratio.
        PhotoResolutionPreference.QUALITY -> PhotoTargetSize(10000, 10000)
    }
}

/** "Quality" also requests higher-quality still capture processing. */
fun usesQualityPriority(preference: PhotoResolutionPreference): Boolean =
    preference == PhotoResolutionPreference.QUALITY
