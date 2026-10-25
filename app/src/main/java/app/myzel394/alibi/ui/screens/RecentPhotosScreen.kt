package app.myzel394.alibi.ui.screens

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.myzel394.alibi.R
import app.myzel394.alibi.helpers.PhotoRecord
import app.myzel394.alibi.helpers.PhotoRepository
import app.myzel394.alibi.helpers.PhotoStorage
import app.myzel394.alibi.ui.components.atoms.TitleWithRunningStatus
import app.myzel394.alibi.ui.components.atoms.AlibiTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentPhotosScreen(
    onBackNavigate: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { PhotoRepository(context) }
    val storage = remember { PhotoStorage(context) }
    val availabilityCache = remember { mutableMapOf<String, Boolean>() }
    val thumbnailCache = remember { PhotoThumbnailCache() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<PhotoRecord>?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showAll by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        records = repository.records()
    }

    fun openPhoto(record: PhotoRecord) {
        val uri = runCatching { storage.uriFor(record) }.getOrNull() ?: return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/jpeg")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }.onFailure {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.photo_open_failed))
            }
        }
    }

    fun sharePhoto(record: PhotoRecord) {
        val uri = runCatching { storage.uriFor(record) }.getOrNull() ?: return
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri(record.displayName, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    context.getString(R.string.photo_recent_share),
                )
            )
        }.onFailure {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.photo_open_failed))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AlibiTopAppBar(
                title = stringResource(R.string.photo_recent_title),
                onBack = onBackNavigate,
                actions = {
                    IconButton(
                        onClick = {
                            availabilityCache.clear()
                            refreshKey++
                        },
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.photo_recent_refresh),
                        )
                    }
                },
            )
        }
    ) { padding ->
        val loadedRecords = records
        if (loadedRecords == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (loadedRecords.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.photo_recent_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            val visibleRecords = if (showAll) loadedRecords else loadedRecords.take(50)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visibleRecords, key = { it.id }) { record ->
                    PhotoRecordCard(
                        record = record,
                        storage = storage,
                        availabilityCache = availabilityCache,
                        thumbnailCache = thumbnailCache,
                        loadUri = { storage.uriFor(record) },
                        onOpen = { openPhoto(record) },
                        onShare = { sharePhoto(record) },
                    )
                }
                if (!showAll && loadedRecords.size > visibleRecords.size) {
                    item {
                        TextButton(
                            onClick = { showAll = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.photo_recent_show_all))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoRecordCard(
    record: PhotoRecord,
    storage: PhotoStorage,
    availabilityCache: MutableMap<String, Boolean>,
    thumbnailCache: PhotoThumbnailCache,
    loadUri: () -> Uri,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    val available by produceState<Boolean?>(initialValue = null, record.id) {
        value = availabilityCache[record.id] ?: withContext(Dispatchers.IO) {
            storage.isAvailable(record).also {
                availabilityCache[record.id] = it
            }
        }
    }
    val isAvailable = available == true
    val date = remember(record.createdAtEpochMillis) {
        Instant.ofEpochMilli(record.createdAtEpochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isAvailable, onClick = onOpen),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isAvailable) {
                PhotoThumbnail(record.id, loadUri, thumbnailCache)
            } else {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                )
                Text(
                    text = stringResource(R.string.photo_recent_taken, date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (available == false) {
                    Text(
                        text = stringResource(R.string.photo_recent_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = onShare, enabled = isAvailable) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(R.string.photo_recent_share),
                )
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(
    cacheKey: String,
    loadUri: () -> Uri,
    cache: PhotoThumbnailCache,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = cache.get(cacheKey), cacheKey) {
        value = cache.get(cacheKey) ?: withContext(Dispatchers.IO) {
            runCatching {
                val uri = loadUri()
                val bounds = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val sample = bitmapSampleSize(bounds.outWidth, bounds.outHeight, 128)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }?.also { cache.put(cacheKey, it) }
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = stringResource(R.string.photo_recent_open),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Icon(
            Icons.Default.Image,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
        )
    }
}

private class PhotoThumbnailCache : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private fun bitmapSampleSize(width: Int, height: Int, target: Int): Int {
    var sample = 1
    var longest = maxOf(width, height)
    while (longest / 2 >= target) {
        longest /= 2
        sample *= 2
    }
    return sample
}
