package app.myzel394.alibi

import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.db.VideoRecorderSettings
import app.myzel394.alibi.ui.models.DEFAULT_VIDEO_ZOOM_RATIO
import app.myzel394.alibi.ui.models.normalizeZoomRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-only coverage for the video zoom target carry-over and for the new
 * video stabilization setting default/round-trip.
 */
class VideoZoomSettingsTest {
    @Test
    fun zoomTargetKeepsRatiosInsideTheCameraRange() {
        assertEquals(2.5f, normalizeZoomRatio(2.5f, 1f, 4f), 0.0001f)
        assertEquals(4f, normalizeZoomRatio(10f, 1f, 4f), 0.0001f)
        assertEquals(1f, normalizeZoomRatio(0.1f, 1f, 4f), 0.0001f)
        assertEquals(2f, normalizeZoomRatio(1f, 2f, 8f), 0.0001f)
    }

    @Test
    fun invalidZoomStateFallsBackToNeutralZoom() {
        assertEquals(
            DEFAULT_VIDEO_ZOOM_RATIO,
            normalizeZoomRatio(Float.NaN, 1f, 4f),
            0.0001f,
        )
        assertEquals(1f, normalizeZoomRatio(1f, Float.NaN, Float.NaN), 0.0001f)
        assertEquals(1f, normalizeZoomRatio(Float.POSITIVE_INFINITY, 0f, -1f), 0.0001f)
    }

    @Test
    fun videoStabilizationIsEnabledByDefault() {
        assertTrue(VideoRecorderSettings().videoStabilizationEnabled)
        assertTrue(VideoRecorderSettings.getDefaultInstance().videoStabilizationEnabled)
    }

    @Test
    fun videoStabilizationChoiceSurvivesExportAndImport() {
        val settings = AppSettings.getDefaultInstance().setVideoRecorderSettings(
            VideoRecorderSettings().setVideoStabilizationEnabled(false)
        )

        val restored = AppSettings.fromExportedString(settings.exportToString())

        assertFalse(restored.videoRecorderSettings.videoStabilizationEnabled)
    }

    @Test
    fun legacySettingsWithoutStabilizationFieldUseTheDefault() {
        val settings = AppSettings.fromExportedString(
            """
            {
              "hasSeenOnboarding": true,
              "videoRecorderSettings": {}
            }
            """.trimIndent()
        )

        assertTrue(settings.videoRecorderSettings.videoStabilizationEnabled)
    }
}
