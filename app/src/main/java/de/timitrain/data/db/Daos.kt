package de.timitrain.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class AlternativeGraph(
    @Embedded val alternative: SlotAlternativeEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "alternativeId")
    val sets: List<PlannedSetEntity>,
)

data class SlotGraph(
    @Embedded val slot: WorkoutSlotEntity,
    @Relation(
        entity = SlotAlternativeEntity::class,
        parentColumn = "id",
        entityColumn = "slotId",
    )
    val alternatives: List<AlternativeGraph>,
)

data class WorkoutGraph(
    @Embedded val workout: WorkoutEntity,
    @Relation(
        entity = WorkoutSlotEntity::class,
        parentColumn = "id",
        entityColumn = "workoutId",
    )
    val slots: List<SlotGraph>,
)

data class SessionWithSets(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val sets: List<SessionSetEntity>,
)

data class WeekDayRow(
    val weekday: Int,
    val workoutId: Long?,
    val workoutName: String?,
)

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE, id")
    suspend fun listAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observe(id: Long): Flow<ExerciseEntity?>

    @Insert
    suspend fun insert(entity: ExerciseEntity): Long

    @Update
    suspend fun update(entity: ExerciseEntity)

    @Query("DELETE FROM exercises")
    suspend fun deleteAll()
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts ORDER BY name COLLATE NOCASE, id")
    suspend fun listAll(): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun get(id: Long): WorkoutEntity?

    @Insert
    suspend fun insert(entity: WorkoutEntity): Long

    @Update
    suspend fun update(entity: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM workouts")
    suspend fun deleteAll()

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeGraph(id: Long): Flow<WorkoutGraph?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun loadGraph(id: Long): WorkoutGraph?

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY name COLLATE NOCASE, id")
    suspend fun loadAllGraphs(): List<WorkoutGraph>
}

@Dao
interface WorkoutSlotDao {
    @Query("SELECT * FROM workout_slots WHERE workoutId = :workoutId ORDER BY position, id")
    suspend fun listForWorkout(workoutId: Long): List<WorkoutSlotEntity>

    @Insert
    suspend fun insert(entity: WorkoutSlotEntity): Long

    @Query("UPDATE workout_slots SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query("UPDATE workout_slots SET rotationIndex = :rotationIndex WHERE id = :id")
    suspend fun updateRotation(id: Long, rotationIndex: Int)

    @Query("DELETE FROM workout_slots WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM workout_slots WHERE id = :id")
    suspend fun get(id: Long): WorkoutSlotEntity?
}

@Dao
interface SlotAlternativeDao {
    @Query("SELECT * FROM slot_alternatives WHERE slotId = :slotId ORDER BY position, id")
    suspend fun listForSlot(slotId: Long): List<SlotAlternativeEntity>

    @Query("SELECT * FROM slot_alternatives WHERE id = :id")
    suspend fun get(id: Long): SlotAlternativeEntity?

    @Insert
    suspend fun insert(entity: SlotAlternativeEntity): Long

    @Update
    suspend fun update(entity: SlotAlternativeEntity)

    @Query("UPDATE slot_alternatives SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query("DELETE FROM slot_alternatives WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PlannedSetDao {
    @Query("SELECT * FROM planned_sets WHERE alternativeId = :alternativeId ORDER BY position, id")
    suspend fun listForAlternative(alternativeId: Long): List<PlannedSetEntity>

    @Query("SELECT * FROM planned_sets WHERE id = :id")
    suspend fun get(id: Long): PlannedSetEntity?

    @Query(
        "SELECT * FROM planned_sets WHERE alternativeId = :alternativeId AND position = :position LIMIT 1",
    )
    suspend fun getAt(alternativeId: Long, position: Int): PlannedSetEntity?

    @Insert
    suspend fun insert(entity: PlannedSetEntity): Long

    @Update
    suspend fun update(entity: PlannedSetEntity)

    @Query("DELETE FROM planned_sets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE planned_sets SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)
}

@Dao
interface WeekPlanDao {
    @Query(
        """
        SELECT week_plan.weekday AS weekday,
               week_plan.workoutId AS workoutId,
               workouts.name AS workoutName
        FROM week_plan
        LEFT JOIN workouts ON workouts.id = week_plan.workoutId
        ORDER BY week_plan.weekday
        """,
    )
    fun observe(): Flow<List<WeekDayRow>>

    @Query(
        """
        SELECT week_plan.weekday AS weekday,
               week_plan.workoutId AS workoutId,
               workouts.name AS workoutName
        FROM week_plan
        LEFT JOIN workouts ON workouts.id = week_plan.workoutId
        ORDER BY week_plan.weekday
        """,
    )
    suspend fun list(): List<WeekDayRow>

    @Query("UPDATE week_plan SET workoutId = :workoutId WHERE weekday = :weekday")
    suspend fun setWorkout(weekday: Int, workoutId: Long?)

    @Query("SELECT weekday FROM week_plan WHERE workoutId = :workoutId ORDER BY weekday")
    suspend fun weekdaysUsing(workoutId: Long): List<Int>
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY date, id")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY date, id")
    suspend fun listAll(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE date = :date LIMIT 1")
    suspend fun onDate(date: String): SessionEntity?

    @Transaction
    @Query("SELECT * FROM sessions WHERE date = :date LIMIT 1")
    suspend fun withSetsOnDate(date: String): SessionWithSets?

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY date, id")
    suspend fun loadAllWithSets(): List<SessionWithSets>

    @Insert
    suspend fun insert(entity: SessionEntity): Long

    @Insert
    suspend fun insertSet(entity: SessionSetEntity): Long

    @Query(
        """
        SELECT session_sets.* FROM session_sets
        INNER JOIN sessions ON sessions.id = session_sets.sessionId
        WHERE session_sets.exerciseId = :exerciseId AND session_sets.position = :position
        ORDER BY sessions.date DESC, sessions.id DESC
        LIMIT 1
        """,
    )
    suspend fun lastSet(exerciseId: Long, position: Int): SessionSetEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Query("DELETE FROM session_sets")
    suspend fun deleteAllSets()

    @Insert
    suspend fun insertPlanSnapshot(entity: SessionPlanSnapshotEntity): Long

    @Insert
    suspend fun insertRotationSnapshot(entity: SessionRotationSnapshotEntity): Long

    @Query("SELECT * FROM session_plan_snapshots WHERE sessionId = :sessionId ORDER BY id")
    suspend fun planSnapshots(sessionId: Long): List<SessionPlanSnapshotEntity>

    @Query("SELECT * FROM session_rotation_snapshots WHERE sessionId = :sessionId ORDER BY id")
    suspend fun rotationSnapshots(sessionId: Long): List<SessionRotationSnapshotEntity>

    @Query("SELECT * FROM session_plan_snapshots ORDER BY id")
    suspend fun allPlanSnapshots(): List<SessionPlanSnapshotEntity>

    @Query("SELECT * FROM session_rotation_snapshots ORDER BY id")
    suspend fun allRotationSnapshots(): List<SessionRotationSnapshotEntity>

    @Query("DELETE FROM session_plan_snapshots")
    suspend fun deleteAllPlanSnapshots()

    @Query("DELETE FROM session_rotation_snapshots")
    suspend fun deleteAllRotationSnapshots()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Insert
    suspend fun insert(entity: SettingsEntity)

    @Update
    suspend fun update(entity: SettingsEntity)
}
