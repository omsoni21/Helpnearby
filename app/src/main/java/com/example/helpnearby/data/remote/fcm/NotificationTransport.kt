package com.example.helpnearby.data.remote.fcm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.helpnearby.domain.model.AppNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Push notification abstraction so Firebase Cloud Messaging (FCM) can be plugged in later
 * once the owner provides google-services.json, while local in-app notifications work offline-first.
 */
interface PushNotificationTransport {
    suspend fun registerDeviceTokenIfConfigured(): Result<String>
    fun dispatchLocalSystemAlert(notification: AppNotification)
}

@Singleton
class LocalAndFcmNotificationTransport @Inject constructor(
    @ApplicationContext private val context: Context
) : PushNotificationTransport {

    init {
        createNotificationChannel()
    }

    override suspend fun registerDeviceTokenIfConfigured(): Result<String> {
        // Placeholder hook for FirebaseMessaging.getInstance().token when FCM is configured.
        return Result.failure(IllegalStateException("FCM not configured; using in-app Notification Center."))
    }

    override fun dispatchLocalSystemAlert(notification: AppNotification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(notification.id.hashCode(), builder.build())
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "HelpNearby Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Community help offers, messages, and safety updates"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "helpnearby_community_alerts"
    }
}
