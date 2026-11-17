package software.rdd.apexperformance

import androidx.health.connect.client.records.SleepSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import software.rdd.apexperformance.core.health.HealthMath
import software.rdd.apexperformance.core.health.HealthMath.Interval
import software.rdd.apexperformance.core.health.HealthMath.SleepSession
import software.rdd.apexperformance.model.Training
import java.time.Instant
import java.time.ZoneId

class HealthMathTest {

    private val zagreb = ZoneId.of("Europe/Zagreb")
    private fun at(time: String) = Instant.parse(time)
    private fun interval(start: String, end: String) = Interval(at(start), at(end))

    private fun training(
        id: String = "t",
        startedAt: String? = "2026-11-10T17:00:00Z",
        endedAt: String? = "2026-11-10T18:10:00Z",
        heartRate: Training.HeartRate? = null
    ) = Training(
        id = id,
        date = at("2026-11-10T17:00:00Z"),
        client = Training.Person("ana", "Ana", "Kovač"),
        startedAt = startedAt?.let(::at),
        endedAt = endedAt?.let(::at),
        heartRate = heartRate
    )

    // MARK: - Day and night

    @Test
    fun lastNightStartsAt18TheDayBefore() {
        val now = at("2026-11-17T07:30:00Z") // 08:30 in Zagreb
        assertEquals(at("2026-11-16T23:00:00Z"), HealthMath.dayStart(now, zagreb))
        assertEquals(at("2026-11-16T17:00:00Z"), HealthMath.sleepWindowStart(now, zagreb))
    }

    // MARK: - Sleep

    @Test
    fun overlappingSessionsOfSeveralAppsAreCountedOnce() {
        // Band 23:00–06:30, phone 23:30–07:00: asleep 23:00–07:00.
        val minutes = HealthMath.asleepMinutes(
            listOf(
                interval("2026-11-16T22:00:00Z", "2026-11-17T05:30:00Z"),
                interval("2026-11-16T22:30:00Z", "2026-11-17T06:00:00Z")
            ),
            from = at("2026-11-16T17:00:00Z"),
            to = at("2026-11-17T07:30:00Z")
        )
        assertEquals(8 * 60, minutes)
    }

    @Test
    fun separateSleepIsAddedAndClippedToTheWindow() {
        val minutes = HealthMath.asleepMinutes(
            listOf(
                // Afternoon nap before the window: only 17:00–17:30 counts.
                interval("2026-11-16T16:00:00Z", "2026-11-16T17:30:00Z"),
                interval("2026-11-16T23:00:00Z", "2026-11-17T01:00:00Z"),
                // Still asleep now: counted until now.
                interval("2026-11-17T02:00:00Z", "2026-11-17T09:00:00Z")
            ),
            from = at("2026-11-16T17:00:00Z"),
            to = at("2026-11-17T07:00:00Z")
        )
        assertEquals(30 + 120 + 300, minutes)
    }

    @Test
    fun noSleepIsNull() {
        assertNull(HealthMath.asleepMinutes(emptyList(), at("2026-11-16T17:00:00Z"), at("2026-11-17T07:00:00Z")))
        // Only outside the window.
        assertNull(
            HealthMath.asleepMinutes(
                listOf(interval("2026-11-16T10:00:00Z", "2026-11-16T12:00:00Z")),
                at("2026-11-16T17:00:00Z"), at("2026-11-17T07:00:00Z")
            )
        )
    }

    @Test
    fun stagesCountOnlyAsleep() {
        val start = at("2026-11-16T22:00:00Z")
        val session = SleepSession(
            start, at("2026-11-17T06:00:00Z"),
            listOf(
                SleepSession.Stage(start, at("2026-11-16T22:20:00Z"), SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED),
                SleepSession.Stage(at("2026-11-16T22:20:00Z"), at("2026-11-17T01:00:00Z"), SleepSessionRecord.STAGE_TYPE_LIGHT),
                SleepSession.Stage(at("2026-11-17T01:00:00Z"), at("2026-11-17T02:30:00Z"), SleepSessionRecord.STAGE_TYPE_DEEP),
                SleepSession.Stage(at("2026-11-17T02:30:00Z"), at("2026-11-17T02:40:00Z"), SleepSessionRecord.STAGE_TYPE_AWAKE),
                SleepSession.Stage(at("2026-11-17T02:40:00Z"), at("2026-11-17T04:00:00Z"), SleepSessionRecord.STAGE_TYPE_REM),
                SleepSession.Stage(at("2026-11-17T04:00:00Z"), at("2026-11-17T05:45:00Z"), SleepSessionRecord.STAGE_TYPE_SLEEPING),
                SleepSession.Stage(at("2026-11-17T05:45:00Z"), at("2026-11-17T06:00:00Z"), SleepSessionRecord.STAGE_TYPE_OUT_OF_BED)
            )
        )
        val minutes = HealthMath.asleepMinutes(
            HealthMath.asleepIntervals(listOf(session)), at("2026-11-16T17:00:00Z"), at("2026-11-17T07:00:00Z")
        )
        // 8 h in bed without 20 min awake in bed, 10 min awake and 15 min out of bed.
        assertEquals(8 * 60 - 20 - 10 - 15, minutes)
    }

    @Test
    fun sessionWithoutStagesIsAllAsleep() {
        val session = SleepSession(at("2026-11-16T22:00:00Z"), at("2026-11-17T05:42:00Z"))
        assertEquals(listOf(interval("2026-11-16T22:00:00Z", "2026-11-17T05:42:00Z")), HealthMath.asleepIntervals(listOf(session)))
        assertEquals(7 to 42, HealthMath.sleepParts(462))
    }

    // MARK: - Heart rate

    @Test
    fun heartRateFromTheAggregate() {
        assertEquals(Training.HeartRate(88, 171, 132, "health_connect"), HealthMath.heartRate(88, 171, 132))
    }

    @Test
    fun noMeasurementsIsNull() {
        assertNull(HealthMath.heartRate(null, null, null))
        assertNull(HealthMath.heartRate(60, null, 100))
    }

    @Test
    fun heartRateIsKeptInTheRangeTheApiTakes() {
        // A strap losing contact can record nonsense; the API takes 25–250 with min ≤ average ≤ max.
        val heartRate = HealthMath.heartRate(0, 300, 120)!!
        assertEquals(25, heartRate.min)
        assertEquals(250, heartRate.max)
        assertEquals(120, heartRate.average)
        assertTrue(heartRate.min <= heartRate.average && heartRate.average <= heartRate.max)
    }

    // MARK: - Trainings to sync

    @Test
    fun needsHeartRateOnlyWhenMarkedAndMissing() {
        assertTrue(HealthMath.needsHeartRate(training()))
        assertFalse(HealthMath.needsHeartRate(training(endedAt = null)))
        assertFalse(HealthMath.needsHeartRate(training(startedAt = null, endedAt = null)))
        assertFalse(HealthMath.needsHeartRate(training(heartRate = Training.HeartRate(60, 150, 110, "apple_health"))))
    }

    @Test
    fun onlyTheLast30DaysAreSynced() {
        val now = at("2026-11-17T12:00:00Z")
        val trainings = listOf(
            training(id = "recent"),
            training(id = "old", startedAt = "2026-10-01T17:00:00Z", endedAt = "2026-10-01T18:00:00Z"),
            training(id = "running", endedAt = null),
            training(id = "done", heartRate = Training.HeartRate(60, 150, 110))
        )
        assertEquals(listOf("recent"), HealthMath.trainingsToSync(trainings, now).map { it.id })
    }
}
