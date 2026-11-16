package software.rdd.apexperformance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import software.rdd.apexperformance.model.Training
import software.rdd.apexperformance.ui.trainings.ExerciseDraft
import software.rdd.apexperformance.ui.trainings.SetDraft
import software.rdd.apexperformance.ui.trainings.removingExercise
import software.rdd.apexperformance.ui.trainings.repeatedExercises
import software.rdd.apexperformance.ui.trainings.restoringExercise
import java.time.Instant

class TrainingFormDraftsTest {

    // MARK: - Undo

    @Test
    fun undoPutsASetBackAtItsPlace() {
        val sets = listOf(SetDraft(id = "a", reps = "10"), SetDraft(id = "b", reps = "8"), SetDraft(id = "c", reps = "6"))
        val exercise = ExerciseDraft(id = "e", workoutId = "w", sets = sets)

        val (removed, undo) = exercise.removingSetForUndo("b")!!
        assertEquals(listOf("a", "c"), removed.sets.map { it.id })

        val restored = removed.restoringSet(undo)
        assertEquals(exercise, restored)
        // A second undo changes nothing.
        assertEquals(restored, restored.restoringSet(undo))
    }

    @Test
    fun undoOfTheFirstSetMakesItsDropSetADropSetAgain() {
        val sets = listOf(SetDraft(id = "a", weight = "50"), SetDraft(id = "drop", weight = "40", isDropSet = true), SetDraft(id = "b", weight = "50"))
        val exercise = ExerciseDraft(id = "e", workoutId = "w", sets = sets)

        val (removed, undo) = exercise.removingSetForUndo("a")!!
        // The first set can't be a drop set.
        assertFalse(removed.sets.first().isDropSet)

        assertEquals(exercise, removed.restoringSet(undo))
        assertNull(exercise.removingSetForUndo("missing"))
    }

    @Test
    fun undoPutsAnExerciseBackWithItsSuperset() {
        val exercises = listOf(
            ExerciseDraft(id = "1", workoutId = "a"),
            ExerciseDraft(id = "2", workoutId = "b"),
            ExerciseDraft(id = "3", workoutId = "c", isSupersetWithPrevious = true)
        )

        // Removing the first exercise of the superset: the next one starts it.
        val (list, removed) = removingExercise(exercises, 1)!!
        assertEquals(listOf("1", "3"), list.map { it.id })
        assertFalse(list[1].isSupersetWithPrevious)

        assertEquals(exercises, restoringExercise(list, removed))
        assertNull(removingExercise(exercises, 5))
    }

    @Test
    fun undoOfTheFirstExerciseKeepsTheFirstOneOutOfASuperset() {
        val exercises = listOf(
            ExerciseDraft(id = "1", workoutId = "a"),
            ExerciseDraft(id = "2", workoutId = "b", isSupersetWithPrevious = true)
        )

        val (list, removed) = removingExercise(exercises, 0)!!
        assertFalse(list.single().isSupersetWithPrevious)
        assertEquals(exercises, restoringExercise(list, removed))
    }

    // MARK: - Repeat last training

    @Test
    fun repeatCopiesExercisesInOrderWithSetsDropSetsAndNotes() {
        val training = Training(
            id = "t",
            name = "Legs",
            date = Instant.parse("2026-11-12T10:00:00Z"),
            isCompleted = true,
            client = Training.Person("ana"),
            startedAt = Instant.parse("2026-11-12T10:00:00Z"),
            endedAt = Instant.parse("2026-11-12T11:00:00Z"),
            exercises = listOf(
                Training.Exercise(
                    id = "e2", workoutId = "row", order = 1, paceType = "Per500m",
                    sets = listOf(Training.ExerciseSet(id = "r1", durationSeconds = 120, distanceMeters = 500))
                ),
                Training.Exercise(
                    id = "e1", workoutId = "squat", order = 0, note = "Slowly down",
                    sets = listOf(
                        Training.ExerciseSet(id = "s2", order = 1, reps = "8", weight = 80.0, isDropSet = true),
                        Training.ExerciseSet(id = "s1", order = 0, reps = "6", weight = 100.0)
                    )
                ),
                Training.Exercise(id = "e3", workoutId = "plank", order = 2, isSupersetWithPrevious = true, sets = listOf(Training.ExerciseSet(id = "p1", durationSeconds = 60)))
            )
        )

        val drafts = repeatedExercises(training)
        val requests = drafts.map { it.request!! }

        assertEquals(listOf("squat", "row", "plank"), requests.map { it.workout })
        assertEquals("Slowly down", requests[0].note)
        assertEquals(listOf(100.0, 80.0), requests[0].sets.map { it.weight })
        assertEquals(listOf("6", "8"), requests[0].sets.map { it.reps })
        assertEquals(listOf(false, true), requests[0].sets.map { it.isDropSet })
        assertEquals(500, requests[1].sets.single().distanceMeters)
        assertEquals(120, requests[1].sets.single().durationSeconds)
        assertTrue(requests[2].isSupersetWithPrevious)
        assertEquals(60, requests[2].sets.single().durationSeconds)
        // New drafts, not the saved training's ids.
        assertTrue(drafts.none { it.id in listOf("e1", "e2", "e3") })
    }
}
