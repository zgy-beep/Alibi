package app.myzel394.alibi

import androidx.camera.core.CameraSelector
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.db.PhotoAspectRatio
import app.myzel394.alibi.db.PhotoCaptureSettings
import app.myzel394.alibi.db.PhotoCaptureStrategy
import app.myzel394.alibi.db.PhotoFlashMode
import app.myzel394.alibi.db.PhotoHapticFeedback
import app.myzel394.alibi.db.PhotoResolutionPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsPhotoCompatibilityTest {
    @Test
    fun settingsWithoutPhotoSectionUseSafeDefaults() {
        val settings = AppSettings.fromExportedString(
            """
            {
              "hasSeenOnboarding": true,
              "showAdvancedSettings": false
            }
            """.trimIndent()
        )

        assertTrue(settings.photoCaptureSettings.feedbackEnabled)
        assertTrue(settings.photoCaptureSettings.fastCaptureEnabled)
        assertEquals(
            CameraSelector.LENS_FACING_BACK,
            settings.photoCaptureSettings.lensFacing,
        )
        assertTrue(settings.dynamicColorsEnabled)
        assertFalse(settings.reduceMotionEnabled)
    }

    @Test
    fun fastCaptureSettingPersistsWhenDisabled() {
        val settings = AppSettings.getDefaultInstance()
            .setPhotoCaptureSettings(
                AppSettings.getDefaultInstance()
                    .photoCaptureSettings
                    .setFastCaptureEnabled(false)
            )

        val restored = AppSettings.fromExportedString(settings.exportToString())

        assertFalse(restored.photoCaptureSettings.fastCaptureEnabled)
        assertEquals(
            PhotoCaptureStrategy.BALANCED,
            restored.photoCaptureSettings.getEffectiveCaptureStrategy(),
        )
    }

    @Test
    fun hapticFeedbackIsOnlyEffectiveAfterAnEnabledSuccessSetting() {
        val settings = PhotoCaptureSettings()
            .setHapticFeedback(PhotoHapticFeedback.LIGHT)

        assertEquals(
            PhotoHapticFeedback.LIGHT,
            settings.getEffectiveHapticFeedback(),
        )
        assertEquals(
            PhotoHapticFeedback.OFF,
            settings.setFeedbackEnabled(false).getEffectiveHapticFeedback(),
        )
    }

    @Test
    fun detailedPhotoSettingsRoundTrip() {
        val photoSettings = PhotoCaptureSettings()
            .setCaptureStrategy(PhotoCaptureStrategy.MOTION)
            .setHapticFeedback(PhotoHapticFeedback.LIGHT)
            .setResolutionPreference(PhotoResolutionPreference.SPEED)
            .setAspectRatio(PhotoAspectRatio.SIXTEEN_NINE)
            .setFlashMode(PhotoFlashMode.AUTO)
            .setOpticalStabilizationEnabled(false)
            .setContinuousAutoFocusEnabled(false)
            .setGridLinesEnabled(true)
            .setMirrorFrontCamera(false)
            .setShowPreviewOnLaunch(true)
            .setOverlayOpacity(0.65f)
            .setRememberZoom(true)
            .setLastZoomRatio(2.5f)
        val settings = AppSettings.getDefaultInstance()
            .setPhotoCaptureSettings(photoSettings)

        val restored = AppSettings.fromExportedString(settings.exportToString())
            .photoCaptureSettings

        assertEquals(PhotoCaptureStrategy.MOTION, restored.getEffectiveCaptureStrategy())
        assertEquals(PhotoHapticFeedback.LIGHT, restored.getEffectiveHapticFeedback())
        assertEquals(PhotoResolutionPreference.SPEED, restored.resolutionPreference)
        assertEquals(PhotoAspectRatio.SIXTEEN_NINE, restored.aspectRatio)
        assertEquals(PhotoFlashMode.AUTO, restored.flashMode)
        assertFalse(restored.opticalStabilizationEnabled)
        assertFalse(restored.continuousAutoFocusEnabled)
        assertTrue(restored.gridLinesEnabled)
        assertFalse(restored.mirrorFrontCamera)
        assertTrue(restored.showPreviewOnLaunch)
        assertEquals(0.65f, restored.overlayOpacity, 0.001f)
        assertTrue(restored.rememberZoom)
        assertEquals(2.5f, restored.lastZoomRatio, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun importedSettingsRejectInvalidOverlayOpacity() {
        AppSettings.fromExportedString(
            """
            {
              "photoCaptureSettings": {
                "overlayOpacity": 0.1
              }
            }
            """.trimIndent(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun importedSettingsRejectInvalidDurations() {
        AppSettings.fromExportedString(
            """
            {
              "maxDuration": 60000,
              "intervalDuration": 120000
            }
            """.trimIndent(),
        )
    }
}
