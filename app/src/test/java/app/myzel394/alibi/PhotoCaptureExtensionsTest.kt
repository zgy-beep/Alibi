package app.myzel394.alibi

import app.myzel394.alibi.db.PhotoCaptureExtensionMode
import app.myzel394.alibi.helpers.resolvePhotoCaptureExtension
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoCaptureExtensionsTest {
    @Test
    fun autoPrefersNightThenHdrThenStandard() {
        assertEquals(
            PhotoCaptureExtensionMode.NIGHT,
            resolvePhotoCaptureExtension(PhotoCaptureExtensionMode.AUTO, true, true),
        )
        assertEquals(
            PhotoCaptureExtensionMode.HDR,
            resolvePhotoCaptureExtension(PhotoCaptureExtensionMode.AUTO, false, true),
        )
        assertEquals(
            PhotoCaptureExtensionMode.OFF,
            resolvePhotoCaptureExtension(PhotoCaptureExtensionMode.AUTO, false, false),
        )
    }

    @Test
    fun explicitModeFallsBackWhenUnavailable() {
        assertEquals(
            PhotoCaptureExtensionMode.OFF,
            resolvePhotoCaptureExtension(PhotoCaptureExtensionMode.NIGHT, false, true),
        )
        assertEquals(
            PhotoCaptureExtensionMode.OFF,
            resolvePhotoCaptureExtension(PhotoCaptureExtensionMode.HDR, true, false),
        )
        assertEquals(
            PhotoCaptureExtensionMode.NIGHT,
            resolvePhotoCaptureExtension(PhotoCaptureExtensionMode.NIGHT, true, false),
        )
    }

    @Test
    fun offNeverUsesExtensions() {
        assertEquals(
            PhotoCaptureExtensionMode.OFF,
            resolvePhotoCaptureExtension(PhotoCaptureExtensionMode.OFF, true, true),
        )
    }
}
