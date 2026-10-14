package de.timitrain.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSectionsTest {
    @Test
    fun sectionsKeepPreviousOrder() {
        val slots = listOf(
            CategorizedSlot(1, 0, ExerciseCategory.MOBILITY),
            CategorizedSlot(2, 1, ExerciseCategory.STRENGTH),
            CategorizedSlot(3, 2, ExerciseCategory.CARDIO),
            CategorizedSlot(4, 3, ExerciseCategory.STRENGTH),
        )
        val grouped = WorkoutSections.grouped(slots)
        assertEquals(listOf(3L), grouped[0].map { it.id })
        assertEquals(listOf(2L, 4L), grouped[1].map { it.id })
        assertEquals(listOf(1L), grouped[2].map { it.id })
    }

    @Test
    fun canonicalOrderIsTheTrainingOrder() {
        val slots = listOf(
            CategorizedSlot(1, 0, ExerciseCategory.MOBILITY),
            CategorizedSlot(2, 1, ExerciseCategory.STRENGTH),
            CategorizedSlot(3, 2, ExerciseCategory.CARDIO),
            CategorizedSlot(4, 3, ExerciseCategory.STRENGTH),
        )
        assertEquals(
            mapOf(3L to 0, 2L to 1, 4L to 2, 1L to 3),
            WorkoutSections.canonicalPositions(slots),
        )
    }

    @Test
    fun reorderStaysInsideTheSection() {
        val slots = listOf(
            CategorizedSlot(1, 0, ExerciseCategory.MOBILITY),
            CategorizedSlot(2, 1, ExerciseCategory.STRENGTH),
            CategorizedSlot(3, 2, ExerciseCategory.CARDIO),
            CategorizedSlot(4, 3, ExerciseCategory.STRENGTH),
        )
        val updates = WorkoutSections.positionsAfterReorder(
            slots,
            ExerciseCategory.STRENGTH,
            listOf(4, 2),
        )
        assertEquals(mapOf(4L to 1, 2L to 3), updates)
        assertNull(updates[1L])
        assertNull(updates[3L])
    }

    @Test
    fun countdownUsesPlannedDurationOnly() {
        assertEquals(90, countdownSeconds(ExerciseType.TIMED, SetValues(durationSec = 90)))
        assertEquals(1200, countdownSeconds(ExerciseType.CARDIO, SetValues(durationMin = 20, distanceKm = 1.0)))
        assertNull(countdownSeconds(ExerciseType.CARDIO, SetValues(distanceKm = 1.0)))
        assertNull(countdownSeconds(ExerciseType.STRENGTH, SetValues(reps = 8, weightKg = 12.5)))
        assertNull(countdownSeconds(ExerciseType.TIMED, SetValues(durationSec = 0)))
    }
}
