package app.myzel394.alibi

import app.myzel394.alibi.db.PhotoAspectRatio
import app.myzel394.alibi.db.PhotoResolutionPreference
import app.myzel394.alibi.helpers.photoTargetSize
import app.myzel394.alibi.helpers.usesQualityPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCaptureTargetsTest {
    @Test
    fun recommendedTargetsAboutTwelveMegapixelsOrEightFor16x9() {
        val fourThree = photoTargetSize(
            PhotoResolutionPreference.RECOMMENDED,
            PhotoAspectRatio.FOUR_THREE,
        )
        assertEquals(4000, fourThree.width)
        assertEquals(3000, fourThree.height)

        val sixteenNine = photoTargetSize(
            PhotoResolutionPreference.RECOMMENDED,
            PhotoAspectRatio.SIXTEEN_NINE,
        )
        assertEquals(3840, sixteenNine.width)
        assertEquals(2160, sixteenNine.height)
    }

    @Test
    fun speedTargetsStaySmall() {
        val fourThree = photoTargetSize(
            PhotoResolutionPreference.SPEED,
            PhotoAspectRatio.FOUR_THREE,
        )
        assertEquals(1280, fourThree.width)
        assertEquals(960, fourThree.height)
    }

    @Test
    fun qualityRequestsTheLargestSupportedSize() {
        val target = photoTargetSize(
            PhotoResolutionPreference.QUALITY,
            PhotoAspectRatio.FOUR_THREE,
        )
        assertEquals(10000, target.width)
        assertEquals(10000, target.height)
        assertTrue(usesQualityPriority(PhotoResolutionPreference.QUALITY))
        assertFalse(usesQualityPriority(PhotoResolutionPreference.RECOMMENDED))
        assertFalse(usesQualityPriority(PhotoResolutionPreference.SPEED))
    }
}
