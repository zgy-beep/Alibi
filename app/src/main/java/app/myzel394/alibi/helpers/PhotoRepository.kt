package app.myzel394.alibi.helpers

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID

@Serializable
data class PhotoRecord(
    val id: String,
    val displayName: String,
    val uri: String,
    val localPath: String? = null,
    val createdAtEpochMillis: Long,
    val lensFacing: Int,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class PendingPhoto(
    val id: String,
    val temporaryPath: String,
    val createdAtEpochMillis: Long,
    val lensFacing: Int,
    val displayName: String? = null,
    val saveFolder: String? = null,
    val destination: String? = null,
    val stage: PendingPhotoStage = PendingPhotoStage.CAPTURED,
)

@Serializable
enum class PendingPhotoStage {
    CAPTURED,
    DESTINATION_PREPARED,
}

@Serializable
data class PhotoIndex(
    val version: Int = 1,
    val records: List<PhotoRecord> = emptyList(),
    val pending: List<PendingPhoto> = emptyList(),
)

class CorruptPhotoIndexException(
    message: String,
    val backupFile: File?,
    cause: Throwable,
) : IllegalStateException(message, cause)

class PhotoRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val indexFile = AtomicFile(File(appContext.filesDir, INDEX_FILE_NAME))
    private val pendingFolder = File(appContext.filesDir, PENDING_FOLDER_NAME)
    private val photosFolder = File(appContext.filesDir, PHOTOS_FOLDER_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val ioMutex = Mutex()

    suspend fun newTemporaryFile(): File = withIo {
        File(pendingFolder, "${UUID.randomUUID()}.jpg")
    }

    suspend fun markPending(pending: PendingPhoto) = withIo {
        val index = readIndex()
        writeIndex(
            index.copy(
                pending = index.pending.filterNot { it.id == pending.id } + pending,
            ),
        )
    }

    suspend fun markDestinationPrepared(pendingId: String, destination: String) = withIo {
        val index = readIndex()
        if (index.pending.any { it.id == pendingId }) {
            writeIndex(
                index.copy(
                    pending = index.pending.map {
                        if (it.id == pendingId) {
                            it.copy(
                                destination = destination,
                                stage = PendingPhotoStage.DESTINATION_PREPARED,
                            )
                        } else {
                            it
                        }
                    },
                ),
            )
        }
    }

    suspend fun complete(pendingId: String, record: PhotoRecord) = withIo {
        val index = readIndex()
        val completedPending = index.pending.firstOrNull { it.id == pendingId }
        writeIndex(
            index.copy(
                records = listOf(record) + index.records.filterNot { it.id == record.id },
                pending = index.pending.filterNot { it.id == pendingId },
            )
        )
        completedPending?.let { pending ->
            File(pending.temporaryPath).delete()
        }
    }

    suspend fun removePending(pendingId: String) = withIo {
        val index = readIndex()
        writeIndex(index.copy(pending = index.pending.filterNot { it.id == pendingId }))
    }

    suspend fun records(): List<PhotoRecord> = withIo { readIndex().records }

    suspend fun pending(): List<PendingPhoto> = withIo { readIndex().pending }

    suspend fun deleteRecord(recordId: String) = withIo {
        val index = readIndex()
        writeIndex(index.copy(records = index.records.filterNot { it.id == recordId }))
    }

    suspend fun recoverPendingFiles(): List<PendingPhoto> = withIo {
        val index = readIndex()
        val existing = index.pending.filter { pending ->
            File(pending.temporaryPath).isFile
        }
        if (existing.size != index.pending.size) {
            writeIndex(index.copy(pending = existing))
        }
        existing
    }

    @Synchronized
    private fun readIndex(): PhotoIndex {
        val backupFile = File("${indexFile.baseFile.absolutePath}.bak")
        if (!indexFile.baseFile.exists() && !backupFile.exists()) {
            return PhotoIndex()
        }

        return try {
            indexFile.openRead().bufferedReader().use { reader ->
                json.decodeFromString(PhotoIndex.serializer(), reader.readText())
            }
        } catch (error: Exception) {
            val backup = runCatching {
                File(indexFile.baseFile.parentFile, "$INDEX_FILE_NAME.corrupt").also {
                    val source = if (indexFile.baseFile.exists()) {
                        indexFile.baseFile
                    } else {
                        backupFile
                    }
                    if (!it.exists() && source.exists()) {
                        source.copyTo(it)
                    }
                }
            }.getOrNull()
            throw CorruptPhotoIndexException(
                "The photo index is unreadable; the original file was preserved",
                backup,
                error,
            )
        }
    }

    private fun writeIndex(index: PhotoIndex) {
        val bytes = json.encodeToString(PhotoIndex.serializer(), index).encodeToByteArray()
        val output = indexFile.startWrite()

        try {
            output.write(bytes)
            output.flush()
            indexFile.finishWrite(output)
        } catch (error: Exception) {
            indexFile.failWrite(output)
            throw error
        }
    }

    private suspend fun <T> withIo(block: () -> T): T {
        return withContext(Dispatchers.IO) {
            ioMutex.withLock {
                photosFolder.mkdirs()
                pendingFolder.mkdirs()
                block()
            }
        }
    }

    companion object {
        const val PHOTOS_FOLDER_NAME = "photos"
        const val PENDING_FOLDER_NAME = "photo_pending"
        const val INDEX_FILE_NAME = "photo_index.json"
        fun pendingId(): String = UUID.randomUUID().toString()

        fun pendingPhoto(
            id: String,
            temporaryFile: File,
            lensFacing: Int,
            displayName: String? = null,
            saveFolder: String? = null,
        ) = PendingPhoto(
            id = id,
            temporaryPath = temporaryFile.absolutePath,
            createdAtEpochMillis = Instant.now().toEpochMilli(),
            lensFacing = lensFacing,
            displayName = displayName,
            saveFolder = saveFolder,
        )
    }
}
