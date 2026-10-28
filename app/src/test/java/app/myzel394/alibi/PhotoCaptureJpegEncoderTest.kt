package app.myzel394.alibi

import app.myzel394.alibi.helpers.shouldUseSoftwareJpegEncoder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCaptureJpegEncoderTest {
    @Test
    fun softwareEncoderOnlyForQualityAndSmallHardwareJpeg() {
        assertTrue(shouldUseSoftwareJpegEncoder(true, 2_583_552L))
        assertTrue(shouldUseSoftwareJpegEncoder(true, 8_000_000L))
    }

    @Test
    fun highResolutionHardwareJpegKeepsMaximumResolution() {
        assertFalse(shouldUseSoftwareJpegEncoder(true, 50_331_648L))
        assertFalse(shouldUseSoftwareJpegEncoder(true, 12_192_768L))
    }

    @Test
    fun unknownHardwareLimitAndNonQualityProfilesUseHardwareJpeg() {
        assertFalse(shouldUseSoftwareJpegEncoder(true, null))
        assertFalse(shouldUseSoftwareJpegEncoder(false, 2_583_552L))
    }
}
