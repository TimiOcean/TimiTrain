package de.timitrain.data.backup

import de.timitrain.domain.ExerciseType
import de.timitrain.domain.cardioHasMetric
import de.timitrain.domain.toExerciseType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * JSON interchange for the four domains named in the spec: exercises, workouts,
 * the week plan, and sessions. The default rest duration is not part of this
 * file; whether import should restore it is unresolved.
 */
@Serializable
data class BackupDocument(
    val schemaVersion: Int,
    val exercises: List<BackupExercise> = emptyList(),
    val workouts: List<BackupWorkout> = emptyList(),
    val weekPlan: List<BackupWeekDay> = emptyList(),
    val sessions: List<BackupSession> = emptyList(),
)

@Serializable
data class BackupExercise(
    val id: Long,
    val name: String,
    val type: String,
    val repsIncrement: Int? = null,
    val weightIncrementKg: Double? = null,
    val durationIncrementSec: Int? = null,
    val distanceIncrementKm: Double? = null,
    val durationIncrementMin: Int? = null,
)

@Serializable
data class BackupWorkout(
    val id: Long,
    val name: String,
    val slots: List<BackupSlot> = emptyList(),
)

@Serializable
data class BackupSlot(
    val id: Long,
    val position: Int,
    val rotationIndex: Int = 0,
    val alternatives: List<BackupAlternative> = emptyList(),
)

@Serializable
data class BackupAlternative(
    val id: Long,
    val exerciseId: Long,
    val position: Int,
    val restSecondsOverride: Int? = null,
    val sets: List<BackupPlannedSet> = emptyList(),
)

@Serializable
data class BackupPlannedSet(
    val id: Long,
    val position: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSec: Int? = null,
    val durationMin: Int? = null,
    val distanceKm: Double? = null,
)

@Serializable
data class BackupWeekDay(
    val weekday: Int,
    val workoutId: Long? = null,
)

@Serializable
data class BackupSession(
    val id: Long,
    val workoutId: Long? = null,
    val workoutName: String,
    val date: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val makesUpForDate: String? = null,
    val sets: List<BackupSessionSet> = emptyList(),
    val planSnapshots: List<BackupPlanSnapshot> = emptyList(),
    val rotationSnapshots: List<BackupRotationSnapshot> = emptyList(),
)

@Serializable
data class BackupPlanSnapshot(
    val id: Long,
    val alternativeId: Long,
    val position: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSec: Int? = null,
    val durationMin: Int? = null,
    val distanceKm: Double? = null,
)

@Serializable
data class BackupRotationSnapshot(
    val id: Long,
    val slotId: Long,
    val rotationIndex: Int,
)

@Serializable
data class BackupSessionSet(
    val id: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val slotId: Long,
    val alternativeId: Long,
    val position: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSec: Int? = null,
    val durationMin: Int? = null,
    val distanceKm: Double? = null,
)

sealed interface BackupRead {
    data class Ok(val document: BackupDocument) : BackupRead
    data object Rejected : BackupRead
}

object BackupCodec {
    const val SCHEMA_VERSION = 1

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = true
    }

    fun encode(document: BackupDocument): String = json.encodeToString(BackupDocument.serializer(), document)

    fun decode(text: String): BackupRead {
        val document = try {
            json.decodeFromString(BackupDocument.serializer(), text)
        } catch (_: Exception) {
            return BackupRead.Rejected
        }
        if (!isValid(document)) return BackupRead.Rejected
        return BackupRead.Ok(document)
    }

    fun isValid(document: BackupDocument): Boolean {
        if (document.schemaVersion != SCHEMA_VERSION) return false
        if (document.exercises.any { it.id <= 0 || it.name.isBlank() || !validExercise(it) }) return false
        if (document.exercises.map { it.id }.distinct().size != document.exercises.size) return false
        val exerciseIds = document.exercises.map { it.id }.toSet()
        if (document.workouts.any { it.id <= 0 || it.name.isBlank() }) return false
        if (document.workouts.map { it.id }.distinct().size != document.workouts.size) return false
        val workoutIds = document.workouts.map { it.id }.toSet()
        val slotIds = mutableSetOf<Long>()
        val alternativeIds = mutableSetOf<Long>()
        for (workout in document.workouts) {
            if (workout.slots.map { it.id }.distinct().size != workout.slots.size) return false
            for (slot in workout.slots) {
                if (slot.id <= 0 || !slotIds.add(slot.id)) return false
                if (slot.alternatives.isEmpty()) return false
                for (alternative in slot.alternatives) {
                    if (alternative.id <= 0 || !alternativeIds.add(alternative.id)) return false
                    if (alternative.exerciseId !in exerciseIds) return false
                    if (alternative.restSecondsOverride != null && alternative.restSecondsOverride < 0) return false
                    val exercise = document.exercises.first { it.id == alternative.exerciseId }
                    val type = exercise.type.toExerciseType()
                    for (set in alternative.sets) {
                        if (set.id <= 0) return false
                        if (!validPlanned(type, set)) return false
                    }
                    if (alternative.sets.map { it.id }.distinct().size != alternative.sets.size) return false
                }
            }
        }
        val weekdays = document.weekPlan.map { it.weekday }
        if (weekdays.sorted() != (1..7).toList()) return false
        if (document.weekPlan.any { it.workoutId != null && it.workoutId !in workoutIds }) return false
        if (document.sessions.map { it.id }.distinct().size != document.sessions.size) return false
        if (document.sessions.map { it.date }.distinct().size != document.sessions.size) return false
        val planSnapshotIds = mutableSetOf<Long>()
        val rotationSnapshotIds = mutableSetOf<Long>()
        for (session in document.sessions) {
            if (session.id <= 0 || session.workoutName.isBlank()) return false
            if (!isoDate(session.date)) return false
            if (session.makesUpForDate != null && !isoDate(session.makesUpForDate)) return false
            if (session.sets.isEmpty()) return false
            for (set in session.sets) {
                if (set.id <= 0 || set.exerciseName.isBlank()) return false
                if (set.exerciseId !in exerciseIds) return false
                if (!nonNegativeSet(set.reps, set.weightKg, set.durationSec, set.durationMin, set.distanceKm)) {
                    return false
                }
            }
            for (snapshot in session.planSnapshots) {
                if (snapshot.id <= 0 || snapshot.alternativeId <= 0 || snapshot.position < 0) return false
                if (!planSnapshotIds.add(snapshot.id)) return false
                if (!nonNegativeSet(
                        snapshot.reps,
                        snapshot.weightKg,
                        snapshot.durationSec,
                        snapshot.durationMin,
                        snapshot.distanceKm,
                    )
                ) {
                    return false
                }
            }
            for (snapshot in session.rotationSnapshots) {
                if (snapshot.id <= 0 || snapshot.slotId <= 0 || snapshot.rotationIndex < 0) return false
                if (!rotationSnapshotIds.add(snapshot.id)) return false
            }
        }
        return true
    }

    private fun validExercise(exercise: BackupExercise): Boolean {
        val type = try {
            exercise.type.toExerciseType()
        } catch (_: IllegalArgumentException) {
            return false
        }
        return when (type) {
            ExerciseType.STRENGTH ->
                positive(exercise.repsIncrement) && positive(exercise.weightIncrementKg)
            ExerciseType.BODYWEIGHT -> positive(exercise.repsIncrement)
            ExerciseType.TIMED -> positive(exercise.durationIncrementSec)
            ExerciseType.CARDIO ->
                positive(exercise.distanceIncrementKm) && positive(exercise.durationIncrementMin)
        }
    }

    private fun validPlanned(type: ExerciseType, set: BackupPlannedSet): Boolean {
        if (!nonNegativeSet(set.reps, set.weightKg, set.durationSec, set.durationMin, set.distanceKm)) return false
        return when (type) {
            ExerciseType.STRENGTH -> set.reps != null && set.weightKg != null
            ExerciseType.BODYWEIGHT -> set.reps != null
            ExerciseType.TIMED -> set.durationSec != null
            ExerciseType.CARDIO -> cardioHasMetric(set.distanceKm, set.durationMin)
        }
    }

    private fun nonNegativeSet(
        reps: Int?,
        weightKg: Double?,
        durationSec: Int?,
        durationMin: Int?,
        distanceKm: Double?,
    ): Boolean {
        if (reps != null && reps < 0) return false
        if (weightKg != null && weightKg < 0.0) return false
        if (durationSec != null && durationSec < 0) return false
        if (durationMin != null && durationMin < 0) return false
        if (distanceKm != null && distanceKm < 0.0) return false
        return true
    }

    private fun positive(value: Int?): Boolean = value != null && value > 0

    private fun positive(value: Double?): Boolean = value != null && value > 0.0

    private fun isoDate(value: String): Boolean {
        if (!DATE.matches(value)) return false
        return try {
            java.time.LocalDate.parse(value)
            true
        } catch (_: Exception) {
            false
        }
    }

    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
}
