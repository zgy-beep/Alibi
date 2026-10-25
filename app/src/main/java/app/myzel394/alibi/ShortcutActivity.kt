package app.myzel394.alibi

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.enums.RecorderState
import app.myzel394.alibi.helpers.AudioBatchesFolder
import app.myzel394.alibi.services.AudioRecorderService
import app.myzel394.alibi.services.PhotoCaptureMode
import app.myzel394.alibi.services.PhotoCaptureService
import app.myzel394.alibi.services.RecorderNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

class ShortcutActivity : AppCompatActivity() {
    private var connection: ServiceConnection? = null
    private var requestedAudioService: AudioRecorderService? = null
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val action = intent.action
        if (action != AppShortcuts.ACTION_RECORD_AUDIO &&
            action != AppShortcuts.ACTION_TAKE_PHOTO
        ) {
            finishAndRemoveTask()
            return
        }

        AppShortcuts.reportUsed(this, action)
        lifecycleScope.launch {
            val settings = runCatching { dataStore.data.first() }.getOrNull()
            if (settings == null) {
                openMain()
                return@launch
            }

            when (action) {
                AppShortcuts.ACTION_RECORD_AUDIO -> startAudioRecording(settings)
                AppShortcuts.ACTION_TAKE_PHOTO -> startPhotoCapture(settings)
            }
        }
    }

    override fun onDestroy() {
        connection?.let { serviceConnection ->
            runCatching { unbindService(serviceConnection) }
        }
        connection = null
        super.onDestroy()
    }

    private fun startAudioRecording(settings: AppSettings) {
        if (!hasAudioPermissions(settings)) {
            showPermissionRequired()
            return
        }

        val notificationDetails = settings.notificationSettings?.let {
            RecorderNotificationHelper.NotificationDetails.fromNotificationSettings(this, it)
        }
        val serviceIntent = Intent(this, AudioRecorderService::class.java).apply {
            action = "init"
            if (notificationDetails != null) {
                putExtra(
                    "notificationDetails",
                    Json.encodeToString(
                        RecorderNotificationHelper.NotificationDetails.serializer(),
                        notificationDetails,
                    ),
                )
            }
        }

        val connectedService = CompletableDeferred<AudioRecorderService>()
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as? app.myzel394.alibi.services.RecorderService.RecorderBinder)
                    ?.getService() as? AudioRecorderService
                if (service == null) {
                    connectedService.completeExceptionally(
                        IllegalStateException("Unexpected recorder binder"),
                    )
                    return
                }
                requestedAudioService = service
                connectedService.complete(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                connectedService.completeExceptionally(
                    IllegalStateException("Recorder service disconnected"),
                )
            }
        }
        connection = serviceConnection

        try {
            check(bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)) {
                "Could not bind recorder service"
            }
        } catch (_: Exception) {
            openMain()
            return
        }

        lifecycleScope.launch {
            val result = withTimeoutOrNull(SHORTCUT_TIMEOUT_MILLIS) {
                val service = connectedService.await()
                if (service.state != RecorderState.IDLE) {
                    return@withTimeoutOrNull service to (service.state != RecorderState.ERROR)
                }

                val batchesFolder = runCatching {
                    withContext(Dispatchers.IO) { createAudioFolder(settings) }
                }.getOrNull() ?: return@withTimeoutOrNull service to false

                ContextCompat.startForegroundService(this@ShortcutActivity, serviceIntent)
                service.settings = settings
                service.batchesFolder = batchesFolder
                service to service.startRecording(beforeStart = {
                    batchesFolder.deleteRecordings()
                })
            }

            if (completed) return@launch
            val service = result?.first
            val started = result?.second == true
            if (started) {
                finishShortcut()
            } else {
                (service ?: requestedAudioService)?.let { failedService ->
                    if (failedService.state in listOf(
                            RecorderState.STARTING,
                            RecorderState.ERROR,
                        )
                    ) {
                        failedService.destroy()
                    }
                }
                openMain()
            }
        }
    }

    private fun startPhotoCapture(settings: AppSettings) {
        if (!hasPhotoPermissions()) {
            showPermissionRequired()
            return
        }

        try {
            ContextCompat.startForegroundService(
                this,
                PhotoCaptureService.createStartIntent(
                    context = this,
                    settings = settings,
                    mode = PhotoCaptureMode.FLOATING,
                    startInBackground = true,
                ),
            )
            finishAndRemoveTask()
        } catch (_: Exception) {
            openMain()
        }
    }

    private fun createAudioFolder(settings: AppSettings): AudioBatchesFolder {
        return when (settings.saveFolder) {
            null -> AudioBatchesFolder.viaInternalFolder(this)
            app.myzel394.alibi.ui.RECORDER_MEDIA_SELECTED_VALUE ->
                AudioBatchesFolder.viaMediaFolder(this)
            else -> AudioBatchesFolder.viaCustomFolder(
                this,
                DocumentFile.fromTreeUri(
                    this,
                    settings.saveFolder.toUri(),
                )!!,
            )
        }
    }

    private fun hasAudioPermissions(settings: AppSettings): Boolean {
        val hasMicrophone = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        val needsLegacyStorage = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            settings.saveFolder == app.myzel394.alibi.ui.RECORDER_MEDIA_SELECTED_VALUE
        val hasStorage = !needsLegacyStorage || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        return hasMicrophone && hasStorage
    }

    private fun hasPhotoPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED && Settings.canDrawOverlays(this)
    }

    private fun showPermissionRequired() {
        Toast.makeText(this, R.string.shortcut_permission_required, Toast.LENGTH_SHORT).show()
        openMain()
    }

    private fun finishShortcut() {
        if (completed) return
        completed = true
        connection?.let { runCatching { unbindService(it) } }
        connection = null
        requestedAudioService = null
        finishAndRemoveTask()
    }

    private fun openMain() {
        if (completed) return
        completed = true
        connection?.let { runCatching { unbindService(it) } }
        connection = null
        requestedAudioService = null
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
        )
        finishAndRemoveTask()
    }

    private companion object {
        const val SHORTCUT_TIMEOUT_MILLIS = 8_000L
    }
}
