package app.myzel394.alibi

import app.myzel394.alibi.services.PhotoCaptureMode
import app.myzel394.alibi.services.PhotoCaptureRequestBudget
import app.myzel394.alibi.services.PhotoCaptureSessionState
import app.myzel394.alibi.services.PhotoCaptureStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCaptureSessionStateTest {
    @Test
    fun activeReadyAndBusyStatesAcceptCapture() {
        PhotoCaptureStatus.entries.forEach { status ->
            val state = PhotoCaptureSessionState(
                status = status,
                mode = PhotoCaptureMode.BLACK_SCREEN,
            )
            if (status in listOf(
                    PhotoCaptureStatus.READY,
                    PhotoCaptureStatus.CAPTURING,
                    PhotoCaptureStatus.SAVING,
                )
            ) {
                assertTrue(state.canAcceptCapture)
            } else {
                assertFalse(status.name, state.canAcceptCapture)
            }
        }
    }

    @Test
    fun stoppedStateIsNotActive() {
        assertFalse(PhotoCaptureSessionState().isActive)
        assertTrue(
            PhotoCaptureSessionState(
                status = PhotoCaptureStatus.STARTING,
                mode = PhotoCaptureMode.FLOATING,
            ).isActive
        )
    }

    @Test
    fun fullRequestBudgetRejectsNewCaptures() {
        val state = PhotoCaptureSessionState(
            status = PhotoCaptureStatus.READY,
            mode = PhotoCaptureMode.BLACK_SCREEN,
            pendingCaptures = PhotoCaptureSessionState.MAX_PENDING_CAPTURES,
        )

        assertFalse(state.canAcceptCapture)
    }

    @Test
    fun requestBudgetTracksTemporaryBytesUntilFinished() {
        val budget = PhotoCaptureRequestBudget(
            maxRequests = 2,
            maxBytes = 10,
        )

        assertTrue(budget.accept(1))
        assertTrue(budget.accept(2))
        assertFalse(budget.accept(3))
        budget.addCapturedBytes(1, 10)
        budget.finish(2)
        assertFalse(budget.canAccept)
        budget.finish(1, capturedBytes = 10)
        assertTrue(budget.canAccept)
        assertTrue(budget.accept(3))
    }
}
