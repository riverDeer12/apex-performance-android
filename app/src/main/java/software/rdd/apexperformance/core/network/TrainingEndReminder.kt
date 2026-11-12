package software.rdd.apexperformance.core.network

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import software.rdd.apexperformance.ApexApp
import software.rdd.apexperformance.BuildConfig
import software.rdd.apexperformance.MainActivity
import software.rdd.apexperformance.R
import software.rdd.apexperformance.model.Training
import java.time.Instant

// The backend's reminder to the coach that a training they started an hour
// ago is still running (data only, type "training_end_reminder"), shown with
// "End training", which ends it without opening the app. A tap opens it.
object TrainingEndReminder {

    const val TYPE = "training_end_reminder"

    data class Content(val trainingId: String, val startedAt: Instant, val title: String, val body: String) {
        // One reminder per training on screen: a later one replaces it.
        val notificationId: Int get() = "$TYPE:$trainingId".hashCode()
    }

    // Null when the push misses something (wrong type, no training or start).
    fun parse(data: Map<String, String>): Content? {
        if (data["type"] != TYPE) return null
        val trainingId = data["trainingId"]?.takeIf { it.isNotBlank() } ?: return null
        val startedAt = data["startedAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        return Content(trainingId, startedAt, data["title"].orEmpty(), data["body"].orEmpty())
    }

    fun show(context: Context, content: Content) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NOTIFICATION_TYPE, TYPE)
            putExtra(MainActivity.EXTRA_TRAINING_ID, content.trainingId)
        }
        val openIntent = PendingIntent.getActivity(
            context, content.notificationId, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val end = Intent(context, EndTrainingReceiver::class.java).apply {
            putExtra(EXTRA_TRAINING_ID, content.trainingId)
            putExtra(EXTRA_STARTED_AT, content.startedAt.toString())
            putExtra(EXTRA_TITLE, content.title)
            putExtra(EXTRA_BODY, content.body)
        }
        val endIntent = PendingIntent.getBroadcast(
            context, content.notificationId, end,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // In the language picked in the app, like the push texts.
        val endLabel = ApexApp.appContext.getString(R.string.end_training)

        val notification = NotificationCompat.Builder(context, ApexMessagingService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.apex_main))
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .addAction(0, endLabel, endIntent)
            .build()

        NotificationManagerCompat.from(context).notify(content.notificationId, notification)
    }

    internal const val EXTRA_TRAINING_ID = "trainingId"
    internal const val EXTRA_STARTED_AT = "startedAt"
    internal const val EXTRA_TITLE = "title"
    internal const val EXTRA_BODY = "body"
}

// "End training" of the reminder: ends the training (PUT trainings/{id}/times
// with its start, the end now and onlyIfRunning) with the saved login and
// removes the notification. If that fails, the reminder stays to retry or open.
class EndTrainingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trainingId = intent.getStringExtra(TrainingEndReminder.EXTRA_TRAINING_ID) ?: return
        val startedAt = intent.getStringExtra(TrainingEndReminder.EXTRA_STARTED_AT)
            ?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return
        val content = TrainingEndReminder.Content(
            trainingId, startedAt,
            intent.getStringExtra(TrainingEndReminder.EXTRA_TITLE).orEmpty(),
            intent.getStringExtra(TrainingEndReminder.EXTRA_BODY).orEmpty()
        )

        val pending = goAsync()
        val appContext = context.applicationContext
        // Removed right away so the button isn't tapped twice.
        NotificationManagerCompat.from(appContext).cancel(content.notificationId)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // A broadcast has about 10 seconds.
                withTimeout(8_000) { Training.endFromReminder(trainingId, startedAt) }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w("TrainingEndReminder", "Training $trainingId not ended", e)
                TrainingEndReminder.show(appContext, content)
            } finally {
                pending.finish()
            }
        }
    }
}
