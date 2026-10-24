package app.myzel394.alibi.helpers

import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.myzel394.alibi.BuildConfig
import app.myzel394.alibi.ui.PHOTO_CUSTOM_FOLDER_NAME
import app.myzel394.alibi.ui.PHOTO_FILE_PROVIDER_AUTHORITY_SUFFIX
import app.myzel394.alibi.ui.PHOTO_RELATIVE_PATH
import app.myzel394.alibi.ui.RECORDER_INTERNAL_SELECTED_VALUE
import app.myzel394.alibi.ui.RECORDER_MEDIA_SELECTED_VALUE
import java.io.File

data class StoredPhoto(
    val uri: Uri,
    val localPath: String?,
    val displayName: String,
    val width: Int,
    val height: Int,
)

class PhotoStorage(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val authority = BuildConfig.APPLICATION_ID + PHOTO_FILE_PROVIDER_AUTHORITY_SUFFIX

    suspend fun store(
        temporaryFile: File,
        saveFolder: String?,
        displayName: String,
        onDestinationPrepared: suspend (String) -> Unit = {},
    ): StoredPhoto {
        require(temporaryFile.isFile && temporaryFile.length() > 0) {
            "Captured photo is empty"
        }

        val size = readDimensions(temporaryFile)

        return when {
            saveFolder == null || saveFolder == RECORDER_INTERNAL_SELECTED_VALUE ->
                storeInternal(temporaryFile, displayName, size, onDestinationPrepared)
            saveFolder == RECORDER_MEDIA_SELECTED_VALUE ->
                storeMedia(temporaryFile, displayName, size, onDestinationPrepared)

            else -> storeCustom(
                temporaryFile,
                saveFolder.toUri(),
                displayName,
                size,
                onDestinationPrepared,
            )
        }
    }

    fun uriFor(record: PhotoRecord): Uri {
        val localPath = record.localPath

        if (localPath != null) {
            val localFile = File(localPath)
            if (localFile.isFile) {
                return FileProvider.getUriForFile(appContext, authority, localFile)
            }
        }

        return record.uri.toUri()
    }

    fun isAvailable(record: PhotoRecord): Boolean {
        if (record.localPath != null && (record.uri.startsWith("file:"))) {
            return File(record.localPath).isFile
        }

        return runCatching {
            resolver.openAssetFileDescriptor(uriFor(record), "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    private suspend fun storeInternal(
        temporaryFile: File,
        displayName: String,
        size: Pair<Int, Int>,
        onDestinationPrepared: suspend (String) -> Unit,
    ): StoredPhoto {
        val folder = File(appContext.filesDir, PhotoRepository.PHOTOS_FOLDER_NAME).apply {
            mkdirs()
        }
        val destination = File(folder, displayName)
        val uri = FileProvider.getUriForFile(appContext, authority, destination)

        if (!destination.isFile || destination.length() == 0L) {
            val partial = File(folder, "$displayName.part")
            onDestinationPrepared(uri.toString())
            copyFile(temporaryFile, partial)
            require(partial.length() > 0L) { "The internal photo copy is empty" }
            if (destination.exists() && !destination.delete()) {
                throw IllegalStateException("Could not replace the internal photo")
            }
            if (!partial.renameTo(destination)) {
                throw IllegalStateException("Could not commit the internal photo")
            }
        } else {
            onDestinationPrepared(uri.toString())
        }

        return StoredPhoto(
            uri = uri,
            localPath = destination.absolutePath,
            displayName = displayName,
            width = size.first,
            height = size.second,
        )
    }

    private suspend fun storeMedia(
        temporaryFile: File,
        displayName: String,
        size: Pair<Int, Int>,
        onDestinationPrepared: suspend (String) -> Unit,
    ): StoredPhoto {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var uri: Uri? = null
            var created = false

            try {
                val existing = findExistingMediaPhoto(displayName)
                uri = existing ?: resolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
                            put(MediaStore.Images.Media.RELATIVE_PATH, PHOTO_RELATIVE_PATH)
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        },
                    ) ?: error("Could not create a MediaStore image")
                created = existing == null
                resolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    },
                    null,
                    null,
                )
                onDestinationPrepared(uri.toString())

                resolver.openOutputStream(uri, "w").use { output ->
                    temporaryFile.inputStream().use { input ->
                        requireNotNull(output) { "Could not open the MediaStore image" }
                        input.copyTo(output)
                    }
                }

                resolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    },
                    null,
                    null,
                )
                StoredPhoto(
                    uri = uri,
                    localPath = null,
                    displayName = displayName,
                    width = size.first,
                    height = size.second,
                )
            } catch (error: Exception) {
                if (created) {
                    uri?.let { runCatching { resolver.delete(it, null, null) } }
                }
                throw error
            }
        } else {
            val folder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "alibi/photos",
            ).apply {
                mkdirs()
            }
            val destination = File(folder, displayName)
            onDestinationPrepared(
                FileProvider.getUriForFile(appContext, authority, destination).toString(),
            )
            copyFile(temporaryFile, destination)
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(destination.absolutePath),
                arrayOf(MIME_TYPE),
                null,
            )

            StoredPhoto(
                uri = FileProvider.getUriForFile(appContext, authority, destination),
                localPath = destination.absolutePath,
                displayName = displayName,
                width = size.first,
                height = size.second,
            )
        }
    }

    private suspend fun storeCustom(
        temporaryFile: File,
        treeUri: Uri,
        displayName: String,
        size: Pair<Int, Int>,
        onDestinationPrepared: suspend (String) -> Unit,
    ): StoredPhoto {
        val root = requireNotNull(DocumentFile.fromTreeUri(appContext, treeUri)) {
            "The selected folder is not available"
        }
        require(root.canWrite()) { "The selected folder is not writable" }

        val folder = root.findFile(PHOTO_CUSTOM_FOLDER_NAME)
            ?: root.createDirectory(PHOTO_CUSTOM_FOLDER_NAME)
            ?: error("Could not create the photo folder")
        val document = folder.findFile(displayName) ?: folder.createFile(MIME_TYPE, displayName)
            ?: error("Could not create the photo file")

        try {
            onDestinationPrepared(document.uri.toString())
            resolver.openOutputStream(document.uri, "w").use { output ->
                temporaryFile.inputStream().use { input ->
                    requireNotNull(output) { "Could not open the selected folder" }
                    input.copyTo(output)
                }
            }
            return StoredPhoto(
                uri = document.uri,
                localPath = null,
                displayName = displayName,
                width = size.first,
                height = size.second,
            )
        } catch (error: Exception) {
            throw error
        }
    }

    private fun copyFile(source: File, destination: File) {
        source.inputStream().use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun findExistingMediaPhoto(displayName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }

        return resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(displayName),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0),
                )
            } else {
                null
            }
        }
    }

    private fun readDimensions(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth.coerceAtLeast(0) to options.outHeight.coerceAtLeast(0)
    }

    private companion object {
        const val MIME_TYPE = "image/jpeg"
    }
}
