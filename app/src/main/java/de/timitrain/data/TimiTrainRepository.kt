package de.timitrain.data

import androidx.room.withTransaction
import de.timitrain.data.backup.BackupAlternative
import de.timitrain.data.backup.BackupDocument
import de.timitrain.data.backup.BackupExercise
import de.timitrain.data.backup.BackupPlanSnapshot
import de.timitrain.data.backup.BackupPlannedSet
import de.timitrain.data.backup.BackupRotationSnapshot
import de.timitrain.data.backup.BackupSession
import de.timitrain.data.backup.BackupSessionSet
import de.timitrain.data.backup.BackupSlot
import de.timitrain.data.backup.BackupWeekDay
import de.timitrain.data.backup.BackupWorkout
import de.timitrain.data.db.AlternativeGraph
import de.timitrain.data.db.ExerciseEntity
import de.timitrain.data.db.PlannedSetEntity
import de.timitrain.data.db.SessionEntity
import de.timitrain.data.db.SessionPlanSnapshotEntity
import de.timitrain.data.db.SessionRotationSnapshotEntity
import de.timitrain.data.db.SessionSetEntity
import de.timitrain.data.db.SessionWithSets
import de.timitrain.data.db.SettingsEntity
import de.timitrain.data.db.SlotAlternativeEntity
import de.timitrain.data.db.SlotGraph
import de.timitrain.data.db.TimiTrainDatabase
import de.timitrain.data.db.WeekDayRow
import de.timitrain.data.db.WorkoutEntity
import de.timitrain.data.db.WorkoutGraph
import de.timitrain.data.db.WorkoutSlotEntity
import de.timitrain.domain.ExerciseType
import de.timitrain.domain.Increments
import de.timitrain.domain.InitialNumbers
import de.timitrain.domain.PlanAdjustment
import de.timitrain.domain.PlanTouch
import de.timitrain.domain.Rotation
import de.timitrain.domain.RotationTouch
import de.timitrain.domain.SessionRewind
import de.timitrain.domain.SessionTouch
import de.timitrain.domain.SetValues
import de.timitrain.domain.stored
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class TimiTrainRepository(
    private val db: TimiTrainDatabase,
) {
    fun observeExercises(): Flow<List<ExerciseEntity>> = db.exercises().observeAll()

    fun observeExercise(id: Long): Flow<ExerciseEntity?> = db.exercises().observe(id)

    suspend fun exercise(id: Long): ExerciseEntity? = db.exercises().get(id)

    suspend fun createExercise(
        name: String,
        type: ExerciseType,
        increments: Increments,
    ): Long {
        return db.exercises().insert(
            ExerciseEntity(
                name = name.trim(),
                type = type.stored(),
                repsIncrement = increments.reps,
                weightIncrementKg = increments.weightKg,
                durationIncrementSec = increments.durationSec,
                distanceIncrementKm = increments.distanceKm,
                durationIncrementMin = increments.durationMin,
            ),
        )
    }

    suspend fun updateIncrements(id: Long, increments: Increments) {
        val existing = db.exercises().get(id) ?: return
        db.exercises().update(
            existing.copy(
                repsIncrement = increments.reps,
                weightIncrementKg = increments.weightKg,
                durationIncrementSec = increments.durationSec,
                distanceIncrementKm = increments.distanceKm,
                durationIncrementMin = increments.durationMin,
            ),
        )
    }

    fun observeWorkouts(): Flow<List<WorkoutEntity>> = db.workouts().observeAll()

    fun observeWorkout(id: Long): Flow<WorkoutGraph?> {
        return db.workouts().observeGraph(id).map { it?.sortedGraph() }
    }

    suspend fun loadWorkout(id: Long): WorkoutGraph? = db.workouts().loadGraph(id)?.sortedGraph()

    suspend fun createWorkout(name: String): Long {
        return db.workouts().insert(WorkoutEntity(name = name.trim()))
    }

    suspend fun renameWorkout(id: Long, name: String) {
        val workout = db.workouts().get(id) ?: return
        db.workouts().update(workout.copy(name = name.trim()))
    }

    suspend fun duplicateWorkout(id: Long): Long? = db.withTransaction {
        val graph = db.workouts().loadGraph(id)?.sortedGraph() ?: return@withTransaction null
        val copyId = db.workouts().insert(WorkoutEntity(name = "${graph.workout.name} (Kopie)"))
        for (slot in graph.slots) {
            val slotId = db.slots().insert(
                WorkoutSlotEntity(
                    workoutId = copyId,
                    position = slot.slot.position,
                    rotationIndex = 0,
                ),
            )
            for (alternative in slot.alternatives) {
                val alternativeId = db.alternatives().insert(
                    alternative.alternative.copy(id = 0, slotId = slotId),
                )
                for (set in alternative.sets) {
                    db.plannedSets().insert(set.copy(id = 0, alternativeId = alternativeId))
                }
            }
        }
        copyId
    }

    suspend fun weekdaysUsing(workoutId: Long): List<Int> = db.weekPlan().weekdaysUsing(workoutId)

    suspend fun deleteWorkout(id: Long) {
        db.workouts().delete(id)
    }

    suspend fun addSlot(workoutId: Long, exerciseId: Long) = db.withTransaction {
        val slots = db.slots().listForWorkout(workoutId)
        val position = (slots.maxOfOrNull { it.position } ?: -1) + 1
        val slotId = db.slots().insert(
            WorkoutSlotEntity(workoutId = workoutId, position = position, rotationIndex = 0),
        )
        db.alternatives().insert(
            SlotAlternativeEntity(slotId = slotId, exerciseId = exerciseId, position = 0),
        )
    }

    suspend fun addAlternative(slotId: Long, exerciseId: Long) = db.withTransaction {
        val existing = db.alternatives().listForSlot(slotId)
        val position = (existing.maxOfOrNull { it.position } ?: -1) + 1
        db.alternatives().insert(
            SlotAlternativeEntity(slotId = slotId, exerciseId = exerciseId, position = position),
        )
        val slot = db.slots().get(slotId) ?: return@withTransaction
        db.slots().updateRotation(slotId, Rotation.normalize(slot.rotationIndex, existing.size + 1))
    }

    suspend fun removeAlternative(alternativeId: Long) = db.withTransaction {
        val alternative = db.alternatives().get(alternativeId) ?: return@withTransaction
        val slot = db.slots().get(alternative.slotId) ?: return@withTransaction
        db.alternatives().delete(alternativeId)
        val remaining = db.alternatives().listForSlot(alternative.slotId)
        if (remaining.isEmpty()) {
            db.slots().delete(slot.id)
            resequenceSlots(slot.workoutId)
        } else {
            remaining.forEachIndexed { index, item ->
                db.alternatives().updatePosition(item.id, index)
            }
            db.slots().updateRotation(slot.id, Rotation.normalize(slot.rotationIndex, remaining.size))
        }
    }

    suspend fun removeSlot(slotId: Long) = db.withTransaction {
        val slot = db.slots().get(slotId) ?: return@withTransaction
        db.slots().delete(slotId)
        resequenceSlots(slot.workoutId)
    }

    suspend fun reorderSlots(workoutId: Long, orderedIds: List<Long>) = db.withTransaction {
        orderedIds.forEachIndexed { index, id -> db.slots().updatePosition(id, index) }
    }

    suspend fun reorderAlternatives(slotId: Long, orderedIds: List<Long>) = db.withTransaction {
        orderedIds.forEachIndexed { index, id -> db.alternatives().updatePosition(id, index) }
        val slot = db.slots().get(slotId) ?: return@withTransaction
        db.slots().updateRotation(slotId, Rotation.normalize(slot.rotationIndex, orderedIds.size))
    }

    suspend fun setRestOverride(alternativeId: Long, seconds: Int?) {
        val alternative = db.alternatives().get(alternativeId) ?: return
        db.alternatives().update(alternative.copy(restSecondsOverride = seconds))
    }

    suspend fun addPlannedSet(alternativeId: Long, values: SetValues) = db.withTransaction {
        val sets = db.plannedSets().listForAlternative(alternativeId)
        val position = (sets.maxOfOrNull { it.position } ?: -1) + 1
        db.plannedSets().insert(values.toEntity(alternativeId, position))
    }

    suspend fun updatePlannedSet(setId: Long, values: SetValues) {
        val existing = db.plannedSets().get(setId) ?: return
        db.plannedSets().update(
            existing.copy(
                reps = values.reps,
                weightKg = values.weightKg,
                durationSec = values.durationSec,
                durationMin = values.durationMin,
                distanceKm = values.distanceKm,
            ),
        )
    }

    suspend fun deletePlannedSet(setId: Long) = db.withTransaction {
        val existing = db.plannedSets().get(setId) ?: return@withTransaction
        db.plannedSets().delete(setId)
        val remaining = db.plannedSets().listForAlternative(existing.alternativeId)
        remaining.forEachIndexed { index, item ->
            db.plannedSets().updatePosition(item.id, index)
        }
    }

    fun observeWeek(): Flow<List<WeekDayRow>> = db.weekPlan().observe()

    suspend fun setWeekday(weekday: Int, workoutId: Long?) {
        db.weekPlan().setWorkout(weekday, workoutId)
    }

    fun observeSessions(): Flow<List<SessionEntity>> = db.sessions().observeAll()

    suspend fun sessionOn(date: LocalDate): SessionEntity? = db.sessions().onDate(date.toString())

    suspend fun sessionDetail(date: LocalDate): SessionWithSets? {
        return db.sessions().withSetsOnDate(date.toString())
    }

    suspend fun lastValues(exerciseId: Long, position: Int): SetValues? {
        return db.sessions().lastSet(exerciseId, position)?.toValues()
    }

    suspend fun saveSession(draft: SessionDraft) = db.withTransaction {
        check(draft.completed.isNotEmpty())
        val sessionId = db.sessions().insert(
            SessionEntity(
                workoutId = draft.workoutId,
                workoutName = draft.workoutName,
                date = draft.date.toString(),
                startedAt = draft.startedAt,
                finishedAt = draft.finishedAt,
                makesUpForDate = draft.makesUpForDate?.toString(),
            ),
        )
        for (completed in draft.completed) {
            db.sessions().insertSet(
                SessionSetEntity(
                    sessionId = sessionId,
                    exerciseId = completed.exerciseId,
                    exerciseName = completed.exerciseName,
                    slotId = completed.slotId,
                    alternativeId = completed.alternativeId,
                    position = completed.position,
                    reps = completed.actual.reps,
                    weightKg = completed.actual.weightKg,
                    durationSec = completed.actual.durationSec,
                    durationMin = completed.actual.durationMin,
                    distanceKm = completed.actual.distanceKm,
                ),
            )
            db.sessions().insertPlanSnapshot(
                SessionPlanSnapshotEntity(
                    sessionId = sessionId,
                    alternativeId = completed.alternativeId,
                    position = completed.position,
                    reps = completed.plan.reps,
                    weightKg = completed.plan.weightKg,
                    durationSec = completed.plan.durationSec,
                    durationMin = completed.plan.durationMin,
                    distanceKm = completed.plan.distanceKm,
                ),
            )
            val planned = db.plannedSets().getAt(completed.alternativeId, completed.position) ?: continue
            val adjusted = PlanAdjustment.adjust(completed.plan, completed.actual, completed.type)
            db.plannedSets().update(planned.withValues(adjusted))
        }
        for (completed in draft.completed.distinctBy { it.slotId }) {
            db.sessions().insertRotationSnapshot(
                SessionRotationSnapshotEntity(
                    sessionId = sessionId,
                    slotId = completed.slotId,
                    rotationIndex = completed.rotationIndex,
                ),
            )
            if (db.slots().get(completed.slotId) == null) continue
            val count = db.alternatives().listForSlot(completed.slotId).size
            db.slots().updateRotation(
                completed.slotId,
                Rotation.afterSave(completed.rotationIndex, count, true),
            )
        }
    }

    suspend fun deleteSession(sessionId: Long) = db.withTransaction {
        val session = db.sessions().get(sessionId) ?: return@withTransaction
        val plansBySession = db.sessions().allPlanSnapshots().groupBy { it.sessionId }
        val rotationsBySession = db.sessions().allRotationSnapshots().groupBy { it.sessionId }
        val deleted = session.toTouch(
            plansBySession[sessionId].orEmpty(),
            rotationsBySession[sessionId].orEmpty(),
        )
        val others = db.sessions().listAll()
            .filter { it.id != sessionId }
            .map { other ->
                other.toTouch(
                    plansBySession[other.id].orEmpty(),
                    rotationsBySession[other.id].orEmpty(),
                )
            }
        val rewind = SessionRewind.restore(deleted, others)
        for (plan in rewind.plans) {
            val row = db.plannedSets().getAt(plan.alternativeId, plan.setPosition) ?: continue
            db.plannedSets().update(row.withValues(plan.before))
        }
        for (rotation in rewind.rotations) {
            if (db.slots().get(rotation.slotId) == null) continue
            db.slots().updateRotation(rotation.slotId, rotation.beforeIndex)
        }
        db.sessions().delete(sessionId)
    }

    fun observeSettings(): Flow<SettingsEntity?> = db.settings().observe()

    suspend fun restSeconds(): Int {
        return db.settings().get()?.defaultRestSeconds ?: InitialNumbers.DEFAULT_REST_SECONDS
    }

    suspend fun setRestSeconds(seconds: Int) {
        val current = db.settings().get()
        if (current == null) {
            db.settings().insert(SettingsEntity(defaultRestSeconds = seconds))
        } else {
            db.settings().update(current.copy(defaultRestSeconds = seconds))
        }
    }

    suspend fun exportDocument(): BackupDocument {
        val exercises = db.exercises().listAll().map { it.toBackup() }
        val workouts = db.workouts().loadAllGraphs().map { it.sortedGraph().toBackup() }
        val week = db.weekPlan().list().map { BackupWeekDay(it.weekday, it.workoutId) }
        val plansBySession = db.sessions().allPlanSnapshots().groupBy { it.sessionId }
        val rotationsBySession = db.sessions().allRotationSnapshots().groupBy { it.sessionId }
        val sessions = db.sessions().loadAllWithSets().map { row ->
            row.toBackup(
                plansBySession[row.session.id].orEmpty(),
                rotationsBySession[row.session.id].orEmpty(),
            )
        }
        return BackupDocument(
            schemaVersion = de.timitrain.data.backup.BackupCodec.SCHEMA_VERSION,
            exercises = exercises,
            workouts = workouts,
            weekPlan = week,
            sessions = sessions,
        )
    }

    suspend fun replaceAll(document: BackupDocument) = db.withTransaction {
        db.sessions().deleteAllPlanSnapshots()
        db.sessions().deleteAllRotationSnapshots()
        db.sessions().deleteAllSets()
        db.sessions().deleteAll()
        db.workouts().deleteAll()
        db.exercises().deleteAll()
        for (exercise in document.exercises) {
            db.exercises().insert(exercise.toEntity())
        }
        for (workout in document.workouts) {
            db.workouts().insert(WorkoutEntity(id = workout.id, name = workout.name))
            for (slot in workout.slots.sortedBy { it.position }) {
                db.slots().insert(
                    WorkoutSlotEntity(
                        id = slot.id,
                        workoutId = workout.id,
                        position = slot.position,
                        rotationIndex = Rotation.normalize(slot.rotationIndex, slot.alternatives.size),
                    ),
                )
                for (alternative in slot.alternatives.sortedBy { it.position }) {
                    db.alternatives().insert(
                        SlotAlternativeEntity(
                            id = alternative.id,
                            slotId = slot.id,
                            exerciseId = alternative.exerciseId,
                            position = alternative.position,
                            restSecondsOverride = alternative.restSecondsOverride,
                        ),
                    )
                    for (set in alternative.sets.sortedBy { it.position }) {
                        db.plannedSets().insert(set.toEntity(alternative.id))
                    }
                }
            }
        }
        for (day in document.weekPlan) {
            db.weekPlan().setWorkout(day.weekday, day.workoutId)
        }
        for (session in document.sessions) {
            db.sessions().insert(session.toEntity())
            for (set in session.sets) {
                db.sessions().insertSet(set.toEntity(session.id))
            }
            for (snapshot in session.planSnapshots) {
                db.sessions().insertPlanSnapshot(snapshot.toEntity(session.id))
            }
            for (snapshot in session.rotationSnapshots) {
                db.sessions().insertRotationSnapshot(snapshot.toEntity(session.id))
            }
        }
    }

    private suspend fun resequenceSlots(workoutId: Long) {
        val slots = db.slots().listForWorkout(workoutId)
        slots.forEachIndexed { index, slot -> db.slots().updatePosition(slot.id, index) }
    }
}

data class CompletedSetDraft(
    val slotId: Long,
    val alternativeId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val position: Int,
    val type: ExerciseType,
    val plan: SetValues,
    val actual: SetValues,
    val rotationIndex: Int,
)

data class SessionDraft(
    val workoutId: Long,
    val workoutName: String,
    val date: LocalDate,
    val startedAt: Long,
    val finishedAt: Long,
    val makesUpForDate: LocalDate? = null,
    val completed: List<CompletedSetDraft>,
)

fun WorkoutGraph.sortedGraph(): WorkoutGraph {
    return copy(
        slots = slots.sortedBy { it.slot.position }.map { slot ->
            slot.copy(
                alternatives = slot.alternatives.sortedBy { it.alternative.position }.map { alternative ->
                    alternative.copy(sets = alternative.sets.sortedBy { it.position })
                },
            )
        },
    )
}

fun PlannedSetEntity.toValues(): SetValues {
    return SetValues(
        reps = reps,
        weightKg = weightKg,
        durationSec = durationSec,
        durationMin = durationMin,
        distanceKm = distanceKm,
    )
}

fun SessionSetEntity.toValues(): SetValues {
    return SetValues(
        reps = reps,
        weightKg = weightKg,
        durationSec = durationSec,
        durationMin = durationMin,
        distanceKm = distanceKm,
    )
}

private fun SetValues.toEntity(alternativeId: Long, position: Int): PlannedSetEntity {
    return PlannedSetEntity(
        alternativeId = alternativeId,
        position = position,
        reps = reps,
        weightKg = weightKg,
        durationSec = durationSec,
        durationMin = durationMin,
        distanceKm = distanceKm,
    )
}

private fun ExerciseEntity.toBackup(): BackupExercise {
    return BackupExercise(
        id = id,
        name = name,
        type = type,
        repsIncrement = repsIncrement,
        weightIncrementKg = weightIncrementKg,
        durationIncrementSec = durationIncrementSec,
        distanceIncrementKm = distanceIncrementKm,
        durationIncrementMin = durationIncrementMin,
    )
}

private fun BackupExercise.toEntity(): ExerciseEntity {
    return ExerciseEntity(
        id = id,
        name = name,
        type = type,
        repsIncrement = repsIncrement,
        weightIncrementKg = weightIncrementKg,
        durationIncrementSec = durationIncrementSec,
        distanceIncrementKm = distanceIncrementKm,
        durationIncrementMin = durationIncrementMin,
    )
}

private fun WorkoutGraph.toBackup(): BackupWorkout {
    return BackupWorkout(
        id = workout.id,
        name = workout.name,
        slots = slots.map { it.toBackup() },
    )
}

private fun SlotGraph.toBackup(): BackupSlot {
    return BackupSlot(
        id = slot.id,
        position = slot.position,
        rotationIndex = slot.rotationIndex,
        alternatives = alternatives.map { it.toBackup() },
    )
}

private fun AlternativeGraph.toBackup(): BackupAlternative {
    return BackupAlternative(
        id = alternative.id,
        exerciseId = alternative.exerciseId,
        position = alternative.position,
        restSecondsOverride = alternative.restSecondsOverride,
        sets = sets.map { set ->
            BackupPlannedSet(
                id = set.id,
                position = set.position,
                reps = set.reps,
                weightKg = set.weightKg,
                durationSec = set.durationSec,
                durationMin = set.durationMin,
                distanceKm = set.distanceKm,
            )
        },
    )
}

private fun BackupPlannedSet.toEntity(alternativeId: Long): PlannedSetEntity {
    return PlannedSetEntity(
        id = id,
        alternativeId = alternativeId,
        position = position,
        reps = reps,
        weightKg = weightKg,
        durationSec = durationSec,
        durationMin = durationMin,
        distanceKm = distanceKm,
    )
}

private fun SessionWithSets.toBackup(
    plans: List<SessionPlanSnapshotEntity>,
    rotations: List<SessionRotationSnapshotEntity>,
): BackupSession {
    return BackupSession(
        id = session.id,
        workoutId = session.workoutId,
        workoutName = session.workoutName,
        date = session.date,
        startedAt = session.startedAt,
        finishedAt = session.finishedAt,
        makesUpForDate = session.makesUpForDate,
        sets = sets.sortedBy { it.id }.map { set ->
            BackupSessionSet(
                id = set.id,
                exerciseId = set.exerciseId,
                exerciseName = set.exerciseName,
                slotId = set.slotId,
                alternativeId = set.alternativeId,
                position = set.position,
                reps = set.reps,
                weightKg = set.weightKg,
                durationSec = set.durationSec,
                durationMin = set.durationMin,
                distanceKm = set.distanceKm,
            )
        },
        planSnapshots = plans.sortedBy { it.id }.map { snapshot ->
            BackupPlanSnapshot(
                id = snapshot.id,
                alternativeId = snapshot.alternativeId,
                position = snapshot.position,
                reps = snapshot.reps,
                weightKg = snapshot.weightKg,
                durationSec = snapshot.durationSec,
                durationMin = snapshot.durationMin,
                distanceKm = snapshot.distanceKm,
            )
        },
        rotationSnapshots = rotations.sortedBy { it.id }.map { snapshot ->
            BackupRotationSnapshot(
                id = snapshot.id,
                slotId = snapshot.slotId,
                rotationIndex = snapshot.rotationIndex,
            )
        },
    )
}

private fun SessionEntity.toTouch(
    plans: List<SessionPlanSnapshotEntity>,
    rotations: List<SessionRotationSnapshotEntity>,
): SessionTouch {
    return SessionTouch(
        sessionId = id,
        date = LocalDate.parse(date),
        plans = plans.distinctBy { it.alternativeId to it.position }.map { snapshot ->
            PlanTouch(
                alternativeId = snapshot.alternativeId,
                setPosition = snapshot.position,
                before = snapshot.toValues(),
            )
        },
        rotations = rotations.distinctBy { it.slotId }.map { snapshot ->
            RotationTouch(slotId = snapshot.slotId, beforeIndex = snapshot.rotationIndex)
        },
    )
}

private fun PlannedSetEntity.withValues(values: SetValues): PlannedSetEntity {
    return copy(
        reps = values.reps,
        weightKg = values.weightKg,
        durationSec = values.durationSec,
        durationMin = values.durationMin,
        distanceKm = values.distanceKm,
    )
}

private fun SessionPlanSnapshotEntity.toValues(): SetValues {
    return SetValues(reps, weightKg, durationSec, durationMin, distanceKm)
}

private fun BackupSession.toEntity(): SessionEntity {
    return SessionEntity(
        id = id,
        workoutId = workoutId,
        workoutName = workoutName,
        date = date,
        startedAt = startedAt,
        finishedAt = finishedAt,
        makesUpForDate = makesUpForDate,
    )
}

private fun BackupPlanSnapshot.toEntity(sessionId: Long): SessionPlanSnapshotEntity {
    return SessionPlanSnapshotEntity(
        id = id,
        sessionId = sessionId,
        alternativeId = alternativeId,
        position = position,
        reps = reps,
        weightKg = weightKg,
        durationSec = durationSec,
        durationMin = durationMin,
        distanceKm = distanceKm,
    )
}

private fun BackupRotationSnapshot.toEntity(sessionId: Long): SessionRotationSnapshotEntity {
    return SessionRotationSnapshotEntity(
        id = id,
        sessionId = sessionId,
        slotId = slotId,
        rotationIndex = rotationIndex,
    )
}

private fun BackupSessionSet.toEntity(sessionId: Long): SessionSetEntity {
    return SessionSetEntity(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        slotId = slotId,
        alternativeId = alternativeId,
        position = position,
        reps = reps,
        weightKg = weightKg,
        durationSec = durationSec,
        durationMin = durationMin,
        distanceKm = distanceKm,
    )
}
