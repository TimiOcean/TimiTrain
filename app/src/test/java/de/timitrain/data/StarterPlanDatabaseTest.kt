package de.timitrain.data

import android.app.Application
import de.timitrain.data.db.TimiTrainDatabase
import de.timitrain.domain.ExerciseCategory
import de.timitrain.domain.ExerciseType
import de.timitrain.domain.toExerciseCategory
import de.timitrain.domain.Increments
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class StarterPlanDatabaseTest {
    @Test
    fun freshDatabaseContainsTheFiveWorkoutsAndFridayCardio() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val occupied = TimiTrainDatabase.createInMemory(context)
        val occupiedRepo = TimiTrainRepository(occupied)
        occupiedRepo.createExercise("Eigene", ExerciseType.BODYWEIGHT, Increments(), emptyList())
        occupiedRepo.seedStarterPlanIfEmpty()
        assertEquals(listOf("Eigene"), occupied.exercises().listAll().map { it.name })
        assertTrue(occupied.workouts().listAll().isEmpty())
        assertTrue(occupied.weekPlan().list().all { it.workoutId == null })
        occupied.close()

        val db = TimiTrainDatabase.createInMemory(context)
        val repository = TimiTrainRepository(db)
        repository.seedStarterPlanIfEmpty()

        val workouts = db.workouts().listAll().associateBy { it.name }
        assertEquals(
            setOf(
                StarterPlan.PUSH,
                StarterPlan.PULL,
                StarterPlan.LEGS,
                StarterPlan.UPPER,
                StarterPlan.LOWER,
                StarterPlan.MOBILITY_C,
            ),
            workouts.keys,
        )
        val week = db.weekPlan().list().associateBy { it.weekday }
        assertEquals(StarterPlan.PUSH, week.getValue(1).workoutName)
        assertEquals(StarterPlan.PULL, week.getValue(2).workoutName)
        assertEquals(StarterPlan.LEGS, week.getValue(3).workoutName)
        assertEquals(StarterPlan.UPPER, week.getValue(4).workoutName)
        assertEquals(StarterPlan.LOWER, week.getValue(5).workoutName)
        assertNull(week.getValue(6).workoutId)
        assertNull(week.getValue(7).workoutId)
        assertTrue(week.values.none { it.workoutId == workouts.getValue(StarterPlan.MOBILITY_C).id })

        val friday = repository.loadWorkout(workouts.getValue(StarterPlan.LOWER).id)!!
        assertEquals(
            listOf(
                StarterPlan.CARDIO,
                "Hip Thrust",
                "Beinbeuger",
                "Beinstrecker",
                "Bird Dog pro Seite",
                "Seitstütz",
                "Deep Squat Hold",
                "90/90-Hip-Switch",
                "World's Greatest Stretch",
                "Glute Bridge",
                "Leg Swings",
            ),
            friday.slots.map { it.alternatives.first().exercise.name },
        )
        val fridayCardio = friday.slots.first().alternatives.single()
        assertEquals(StarterPlan.CARDIO, fridayCardio.exercise.name)
        assertEquals(StarterPlan.FRIDAY_CARDIO_MINUTES, fridayCardio.sets.single().durationMin)
        assertNull(fridayCardio.sets.single().distanceKm)
        assertEquals(
            StarterPlan.DEFAULT_CARDIO_MINUTES,
            repository.defaultSets(fridayCardio.exercise.id).single().durationMin,
        )

        val birdDog = friday.slots.flatMap { it.alternatives }.single { it.exercise.name == "Bird Dog pro Seite" }
        assertEquals(listOf(8, 8, 8), birdDog.sets.map { it.reps })
        assertTrue(birdDog.sets.all { it.weightKg == null })
        val mobility = repository.loadWorkout(workouts.getValue(StarterPlan.MOBILITY_C).id)!!
        val birdDogMobility = mobility.slots.flatMap { it.alternatives }
            .single { it.exercise.name == "Bird Dog (Mobility)" }
        assertEquals(120, birdDogMobility.sets.single().durationSec)

        val byName = db.exercises().listAll().associateBy { it.name }
        assertEquals(ExerciseCategory.CARDIO, byName.getValue(StarterPlan.CARDIO).category.toExerciseCategory())
        assertEquals(ExerciseCategory.MOBILITY, byName.getValue("Bird Dog (Mobility)").category.toExerciseCategory())
        assertEquals(ExerciseCategory.MOBILITY, byName.getValue("Cat-Cow").category.toExerciseCategory())
        assertEquals(ExerciseCategory.STRENGTH, byName.getValue("Seitstütz").category.toExerciseCategory())
        assertEquals(ExerciseCategory.STRENGTH, byName.getValue("Bird Dog pro Seite").category.toExerciseCategory())
        assertEquals(ExerciseCategory.STRENGTH, byName.getValue("Kurzhantel-Schrägbankdrücken").category.toExerciseCategory())
        assertTrue(byName.values.all { it.category.toExerciseCategory() in ExerciseCategory.entries })

        val extraId = repository.createExercise(
            "Zusatz-Kraft",
            ExerciseType.STRENGTH,
            Increments(),
            emptyList(),
            ExerciseCategory.STRENGTH,
        )
        repository.addSlot(friday.workout.id, extraId)
        val withExtra = repository.loadWorkout(friday.workout.id)!!
        val extraNames = withExtra.slots.map { it.alternatives.first().exercise.name }
        assertEquals(extraNames.indexOf("Seitstütz") + 1, extraNames.indexOf("Zusatz-Kraft"))
        assertEquals(extraNames.indexOf("Zusatz-Kraft") + 1, extraNames.indexOf("Deep Squat Hold"))
        val hip = withExtra.slots.first { it.alternatives.first().exercise.name == "Hip Thrust" }
        val bein = withExtra.slots.first { it.alternatives.first().exercise.name == "Beinbeuger" }
        repository.reorderSlotPositions(
            mapOf(hip.slot.id to bein.slot.position, bein.slot.id to hip.slot.position),
        )
        val reordered = repository.loadWorkout(friday.workout.id)!!.slots.map { it.alternatives.first().exercise.name }
        assertEquals(StarterPlan.CARDIO, reordered.first())
        assertEquals("Beinbeuger", reordered[1])
        assertEquals("Hip Thrust", reordered[2])
        assertEquals("Deep Squat Hold", reordered[reordered.indexOf("Zusatz-Kraft") + 1])

        val exercises = db.exercises().listAll().size
        val workoutCount = db.workouts().listAll().size
        repository.seedStarterPlanIfEmpty()
        assertEquals(exercises, db.exercises().listAll().size)
        assertEquals(workoutCount, db.workouts().listAll().size)
        db.close()
    }
}
