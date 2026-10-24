package app.myzel394.alibi.services

enum class PhotoCaptureMode {
    BLACK_SCREEN,
    FLOATING,
}

enum class PhotoCaptureStatus {
    STOPPED,
    STARTING,
    READY,
    CAPTURING,
    SAVING,
    STOPPING,
    ERROR,
}

data class PhotoCaptureSessionState(
    val status: PhotoCaptureStatus = PhotoCaptureStatus.STOPPED,
    val mode: PhotoCaptureMode? = null,
    val error: String? = null,
    val lastPhotoUri: String? = null,
    val canRetrySave: Boolean = false,
    val queuedCaptures: Int = 0,
    val pendingCaptures: Int = 0,
    val zoomRatio: Float = 1f,
) {
    val canAcceptCapture: Boolean
        get() = pendingCaptures < MAX_PENDING_CAPTURES &&
            (
                status == PhotoCaptureStatus.READY ||
                    status == PhotoCaptureStatus.CAPTURING ||
                    status == PhotoCaptureStatus.SAVING
                )

    val isActive: Boolean
        get() = status != PhotoCaptureStatus.STOPPED

    companion object {
        const val MAX_PENDING_CAPTURES = 8
    }
}

class PhotoCaptureRequestBudget(
    private val maxRequests: Int = MAX_PENDING_CAPTURES,
    private val maxBytes: Long = MAX_PENDING_PHOTO_BYTES,
) {
    private val pendingIds = LinkedHashSet<Long>()
    var pendingBytes: Long = 0L
        private set

    val pendingCount: Int
        get() = pendingIds.size

    val canAccept: Boolean
        get() = pendingIds.size < maxRequests && pendingBytes < maxBytes

    fun accept(requestId: Long): Boolean {
        if (!canAccept || requestId in pendingIds) {
            return false
        }
        pendingIds.add(requestId)
        return true
    }

    fun addCapturedBytes(requestId: Long, bytes: Long) {
        if (requestId in pendingIds) {
            pendingBytes = (pendingBytes + bytes.coerceAtLeast(0L)).coerceAtLeast(0L)
        }
    }

    fun finish(requestId: Long, capturedBytes: Long = 0L) {
        if (pendingIds.remove(requestId)) {
            pendingBytes = (pendingBytes - capturedBytes.coerceAtLeast(0L)).coerceAtLeast(0L)
        }
    }

    fun clear() {
        pendingIds.clear()
        pendingBytes = 0L
    }

    companion object {
        const val MAX_PENDING_CAPTURES = PhotoCaptureSessionState.MAX_PENDING_CAPTURES
        const val MAX_PENDING_PHOTO_BYTES = 128L * 1024L * 1024L
    }
}
