package software.rdd.apexperformance.core.network

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import software.rdd.apexperformance.MainActivity
import software.rdd.apexperformance.R

class ApexMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushTokenService.register(token)
    }

    // Background notifications are shown by the system. This is called only
    // while the app is in the foreground, where they are shown too, as on iOS.
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            message.data[MainActivity.EXTRA_NOTIFICATION_TYPE]?.let {
                putExtra(MainActivity.EXTRA_NOTIFICATION_TYPE, it)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            message.messageId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val built = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.apex_main))
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(message.messageId.hashCode(), built)
    }

    companion object {
        const val CHANNEL_ID = "apex_default"
    }
}
