package app.myzel394.alibi

import app.myzel394.alibi.helpers.PendingPhoto
import app.myzel394.alibi.helpers.PendingPhotoStage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoRepositoryCompatibilityTest {
    @Test
    fun legacyPendingPhotoLoadsWithRecoveryDefaults() {
        val pending = Json.decodeFromString(
            PendingPhoto.serializer(),
            """
            {
              "id": "legacy",
              "temporaryPath": "/tmp/legacy.jpg",
              "createdAtEpochMillis": 1,
              "lensFacing": 0
            }
            """.trimIndent(),
        )

        assertNull(pending.displayName)
        assertNull(pending.destination)
        assertEquals(PendingPhotoStage.CAPTURED, pending.stage)
    }
}
