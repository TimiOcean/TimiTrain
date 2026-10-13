package de.timitrain.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val repsIncrement: Int? = null,
    val weightIncrementKg: Double? = null,
    val durationIncrementSec: Int? = null,
    val distanceIncrementKm: Double? = null,
    val durationIncrementMin: Int? = null,
)

@Entity(
    tableName = "exercise_default_sets",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId")],
)
data class ExerciseDefaultSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val position: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSec: Int? = null,
    val durationMin: Int? = null,
    val distanceKm: Double? = null,
)

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "workout_slots",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId")],
)
data class WorkoutSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val position: Int,
    val rotationIndex: Int = 0,
)

@Entity(
    tableName = "slot_alternatives",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["slotId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("slotId"), Index("exerciseId")],
)
data class SlotAlternativeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slotId: Long,
    val exerciseId: Long,
    val position: Int,
    val restSecondsOverride: Int? = null,
)

@Entity(
    tableName = "planned_sets",
    foreignKeys = [
        ForeignKey(
            entity = SlotAlternativeEntity::class,
            parentColumns = ["id"],
            childColumns = ["alternativeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("alternativeId")],
)
data class PlannedSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alternativeId: Long,
    val position: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSec: Int? = null,
    val durationMin: Int? = null,
    val distanceKm: Double? = null,
)

@Entity(
    tableName = "week_plan",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("workoutId")],
)
data class WeekPlanEntity(
    @PrimaryKey val weekday: Int,
    val workoutId: Long? = null,
)

@Entity(
    tableName = "sessions",
    indices = [Index(value = ["date"], unique = true)],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long?,
    val workoutName: String,
    val date: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val makesUpForDate: String? = null,
)

@Entity(
    tableName = "session_sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
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

@Entity(
    tableName = "session_plan_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SessionPlanSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val alternativeId: Long,
    val position: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSec: Int? = null,
    val durationMin: Int? = null,
    val distanceKm: Double? = null,
)

@Entity(
    tableName = "session_rotation_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SessionRotationSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val slotId: Long,
    val rotationIndex: Int,
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SETTINGS_ROW,
    val defaultRestSeconds: Int,
) {
    companion object {
        const val SETTINGS_ROW = 1
    }
}
