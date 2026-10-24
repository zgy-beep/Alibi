package app.myzel394.alibi.helpers

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object PhotoNaming {
    private val formatter =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.US)

    fun create(now: LocalDateTime, token: String): String {
        val safeToken = token.filter { it.isLetterOrDigit() }.take(8)
        require(safeToken.isNotEmpty()) { "Photo token must contain letters or digits" }

        return "alibi-photo_${now.format(formatter)}_$safeToken.jpg"
    }
}
