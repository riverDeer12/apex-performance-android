package software.rdd.apexperformance.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import software.rdd.apexperformance.MainActivity
import software.rdd.apexperformance.R
import software.rdd.apexperformance.core.util.AppLanguage
import software.rdd.apexperformance.core.util.DateFormats
import software.rdd.apexperformance.model.Appointment
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

// Home screen widget (classic RemoteViews, no extra library). Client: next
// appointment ("za 2 h", coach) and package ("5 / 12"). Coach: next
// appointment today (time, client) and pending requests. Logged out:
// "Prijavi se u Apex". Tapping it opens the app on appointments (or login).
class ApexWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        ApexWidget.render(context, manager, appWidgetIds)
    }
}

object ApexWidget {
    private const val PREFERENCES = "widget"
    private const val KEY = "snapshot"

    // MARK: - Writing (the app)

    fun saveClient(context: Context, next: Appointment?, nextIsPending: Boolean, coachName: String?, packageDone: Int, packageTotal: Int) =
        save(
            context,
            WidgetSnapshot(
                role = WidgetSnapshot.CLIENT,
                nextStart = next?.startTime?.toEpochMilli(),
                nextEnd = next?.endTime?.toEpochMilli(),
                nextIsPending = nextIsPending,
                personName = coachName,
                packageDone = packageDone,
                packageTotal = packageTotal
            )
        )

    fun saveStaff(context: Context, nextToday: Appointment?, pendingRequests: Int) =
        save(
            context,
            WidgetSnapshot(
                role = WidgetSnapshot.STAFF,
                nextStart = nextToday?.startTime?.toEpochMilli(),
                nextEnd = nextToday?.endTime?.toEpochMilli(),
                personName = nextToday?.clients?.joinToString(", ") { it.fullName.trim() }?.ifBlank { null },
                pendingRequests = pendingRequests
            )
        )

    fun loggedIn(context: Context) = save(context, WidgetSnapshot(role = WidgetSnapshot.LOGGED_IN))

    fun loggedOut(context: Context) = save(context, WidgetSnapshot(role = WidgetSnapshot.LOGGED_OUT))

    private fun save(context: Context, snapshot: WidgetSnapshot) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val text = WidgetSnapshot.encode(snapshot)
        if (preferences.getString(KEY, null) == text) return
        preferences.edit().putString(KEY, text).apply()
        update(context)
    }

    // Re-draws every widget on the home screen.
    fun update(context: Context) {
        try {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ApexWidgetProvider::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        } catch (e: Exception) {
            // No widgets (or no widget host): nothing to draw.
        }
    }

    // MARK: - Drawing (the widget)

    fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Texts in the language picked in the app.
        val localized = AppLanguage.wrap(context)
        val snapshot = WidgetSnapshot.decode(
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, null)
        ) ?: WidgetSnapshot(role = WidgetSnapshot.LOGGED_OUT)
        val content = content(localized, snapshot, Instant.now())

        val views = RemoteViews(context.packageName, R.layout.widget_apex).apply {
            setText(R.id.widget_label, content.label)
            setText(R.id.widget_title, content.title)
            setTextOrHide(R.id.widget_subtitle, content.subtitle)
            setTextOrHide(R.id.widget_footer, content.footer)
            setOnClickPendingIntent(R.id.widget_root, openIntent(context, snapshot.role))
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    private class Content(val label: String, val title: String, val subtitle: String?, val footer: String?)

    private fun content(context: Context, snapshot: WidgetSnapshot, now: Instant): Content {
        val start = snapshot.nextStart?.let { Instant.ofEpochMilli(it) }
        val end = snapshot.nextEnd?.let { Instant.ofEpochMilli(it) }
        return when (snapshot.role) {
            WidgetSnapshot.CLIENT -> {
                val upcoming = start?.takeIf { it.isAfter(now) }
                Content(
                    label = context.getString(if (snapshot.nextIsPending) R.string.next_appointment_pending else R.string.next_appointment),
                    title = upcoming?.let { dayAndTime(context, it, now) } ?: context.getString(R.string.widget_no_appointment),
                    subtitle = upcoming?.let { listOfNotNull(countdown(context, now, it), snapshot.personName).joinToString(" · ") },
                    footer = if (snapshot.packageTotal != null && snapshot.packageDone != null && snapshot.packageTotal > 0) {
                        context.getString(R.string.widget_package, snapshot.packageDone, snapshot.packageTotal)
                    } else null
                )
            }
            WidgetSnapshot.STAFF -> {
                // Only today's, and gone once it is over.
                val today = start?.takeIf {
                    DateFormats.localDate(it) == DateFormats.localDate(now) && (end ?: it).isAfter(now)
                }
                Content(
                    label = context.getString(R.string.today),
                    title = today?.let { listOfNotNull(DateFormats.time(it), snapshot.personName).joinToString(" · ") }
                        ?: context.getString(R.string.widget_no_more_today),
                    subtitle = today?.let { if (it.isAfter(now)) countdown(context, now, it) else context.getString(R.string.in_progress) },
                    footer = context.getString(R.string.widget_pending_requests, snapshot.pendingRequests ?: 0)
                )
            }
            WidgetSnapshot.LOGGED_IN -> Content(
                label = context.getString(R.string.app_name),
                title = context.getString(R.string.widget_open_app),
                subtitle = null,
                footer = null
            )
            else -> Content(
                label = context.getString(R.string.app_name),
                title = context.getString(R.string.widget_log_in),
                subtitle = null,
                footer = null
            )
        }
    }

    // "Danas · 18:00", "Sutra · 07:30" or "21.11.2026 · 18:00".
    private fun dayAndTime(context: Context, start: Instant, now: Instant): String {
        val days = ChronoUnit.DAYS.between(DateFormats.localDate(now), DateFormats.localDate(start))
        val day = when (days) {
            0L -> context.getString(R.string.today)
            1L -> context.getString(R.string.tomorrow)
            else -> DateFormats.date(start)
        }
        return "$day · ${DateFormats.time(start)}"
    }

    // "za 45 min", "za 2 h" or "za 3 dana", like home.
    private fun countdown(context: Context, now: Instant, start: Instant): String {
        val minutes = Duration.between(now, start).toMinutes()
        val days = ChronoUnit.DAYS.between(DateFormats.localDate(now), DateFormats.localDate(start)).toInt()
        return when {
            minutes < 60 -> context.getString(R.string.countdown_minutes, maxOf(minutes, 1).toInt())
            minutes < 24 * 60 || days < 1 -> context.getString(R.string.countdown_hours, (minutes / 60).toInt())
            else -> context.resources.getQuantityString(R.plurals.countdown_days, days, days)
        }
    }

    private fun RemoteViews.setTextOrHide(id: Int, text: String?) {
        if (text.isNullOrBlank()) {
            setViewVisibility(id, View.GONE)
        } else {
            setViewVisibility(id, View.VISIBLE)
            setTextViewText(id, text)
        }
    }

    private fun RemoteViews.setText(id: Int, text: String) = setTextViewText(id, text)

    // Appointments for a logged-in user (the tab the appointment push opens), else the app.
    private fun openIntent(context: Context, role: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (role == WidgetSnapshot.CLIENT || role == WidgetSnapshot.STAFF) {
                putExtra(MainActivity.EXTRA_NOTIFICATION_TYPE, "appointment_updated")
            }
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val REQUEST_CODE = 4201
}
