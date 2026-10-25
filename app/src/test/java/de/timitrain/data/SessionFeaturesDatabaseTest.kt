package de.timitrain.data

import android.app.Application
import de.timitrain.data.db.TimiTrainDatabase
import de.timitrain.domain.ExerciseType
import de.timitrain.domain.Increments
import de.timitrain.domain.SetValues
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class SessionFeaturesDatabaseTest {
    @Test
    fun notePhotoAndPerSideStayOnTheExercise() = runBlocking {
        val db = TimiTrainDatabase.createInMemory(RuntimeEnvironment.getApplication())
        val repository = TimiTrainRepository(db)
        val id = repository.createExercise(
            "Seitstütz",
            ExerciseType.TIMED,
            Increments(durationSec = 5),
            listOf(SetValues(durationSec = 30)),
            note = "Ellbogen unter der Schulter",
            photoFile = "seitstuetz.jpg",
            perSide = true,
        )
        val stored = repository.exercise(id)!!
        assertEquals("Ellbogen unter der Schulter", stored.note)
        assertEquals("seitstuetz.jpg", stored.photoFile)
        assertEquals(true, stored.perSide)

        val strength = repository.createExercise(
            "Kniebeuge",
            ExerciseType.STRENGTH,
            Increments(weightKg = 2.5),
            listOf(SetValues(reps = 8, weightKg = 50.0)),
            perSide = true,
        )
        assertFalse(repository.exercise(strength)!!.perSide)

        repository.updateExercise(
            id,
            Increments(durationSec = 5),
            listOf(SetValues(durationSec = 30)),
            de.timitrain.domain.ExerciseCategory.STRENGTH,
            note = "  ",
            photoFile = null,
            perSide = false,
        )
        val cleared = repository.exercise(id)!!
        assertNull(cleared.note)
        assertNull(cleared.photoFile)
        assertFalse(cleared.perSide)
        db.close()
    }

    @Test
    fun trainingEditRewritesOnlyTheCurrentAlternative() = runBlocking {
        val db = TimiTrainDatabase.createInMemory(RuntimeEnvironment.getApplication())
        val repository = TimiTrainRepository(db)
        val exerciseId = repository.createExercise(
            "Plank",
            ExerciseType.TIMED,
            Increments(durationSec = 5),
            listOf(SetValues(durationSec = 20)),
        )
        val workoutId = repository.createWorkout("Core")
        val otherId = repository.createWorkout("Andere")
        repository.addSlot(workoutId, exerciseId)
        repository.addSlot(otherId, exerciseId)
        val alternativeId = repository.loadWorkout(workoutId)!!.slots.single().alternatives.single().alternative.id
        repository.replaceAlternativeSets(alternativeId, listOf(SetValues(durationSec = 45), SetValues(durationSec = 50)))
        assertEquals(listOf(45, 50), repository.plannedValues(alternativeId).map { it.durationSec })
        assertEquals(listOf(20), repository.defaultSets(exerciseId).map { it.durationSec })
        val otherAlternative = repository.loadWorkout(otherId)!!.slots.single().alternatives.single().alternative.id
        assertEquals(listOf(20), repository.plannedValues(otherAlternative).map { it.durationSec })
        db.close()
    }

    @Test
    fun saveAdvancesFromTheAlternativeThatWasPerformed() = runBlocking {
        val db = TimiTrainDatabase.createInMemory(RuntimeEnvironment.getApplication())
        val repository = TimiTrainRepository(db)
        val ids = listOf("Eins", "Zwei", "Drei").map { name ->
            repository.createExercise(name, ExerciseType.BODYWEIGHT, Increments(), listOf(SetValues(reps = 8)))
        }
        val workoutId = repository.createWorkout("Rotation")
        repository.addSlot(workoutId, ids[0])
        val slot = repository.loadWorkout(workoutId)!!.slots.single()
        repository.addAlternative(slot.slot.id, ids[1])
        repository.addAlternative(slot.slot.id, ids[2])
        val graph = repository.loadWorkout(workoutId)!!
        val alternatives = graph.slots.single().alternatives
        assertEquals(0, graph.slots.single().slot.rotationIndex)
        val performed = alternatives[1]
        val date = LocalDate.of(2026, 10, 25)
        repository.saveSession(
            SessionDraft(
                workoutId = workoutId,
                workoutName = graph.workout.name,
                date = date,
                startedAt = 1,
                finishedAt = 2,
                completed = listOf(
                    CompletedSetDraft(
                        slotId = graph.slots.single().slot.id,
                        alternativeId = performed.alternative.id,
                        exerciseId = performed.exercise.id,
                        exerciseName = performed.exercise.name,
                        position = performed.sets.single().position,
                        type = ExerciseType.BODYWEIGHT,
                        plan = performed.sets.single().toValues(),
                        actual = SetValues(reps = 8),
                        rotationIndex = 0,
                        alternativeIndex = 1,
                    ),
                ),
            ),
        )
        assertEquals(2, repository.loadWorkout(workoutId)!!.slots.single().slot.rotationIndex)
        repository.deleteSession(repository.sessionOn(date)!!.id)
        assertEquals(0, repository.loadWorkout(workoutId)!!.slots.single().slot.rotationIndex)
        db.close()
    }
}
