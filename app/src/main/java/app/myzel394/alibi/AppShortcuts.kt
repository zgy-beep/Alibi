package app.myzel394.alibi

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat

object AppShortcuts {
    const val RECORD_AUDIO_ID = "background_audio_recording"
    const val TAKE_PHOTO_ID = "background_photo_capture"

    val ACTION_RECORD_AUDIO = "${BuildConfig.APPLICATION_ID}.shortcut.RECORD_AUDIO"
    val ACTION_TAKE_PHOTO = "${BuildConfig.APPLICATION_ID}.shortcut.TAKE_PHOTO"

    fun reportUsed(context: Context, action: String?) {
        val id = when (action) {
            ACTION_RECORD_AUDIO -> RECORD_AUDIO_ID
            ACTION_TAKE_PHOTO -> TAKE_PHOTO_ID
            else -> return
        }
        runCatching { ShortcutManagerCompat.reportShortcutUsed(context, id) }
    }
}
