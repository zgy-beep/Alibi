package app.myzel394.alibi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

object NotificationHelper {
    const val RECORDER_CHANNEL_ID = "recorder"
    const val RECORDER_CHANNEL_NOTIFICATION_ID = 1
    const val PHOTO_CHANNEL_ID = "photo_capture"
    const val PHOTO_NOTIFICATION_ID = 2

    @RequiresApi(Build.VERSION_CODES.O)
    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            RECORDER_CHANNEL_ID,
            context.resources.getString(R.string.notificationChannels_recorder_name),
            android.app.NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = context.resources.getString(R.string.notificationChannels_recorder_description)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val photoChannel = NotificationChannel(
            PHOTO_CHANNEL_ID,
            context.resources.getString(R.string.notificationChannels_photo_name),
            android.app.NotificationManager.IMPORTANCE_LOW,
        )
        photoChannel.description =
            context.resources.getString(R.string.notificationChannels_photo_description)
        photoChannel.setShowBadge(false)
        notificationManager.createNotificationChannel(photoChannel)
    }

}
