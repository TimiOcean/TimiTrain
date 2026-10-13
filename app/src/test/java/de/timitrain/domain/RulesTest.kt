package de.timitrain.domain

import de.timitrain.data.backup.BackupAlternative
import de.timitrain.data.backup.BackupCodec
import de.timitrain.data.backup.BackupDocument
import de.timitrain.data.backup.BackupExercise
import de.timitrain.data.backup.BackupPlannedSet
import de.timitrain.data.backup.BackupRead
import de.timitrain.data.backup.BackupSession
import de.timitrain.data.backup.BackupSessionSet
import de.timitrain.data.backup.BackupSlot
import de.timitrain.data.backup.BackupWeekDay
import de.timitrain.data.backup.BackupWorkout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanAdjustmentTest {
    @Test
    fun weightMovesHalfwayUp() {
        val updated = PlanAdjustment.adjust(
            SetValues(reps = 8, weightKg = 50.0),
            SetValues(reps = 8, weightKg = 60.0),
            ExerciseType.STRENGTH,
        )
        assertEquals(55.0, updated.weightKg!!, 0.0)
        assertEquals(8, updated.reps)
    }

    @Test
    fun weightMovesHalfwayDown() {
        val updated = PlanAdjustment.adjust(
            SetValues(weightKg = 50.0),
            SetValues(weightKg = 44.0),
            ExerciseType.STRENGTH,
        )
        assertEquals(47.0, updated.weightKg!!, 0.0)
    }

    @Test
    fun sameArithmeticForRepsSecondsMinutesAndDistance() {
        assertEquals(
            11,
            PlanAdjustment.adjust(SetValues(reps = 10), SetValues(reps = 12), ExerciseType.BODYWEIGHT).reps,
        )
        assertEquals(
            35,
            PlanAdjustment.adjust(SetValues(durationSec = 30), SetValues(durationSec = 40), ExerciseType.TIMED).durationSec,
        )
        assertEquals(
            25,
            PlanAdjustment.adjust(SetValues(durationMin = 20), SetValues(durationMin = 30), ExerciseType.CARDIO).durationMin,
        )
        assertEquals(
            1.1,
            PlanAdjustment.adjust(SetValues(distanceKm = 1.0), SetValues(distanceKm = 1.2), ExerciseType.CARDIO).distanceKm!!,
            0.0,
        )
    }

    @Test
    fun onlyTheCompletedSetChanges() {
        val first = SetValues(weightKg = 50.0)
        val second = SetValues(weightKg = 40.0)
        val adjustedFirst = PlanAdjustment.adjust(first, SetValues(weightKg = 60.0), ExerciseType.STRENGTH)
        assertEquals(55.0, adjustedFirst.weightKg!!, 0.0)
        assertEquals(40.0, second.weightKg!!, 0.0)
    }

    @Test
    fun missingPlanMetricStaysMissing() {
        val updated = PlanAdjustment.adjust(
            SetValues(distanceKm = null, durationMin = 20),
            SetValues(distanceKm = 5.0, durationMin = 30),
            ExerciseType.CARDIO,
        )
        assertNull(updated.distanceKm)
        assertEquals(25, updated.durationMin)
    }
}

class RotationTest {
    @Test
    fun cyclesThroughAlternatives() {
        val names = listOf("A", "B", "C")
        var index = 0
        val seen = mutableListOf<String>()
        repeat(4) {
            seen += names[Rotation.normalize(index, names.size)]
            index = Rotation.afterSave(index, names.size, completedSetInSlot = true)
        }
        assertEquals(listOf("A", "B", "C", "A"), seen)
    }

    @Test
    fun slotWithoutCompletedSetDoesNotAdvance() {
        assertEquals(1, Rotation.afterSave(1, 3, completedSetInSlot = false))
    }

    @Test
    fun removingCurrentAlternativeKeepsIndexInRange() {
        assertEquals(1, Rotation.normalize(1, 2))
    }
}

class StepperTest {
    @Test
    fun neverGoesBelowZero() {
        assertEquals(0, SetStepper.nudgeInt(0, 1, -1))
        assertEquals(0.0, SetStepper.nudgeDecimal(0.5, 2.5, -1), 0.0)
        assertEquals(2.5, SetStepper.nudgeDecimal(0.0, 2.5, 1), 0.0)
    }

    @Test
    fun optionalMetricStaysUnsetOnMinus() {
        assertNull(SetStepper.nudgeOptionalDecimal(null, 0.1, -1))
        assertNull(SetStepper.nudgeOptionalInt(null, 1, -1))
        assertEquals(0.1, SetStepper.nudgeOptionalDecimal(null, 0.1, 1)!!, 0.0)
    }

    @Test
    fun eachStepUsesOnlyItsOwnIncrement() {
        assertEquals(9, SetStepper.nudgeInt(8, 1, 1))
        assertEquals(52.5, SetStepper.nudgeDecimal(50.0, 2.5, 1), 0.0)
    }
}

class CardioValidationTest {
    @Test
    fun atLeastOneMetric() {
        assertFalse(cardioHasMetric(null, null))
        assertTrue(cardioHasMetric(1.0, null))
        assertTrue(cardioHasMetric(null, 20))
        assertEquals(InputProblem.CARDIO, validatePlanned(ExerciseType.CARDIO, SetValues()))
        assertNull(validatePlanned(ExerciseType.CARDIO, SetValues(distanceKm = 1.0)))
        assertEquals(InputProblem.NEGATIVE, validatePlanned(ExerciseType.STRENGTH, SetValues(reps = -1, weightKg = 20.0)))
    }
}

class GermanNumberTest {
    @Test
    fun parsesCommaDecimals() {
        assertEquals(2.5, GermanNumbers.parse("2,5")!!, 0.0)
        assertEquals(0.1, GermanNumbers.parse("0,1")!!, 0.0)
        assertEquals("2,5", GermanNumbers.format(2.5))
    }
}

class StreakTest {
    private val start = LocalDate.of(2026, 1, 5)

    @Test
    fun missDropsStreakAndLaterTrainingCountsFromTheMiss() {
        val trainings = (0..4).map { SessionFact(start.plusDays(it.toLong())) }
        val miss = start.plusDays(5)
        val after = start.plusDays(6)
        val rest = start.plusDays(7)
        val plannedDays = (0..6).map { start.plusDays(it.toLong()) }.toSet()
        fun planned(date: LocalDate) = date in plannedDays

        assertEquals(0, StreakRules.streak(miss.plusDays(1), trainings, ::planned))

        val withAfter = trainings + SessionFact(after)
        assertEquals(1, StreakRules.streak(after.plusDays(1), withAfter, ::planned))

        val restored = withAfter + SessionFact(date = rest, makesUpForDate = miss)
        assertEquals(7, StreakRules.streak(rest.plusDays(1), restored, ::planned))

        assertEquals(1, StreakRules.streak(miss.plusDays(8), withAfter, ::planned))
    }

    @Test
    fun restDayDoesNotChangeStreak() {
        val trainings = (0..4).map { SessionFact(start.plusDays(it.toLong())) }
        val rest = start.plusDays(5)
        fun planned(date: LocalDate) = date < rest
        assertEquals(5, StreakRules.streak(rest, trainings, ::planned))
        assertEquals(5, StreakRules.streak(rest.plusDays(1), trainings, ::planned))
    }

    @Test
    fun makeupIsOfferedOnlyOnAnOpenRestDayInsideTheWindow() {
        val miss = start
        val plannedDays = setOf(miss)
        val sessions = emptyList<SessionFact>()
        assertTrue(
            StreakRules.openMakeups(
                today = miss.plusDays(1),
                todayIsPlanned = true,
                todayHasSession = false,
                plannedDays = plannedDays,
                sessions = sessions,
            ).isEmpty(),
        )
        assertEquals(
            listOf(miss),
            StreakRules.openMakeups(
                today = miss.plusDays(7),
                todayIsPlanned = false,
                todayHasSession = false,
                plannedDays = plannedDays,
                sessions = sessions,
            ),
        )
        assertTrue(
            StreakRules.openMakeups(
                today = miss.plusDays(8),
                todayIsPlanned = false,
                todayHasSession = false,
                plannedDays = plannedDays,
                sessions = sessions,
            ).isEmpty(),
        )
        assertTrue(
            StreakRules.openMakeups(
                today = miss.plusDays(2),
                todayIsPlanned = false,
                todayHasSession = true,
                plannedDays = plannedDays,
                sessions = sessions,
            ).isEmpty(),
        )
    }
}

class SessionRewindTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val plan = PlanTouch(
        alternativeId = 4,
        setPosition = 0,
        before = SetValues(reps = 10, weightKg = 50.0),
    )
    private val rotation = RotationTouch(slotId = 3, beforeIndex = 1)

    @Test
    fun restoresTheSessionStartPlanAndRotation() {
        val deleted = SessionTouch(
            sessionId = 1,
            date = monday,
            plans = listOf(plan),
            rotations = listOf(rotation),
        )
        val rewind = SessionRewind.restore(deleted, emptyList())
        assertEquals(listOf(plan), rewind.plans)
        assertEquals(listOf(rotation), rewind.rotations)
    }

    @Test
    fun leavesLaterPlanAndRotationAndStillRestoresUntouchedTargets() {
        val older = SessionTouch(
            sessionId = 1,
            date = monday,
            plans = listOf(plan, PlanTouch(9, 1, SetValues(reps = 8))),
            rotations = listOf(rotation, RotationTouch(8, 0)),
        )
        val later = SessionTouch(
            sessionId = 2,
            date = monday.plusDays(2),
            plans = listOf(PlanTouch(4, 0, SetValues(reps = 11, weightKg = 55.0))),
            rotations = listOf(RotationTouch(3, 2)),
        )
        val rewind = SessionRewind.restore(older, listOf(later))
        assertEquals(listOf(PlanTouch(9, 1, SetValues(reps = 8))), rewind.plans)
        assertEquals(listOf(RotationTouch(8, 0)), rewind.rotations)
    }
}

class MakeupTargetTest {
    @Test
    fun makeupUsesTheWorkoutCurrentlyAssignedToTheMissedWeekday() {
        val missedMonday = LocalDate.of(2026, 10, 5)
        assertEquals(java.time.DayOfWeek.MONDAY, missedMonday.dayOfWeek)
        val current = mapOf(1 to 20L, 3 to 30L)
        val today = missedMonday.plusDays(2)
        val open = StreakRules.openMakeups(
            today = today,
            todayIsPlanned = false,
            todayHasSession = false,
            plannedDays = setOf(missedMonday),
            sessions = emptyList(),
        )
        assertEquals(listOf(missedMonday), open)
        assertEquals(20L, MakeupTarget.workoutId(open.single(), current))
        assertNull(MakeupTarget.workoutId(missedMonday.plusDays(1), current))
    }
}

class RepsStepTest {
    @Test
    fun repsStepIsAlwaysOne() {
        assertEquals(1, RepsStep.STEP)
        assertEquals(11, RepsStep.nudge(10, 1))
        assertEquals(9, RepsStep.nudge(10, -1))
        assertEquals(0, RepsStep.nudge(0, -1))
        assertEquals(
            11,
            PlanAdjustment.adjust(SetValues(reps = 10), SetValues(reps = 12), ExerciseType.BODYWEIGHT).reps,
        )
    }
}

class DefaultSetsTest {
    @Test
    fun addingAnExerciseCopiesItsDefaultSets() {
        val defaults = listOf(
            SetValues(reps = 8, weightKg = 50.0),
            SetValues(reps = 6, weightKg = 55.0),
        )
        val copied = DefaultSets.copyForSlot(defaults)
        assertEquals(listOf(0, 1), copied.indices.toList())
        assertEquals(8, copied[0].reps)
        assertEquals(50.0, copied[0].weightKg!!, 0.0)
        assertEquals(6, copied[1].reps)
        assertEquals(55.0, copied[1].weightKg!!, 0.0)
        val editedOnTheWorkout = copied.map { it.copy(reps = 1, weightKg = 40.0) }
        assertEquals(8, defaults[0].reps)
        assertEquals(50.0, defaults[0].weightKg!!, 0.0)
        assertEquals(1, editedOnTheWorkout[0].reps)
        assertTrue(DefaultSets.copyForSlot(emptyList()).isEmpty())
    }
}

class BackupCodecTest {
    @Test
    fun roundTripKeepsTheFourDomainsAndRejectsAnotherVersion() {
        val document = sample()
        val json = BackupCodec.encode(document)
        assertFalse(json.contains("defaultRest"))
        val read = BackupCodec.decode(json)
        assertTrue(read is BackupRead.Ok)
        assertEquals(document, (read as BackupRead.Ok).document)
        assertFalse(BackupCodec.isValid(document.copy(schemaVersion = 2)))
    }

    @Test
    fun rejectsCardioSetWithoutAMetric() {
        val document = sample()
        val broken = document.copy(
            workouts = listOf(
                document.workouts.first().copy(
                    slots = listOf(
                        document.workouts.first().slots.first().copy(
                            alternatives = listOf(
                                document.workouts.first().slots.first().alternatives.first().copy(
                                    sets = listOf(BackupPlannedSet(id = 5, position = 0)),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertFalse(BackupCodec.isValid(broken))
    }

    private fun sample(): BackupDocument {
        return BackupDocument(
            schemaVersion = 1,
            exercises = listOf(
                BackupExercise(
                    id = 1,
                    name = "Laufband",
                    type = ExerciseType.CARDIO.name,
                    distanceIncrementKm = 0.1,
                    durationIncrementMin = 1,
                ),
            ),
            workouts = listOf(
                BackupWorkout(
                    id = 2,
                    name = "Cardio",
                    slots = listOf(
                        BackupSlot(
                            id = 3,
                            position = 0,
                            rotationIndex = 0,
                            alternatives = listOf(
                                BackupAlternative(
                                    id = 4,
                                    exerciseId = 1,
                                    position = 0,
                                    sets = listOf(BackupPlannedSet(id = 5, position = 0, distanceKm = 1.0)),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            weekPlan = (1..7).map { BackupWeekDay(weekday = it, workoutId = if (it == 1) 2 else null) },
            sessions = listOf(
                BackupSession(
                    id = 6,
                    workoutId = 2,
                    workoutName = "Cardio",
                    date = "2026-01-05",
                    startedAt = 1L,
                    finishedAt = 2L,
                    sets = listOf(
                        BackupSessionSet(
                            id = 7,
                            exerciseId = 1,
                            exerciseName = "Laufband",
                            slotId = 3,
                            alternativeId = 4,
                            position = 0,
                            distanceKm = 1.2,
                        ),
                    ),
                ),
            ),
        )
    }
}
