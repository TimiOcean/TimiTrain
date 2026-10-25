package de.timitrain.data

import android.app.Application
import de.timitrain.data.db.TimiTrainDatabase
import de.timitrain.domain.ExerciseType
import de.timitrain.domain.Increments
import de.timitrain.domain.SetValues
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PlanWeightDatabaseTest {
    @Test
    fun exactWeightReplacementIsStoredOnDefaultsAndEveryWorkout() = runBlocking {
        val fixture = Fixture()
        fixture.log(fixture.pushId, LocalDate.of(2026, 10, 5), SetValues(reps = 8, weightKg = 51.3))
        fixture.assertWeights(51.3)
        fixture.assertReps(8)
        fixture.close()
    }

    @Test
    fun repsOnlyEditDoesNotRunTheOldFormula() = runBlocking {
        val fixture = Fixture()
        fixture.log(fixture.pushId, LocalDate.of(2026, 10, 5), SetValues(reps = 12, weightKg = 50.0))
        fixture.assertWeights(50.0)
        fixture.assertReps(8)
        fixture.close()
    }

    @Test
    fun asPlannedLeavesTheWeightUnchanged() = runBlocking {
        val fixture = Fixture()
        fixture.setPullWeight(40.0)
        fixture.log(fixture.pushId, LocalDate.of(2026, 10, 5), SetValues(reps = 8, weightKg = 50.0))
        assertEquals(50.0, fixture.weight(fixture.pushId), 0.0)
        assertEquals(40.0, fixture.weight(fixture.pullId), 0.0)
        assertEquals(50.0, fixture.repository.defaultSets(fixture.exerciseId).single().weightKg!!, 0.0)
        fixture.assertReps(8)
        fixture.close()
    }

    @Test
    fun deletingTheSessionRestoresTheSnapshotWeight() = runBlocking {
        val fixture = Fixture()
        fixture.setPullWeight(40.0)
        val date = LocalDate.of(2026, 10, 5)
        fixture.log(fixture.pushId, date, SetValues(reps = 8, weightKg = 51.3))
        fixture.assertWeights(51.3)
        val session = fixture.repository.sessionOn(date)!!
        fixture.repository.deleteSession(session.id)
        assertEquals(50.0, fixture.weight(fixture.pushId), 0.0)
        assertEquals(40.0, fixture.weight(fixture.pullId), 0.0)
        assertEquals(50.0, fixture.repository.defaultSets(fixture.exerciseId).single().weightKg!!, 0.0)
        assertNull(fixture.repository.sessionOn(date))
        fixture.close()
    }

    private class Fixture {
        val context = RuntimeEnvironment.getApplication()
        val db = TimiTrainDatabase.createInMemory(context)
        val repository = TimiTrainRepository(db)
        val exerciseId: Long
        val pushId: Long
        val pullId: Long

        init {
            exerciseId = runBlocking {
                repository.createExercise(
                    "Kniebeuge",
                    ExerciseType.STRENGTH,
                    Increments(weightKg = 2.5),
                    listOf(SetValues(reps = 8, weightKg = 50.0)),
                )
            }
            pushId = runBlocking { repository.createWorkout("Push") }
            pullId = runBlocking { repository.createWorkout("Pull") }
            runBlocking {
                repository.addSlot(pushId, exerciseId)
                repository.addSlot(pullId, exerciseId)
            }
        }

        suspend fun setPullWeight(weightKg: Double) {
            val set = planned(pullId)
            repository.updatePlannedSet(set.id, set.toValues().copy(weightKg = weightKg))
        }

        suspend fun log(workoutId: Long, date: LocalDate, actual: SetValues) {
            val graph = repository.loadWorkout(workoutId)!!
            val slot = graph.slots.single()
            val alternative = slot.alternatives.single()
            val set = alternative.sets.single()
            repository.saveSession(
                SessionDraft(
                    workoutId = workoutId,
                    workoutName = graph.workout.name,
                    date = date,
                    startedAt = 1,
                    finishedAt = 2,
                    completed = listOf(
                        CompletedSetDraft(
                            slotId = slot.slot.id,
                            alternativeId = alternative.alternative.id,
                            exerciseId = exerciseId,
                            exerciseName = alternative.exercise.name,
                            position = set.position,
                            type = ExerciseType.STRENGTH,
                            plan = set.toValues(),
                            actual = actual,
                            rotationIndex = slot.slot.rotationIndex,
                        ),
                    ),
                ),
            )
        }

        suspend fun weight(workoutId: Long): Double = planned(workoutId).weightKg!!

        suspend fun assertWeights(expected: Double) {
            assertEquals(expected, weight(pushId), 0.0)
            assertEquals(expected, weight(pullId), 0.0)
            assertEquals(expected, repository.defaultSets(exerciseId).single().weightKg!!, 0.0)
        }

        suspend fun assertReps(expected: Int) {
            assertEquals(expected, planned(pushId).reps)
            assertEquals(expected, planned(pullId).reps)
            assertEquals(expected, repository.defaultSets(exerciseId).single().reps)
        }

        private suspend fun planned(workoutId: Long) =
            repository.loadWorkout(workoutId)!!.slots.single().alternatives.single().sets.single()

        fun close() = db.close()
    }
}
