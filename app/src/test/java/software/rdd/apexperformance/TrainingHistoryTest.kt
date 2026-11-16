package software.rdd.apexperformance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.model.TrainingHistory
import java.time.Instant

class TrainingHistoryTest {

    private val ana = Training.Person("ana", "Ana", "Kovač")
    private val marko = Training.Person("marko", "Marko", "Horvat")

    private fun set(id: String, weight: Double?, reps: String? = "8", isDropSet: Boolean = false, order: Int = 0) =
        Training.ExerciseSet(id = id, order = order, reps = reps, weight = weight, isDropSet = isDropSet)

    private fun exercise(id: String, workoutId: String, vararg sets: Training.ExerciseSet, note: String? = null, order: Int = 0) =
        Training.Exercise(id = id, workoutId = workoutId, order = order, note = note, sets = sets.toList())

    private fun training(
        id: String,
        date: String,
        vararg exercises: Training.Exercise,
        name: String = "Legs",
        client: Training.Person = ana,
        isCompleted: Boolean = true
    ) = Training(
        id = id,
        name = name,
        date = Instant.parse(date),
        isCompleted = isCompleted,
        client = client,
        exercises = exercises.toList()
    )

    @Test
    fun bestWeightIsTheHeaviestSetThatIsNotADropSet() {
        val trainings = listOf(
            training("t1", "2026-11-01T10:00:00Z", exercise("e1", "bench", set("s1", 60.0), set("s2", 70.0, reps = "6"))),
            // A heavier drop set doesn't count.
            training("t2", "2026-11-05T10:00:00Z", exercise("e2", "bench", set("s3", 65.0), set("s4", 90.0, isDropSet = true))),
            training("t3", "2026-11-08T10:00:00Z", exercise("e3", "squat", set("s5", 100.0, reps = "5")))
        )

        val records = TrainingHistory.bestWeights(trainings)

        // The most recent record first.
        assertEquals(listOf("squat", "bench"), records.map { it.workoutId })
        val bench = records.first { it.workoutId == "bench" }
        assertEquals(70.0, bench.weight, 0.0)
        assertEquals("6", bench.reps)
        assertEquals("t1", bench.trainingId)
    }

    @Test
    fun repeatingARecordKeepsTheFirstDateAndPlannedTrainingsDontCount() {
        val trainings = listOf(
            training("t1", "2026-11-01T10:00:00Z", exercise("e1", "bench", set("s1", 70.0))),
            training("t2", "2026-11-05T10:00:00Z", exercise("e2", "bench", set("s2", 70.0))),
            training("t3", "2026-11-09T10:00:00Z", exercise("e3", "bench", set("s3", 120.0)), isCompleted = false)
        )

        val bench = TrainingHistory.bestWeights(trainings).single()

        assertEquals(70.0, bench.weight, 0.0)
        assertEquals(Instant.parse("2026-11-01T10:00:00Z"), bench.date)
    }

    @Test
    fun newRecordIsHeavierThanEveryEarlierSetOfTheSameClient() {
        val earlier = training("t1", "2026-11-01T10:00:00Z", exercise("e1", "bench", set("s1", 70.0)), exercise("e2", "squat", set("s2", 100.0)))
        // Marko's heavier bench doesn't matter for Ana.
        val other = training("m1", "2026-11-02T10:00:00Z", exercise("m2", "bench", set("m3", 120.0)), client = marko)
        val latest = training(
            "t2", "2026-11-08T10:00:00Z",
            exercise("e3", "bench", set("s3", 70.0), set("s4", 72.5, reps = "5"), set("s5", 80.0, isDropSet = true)),
            exercise("e4", "squat", set("s6", 100.0)),
            // Done for the first time: nothing to beat yet.
            exercise("e5", "deadlift", set("s7", 140.0))
        )

        val records = TrainingHistory.newRecords(latest, listOf(earlier, other, latest))

        assertEquals(mapOf("e3" to "s4"), records)
    }

    @Test
    fun laterTrainingsDontTakeTheRecordAwayFromAnEarlierOne() {
        val first = training("t1", "2026-11-01T10:00:00Z", exercise("e1", "bench", set("s1", 60.0)))
        val record = training("t2", "2026-11-05T10:00:00Z", exercise("e2", "bench", set("s2", 70.0)))
        val heavier = training("t3", "2026-11-09T10:00:00Z", exercise("e3", "bench", set("s3", 80.0)))

        assertEquals(mapOf("e2" to "s2"), TrainingHistory.newRecords(record, listOf(first, record, heavier)))
        assertTrue(TrainingHistory.newRecords(first, listOf(first, record, heavier)).isEmpty())
    }

    @Test
    fun repeatPrefersTheLatestTrainingWithTheTypedName() {
        val legs = training("t1", "2026-11-01T10:00:00Z", exercise("e1", "squat", set("s1", 100.0)), name = "Legs")
        val push = training("t2", "2026-11-05T10:00:00Z", exercise("e2", "bench", set("s2", 70.0)), name = "Push")
        val oldLegs = training("t0", "2026-10-20T10:00:00Z", exercise("e0", "squat", set("s0", 90.0)), name = "Legs")
        val history = listOf(oldLegs, legs, push)
        val now = Instant.parse("2026-11-16T10:00:00Z")

        assertEquals("t1", TrainingHistory.repeatSource(history, "ana", " legs ", now = now)?.id)
        // No training with that name: the latest one.
        assertEquals("t2", TrainingHistory.repeatSource(history, "ana", "Pull", now = now)?.id)
        assertEquals("t2", TrainingHistory.repeatSource(history, "ana", "", now = now)?.id)
    }

    @Test
    fun repeatSkipsOtherClientsEmptyAndFutureTrainingsAndTheOneBeingEdited() {
        val now = Instant.parse("2026-11-16T10:00:00Z")
        val done = training("t1", "2026-11-01T10:00:00Z", exercise("e1", "squat", set("s1", 100.0)))
        val empty = training("t2", "2026-11-10T10:00:00Z")
        val planned = training("t3", "2026-11-20T10:00:00Z", exercise("e3", "squat", set("s3", 100.0)), isCompleted = false)
        val markos = training("m1", "2026-11-12T10:00:00Z", exercise("m2", "squat", set("m3", 100.0)), client = marko)
        val editing = training("t4", "2026-11-15T10:00:00Z", exercise("e4", "squat", set("s4", 100.0)))
        val history = listOf(done, empty, planned, markos, editing)

        assertEquals("t1", TrainingHistory.repeatSource(history, "ana", "", excludingId = "t4", now = now)?.id)
        assertNull(TrainingHistory.repeatSource(history, "nobody", "", now = now))
    }
}
