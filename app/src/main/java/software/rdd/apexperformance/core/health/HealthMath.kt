package software.rdd.apexperformance.core.health

import software.rdd.apexperformance.model.Training
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

// Calculations on what Health Connect returns, without Health Connect itself
// (unit tested). Same rules as AppleHealth.swift on the iPhone.
object HealthMath {

    // Trainings older than this aren't read automatically any more (as on the iPhone).
    // Health Connect also gives apps only the last 30 days before they were first allowed.
    const val SYNCED_DAYS = 30L

    // Resting heart rate: the latest one in this many days.
    const val RESTING_HEART_RATE_DAYS = 7L

    // The API accepts heart rates in this range.
    private val heartRateRange = 25..250

    data class Interval(val start: Instant, val end: Instant)

    // A sleep session; stages are empty when the app that wrote it doesn't record them.
    data class SleepSession(val start: Instant, val end: Instant, val stages: List<Stage> = emptyList()) {
        data class Stage(val start: Instant, val end: Instant, val type: Int)
    }

    // Stage types counted as asleep (SleepSessionRecord.STAGE_TYPE_*): sleeping, light, deep, REM.
    // Awake, awake in bed, out of bed and unknown are not.
    private val asleepStages = setOf(
        androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_SLEEPING,
        androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_LIGHT,
        androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_DEEP,
        androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_REM
    )

    // Last night starts at 18:00 the day before, as on the iPhone.
    fun sleepWindowStart(now: Instant, zone: ZoneId): Instant =
        dayStart(now, zone).minus(Duration.ofHours(6))

    fun dayStart(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

    // Asleep parts of the sessions: their asleep stages, or the whole session without stages.
    fun asleepIntervals(sessions: List<SleepSession>): List<Interval> =
        sessions.flatMap { session ->
            if (session.stages.isEmpty()) listOf(Interval(session.start, session.end))
            else session.stages.filter { it.type in asleepStages }.map { Interval(it.start, it.end) }
        }

    // Asleep minutes between the times, overlapping sessions of several apps
    // (watch, band, phone) counted once; null without sleep.
    fun asleepMinutes(intervals: List<Interval>, from: Instant, to: Instant): Int? {
        val clipped = intervals
            .map { Interval(maxOf(it.start, from), minOf(it.end, to)) }
            .filter { it.start < it.end }
            .sortedBy { it.start }
        if (clipped.isEmpty()) return null

        var total = Duration.ZERO
        var current = clipped.first()
        for (interval in clipped.drop(1)) {
            if (interval.start <= current.end) {
                current = current.copy(end = maxOf(current.end, interval.end))
            } else {
                total += Duration.between(current.start, current.end)
                current = interval
            }
        }
        total += Duration.between(current.start, current.end)
        return (total.seconds / 60.0).roundToInt()
    }

    // Lowest, highest and average heart rate from Health Connect's aggregate,
    // null without measurements. Values the API wouldn't take are kept in its range.
    fun heartRate(min: Long?, max: Long?, average: Long?, source: String = SOURCE): Training.HeartRate? {
        if (min == null || max == null || average == null) return null
        fun bounded(value: Long) = value.coerceIn(heartRateRange.first.toLong(), heartRateRange.last.toLong()).toInt()
        val low = bounded(min)
        val high = bounded(maxOf(max, min))
        return Training.HeartRate(low, high, bounded(average).coerceIn(low, high), source)
    }

    // Whether the training has a start and end but no heart rate yet.
    fun needsHeartRate(training: Training): Boolean =
        training.heartRate == null && training.startedAt != null && training.endedAt != null

    // Trainings whose heart rate is read automatically: missing it and ended in the last 30 days.
    fun trainingsToSync(trainings: List<Training>, now: Instant): List<Training> {
        val oldest = now.minus(Duration.ofDays(SYNCED_DAYS))
        return trainings.filter { needsHeartRate(it) && (it.endedAt ?: now) > oldest }
    }

    // For example "7 h 42 min".
    fun sleepParts(minutes: Int): Pair<Int, Int> = minutes / 60 to minutes % 60

    // The API's source of heart rates read here.
    const val SOURCE = "health_connect"
}
