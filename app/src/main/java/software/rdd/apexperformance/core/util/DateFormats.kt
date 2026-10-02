package software.rdd.apexperformance.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Same formats as the iOS DateFormatter extensions, in the device's time zone.
object DateFormats {
    private val dateWithDots = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val dateAndTimeWithDots = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
    private val apiDate = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    val zone: ZoneId get() = ZoneId.systemDefault()

    fun date(instant: Instant): String = dateWithDots.format(instant.atZone(zone))

    fun date(date: LocalDate): String = dateWithDots.format(date)

    fun dateAndTime(instant: Instant): String = dateAndTimeWithDots.format(instant.atZone(zone))

    // Full weekday name in the app language, e.g. "Monday".
    fun dayName(instant: Instant, locale: Locale = Locale.getDefault()): String =
        instant.atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            .replaceFirstChar { it.titlecase(locale) }

    fun apiDate(date: LocalDate): String = apiDate.format(date)

    fun localDate(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    // Combines a day with a "HH:mm:ss" time from a time slot.
    fun dateTime(day: LocalDate, time: String): Instant? =
        runCatching { LocalDateTime.of(day, LocalTime.parse(time)).atZone(zone).toInstant() }.getOrNull()
}

// Measurements are shown with at most one decimal, e.g. "80.4" or "81".
fun formatNumber(value: Double): String {
    val rounded = Math.round(value * 10) / 10.0
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
}
