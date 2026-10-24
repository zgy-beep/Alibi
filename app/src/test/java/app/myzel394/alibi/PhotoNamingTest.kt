package app.myzel394.alibi

import app.myzel394.alibi.helpers.PhotoNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime

class PhotoNamingTest {
    @Test
    fun createsTimestampedJpegName() {
        val name = PhotoNaming.create(
            LocalDateTime.of(2026, 10, 24, 13, 14, 15, 123_000_000),
            "abcd-1234-unsafe",
        )

        assertEquals("alibi-photo_20261024_131415_123_abcd1234.jpg", name)
    }

    @Test
    fun rejectsTokensWithoutLettersOrDigits() {
        assertThrows(IllegalArgumentException::class.java) {
            PhotoNaming.create(LocalDateTime.now(), "----")
        }
    }
}
