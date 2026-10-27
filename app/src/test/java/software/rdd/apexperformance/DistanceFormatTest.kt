package software.rdd.apexperformance

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import software.rdd.apexperformance.model.PaceType
import software.rdd.apexperformance.model.PersonalRecord
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.model.Workout
import software.rdd.apexperformance.model.distanceSetParts
import software.rdd.apexperformance.model.formatDistance
import software.rdd.apexperformance.model.formatPace
import software.rdd.apexperformance.model.formatSpeed

// Same results as the web's formatDistance, formatPace, formatSpeed and describeSet.
class DistanceFormatTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }

    @Test
    fun formatDistance_metresBelowKilometre() {
        assertEquals("800 m", formatDistance(800))
        assertEquals("1 m", formatDistance(1))
        assertEquals("", formatDistance(null))
        assertEquals("", formatDistance(0))
    }

    @Test
    fun formatDistance_kilometresWithCroatianDecimals() {
        assertEquals("1 km", formatDistance(1000))
        assertEquals("2 km", formatDistance(2000))
        assertEquals("5,25 km", formatDistance(5250))
        assertEquals("1,5 km", formatDistance(1500))
        assertEquals("1,23 km", formatDistance(1234))
        assertEquals("500 km", formatDistance(500000))
    }

    @Test
    fun formatSpeed_perKm() {
        // 1000 m in 5:30 -> 181.82 m/min.
        assertEquals("5:30 /km", formatSpeed(PaceType.PER_KM, 1000 * 60.0 / 330))
        // Without a pace type the pace is per km, like the web.
        assertEquals("5:30 /km", formatSpeed(null, 1000 * 60.0 / 330))
        assertEquals("", formatSpeed(PaceType.PER_KM, 0.0))
    }

    @Test
    fun formatSpeed_per500m() {
        // 2000 m in 7:28 -> 1:52 per 500 m.
        assertEquals("1:52 /500 m", formatSpeed(PaceType.PER_500M, 2000 * 60.0 / 448))
    }

    @Test
    fun formatSpeed_kmh() {
        // 408.33 m/min = 24.5 km/h.
        assertEquals("24,5 km/h", formatSpeed(PaceType.KMH, 24500 / 60.0))
        assertEquals("30 km/h", formatSpeed(PaceType.KMH, 500.0))
    }

    @Test
    fun formatSpeed_hourOrMoreUsesHours() {
        // 1 km in 1:02:30.
        assertEquals("1:02:30 /km", formatSpeed(PaceType.PER_KM, 1000 * 60.0 / 3750))
    }

    @Test
    fun formatPace_fromDistanceAndDuration() {
        assertEquals("1:52 /500 m", formatPace(PaceType.PER_500M, 2000, 448))
        assertEquals("5:30 /km", formatPace(PaceType.PER_KM, 5000, 1650))
        assertEquals("24 km/h", formatPace(PaceType.KMH, 10000, 1500))
        assertEquals("", formatPace(PaceType.PER_KM, 5000, null))
        assertEquals("", formatPace(PaceType.PER_KM, null, 1650))
        assertEquals("", formatPace(PaceType.PER_KM, 0, 1650))
    }

    @Test
    fun distanceSetParts_joinsDistanceDurationAndPace() {
        val set = Training.ExerciseSet(id = "1", distanceMeters = 2000, durationSeconds = 448)
        assertEquals("2 km · 7:28 · 1:52 /500 m", distanceSetParts(set, PaceType.PER_500M).joinToString(" · "))
    }

    @Test
    fun distanceSetParts_withoutDurationOrDistance() {
        assertEquals(listOf("800 m"), distanceSetParts(Training.ExerciseSet(id = "1", distanceMeters = 800), PaceType.PER_KM))
        assertEquals(
            emptyList<String>(),
            distanceSetParts(Training.ExerciseSet(id = "1", durationSeconds = 90, reps = "10"), PaceType.PER_KM)
        )
    }

    @Test
    fun parsesNewFields() {
        val workout = json.decodeFromString<Workout>("""{"id":"w","isTimed":true,"paceType":"Per500m"}""")
        assertEquals(PaceType.PER_500M, workout.paceType)

        val oldWorkout = json.decodeFromString<Workout>("""{"id":"w"}""")
        assertNull(oldWorkout.paceType)

        val exercise = json.decodeFromString<Training.Exercise>(
            """{"id":"e","workoutId":"w","paceType":"Kmh","sets":[{"id":"s","durationSeconds":600,"distanceMeters":5000}]}"""
        )
        assertEquals(PaceType.KMH, exercise.paceType)
        assertEquals(5000, exercise.sets.single().distanceMeters)

        val record = json.decodeFromString<PersonalRecord>(
            """{"id":"r","clientId":"c","workoutId":"w","trainingId":"t","type":"FastestPace","value":267.86,""" +
                """"achievedAt":"2026-10-01T10:00:00Z","paceType":"Per500m"}"""
        )
        assertEquals(true, record.isFastestPace)
        assertEquals(PaceType.PER_500M, record.paceType)
    }
}
