package de.timitrain.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ExerciseType {
    STRENGTH,
    BODYWEIGHT,
    TIMED,
    CARDIO,
}

fun ExerciseType.stored(): String = name

fun String.toExerciseType(): ExerciseType = ExerciseType.valueOf(this)

/**
 * Plan section. Independent of [ExerciseType]: a timed exercise can be Kraft
 * (Seitstütz) or Mobility, and Cardio is its own section.
 */
enum class ExerciseCategory {
    CARDIO,
    STRENGTH,
    MOBILITY,
}

fun ExerciseCategory.stored(): String = name

fun String.toExerciseCategory(): ExerciseCategory = ExerciseCategory.valueOf(this)

fun String.toExerciseCategoryOrNull(): ExerciseCategory? = try {
    ExerciseCategory.valueOf(this)
} catch (_: IllegalArgumentException) {
    null
}

/** Seconds for the exercise countdown. Null when this set has no planned duration. */
fun countdownSeconds(type: ExerciseType, plan: SetValues): Int? = when (type) {
    ExerciseType.TIMED -> plan.durationSec?.takeIf { it > 0 }
    ExerciseType.CARDIO -> plan.durationMin?.takeIf { it > 0 }?.times(60)
    else -> null
}

data class CategorizedSlot(
    val id: Long,
    val position: Int,
    val category: ExerciseCategory,
)

/**
 * Plan mode shows Cardio, then Kraft, then Mobility. Inside a section the
 * previous positions decide the order. Reordering a section only reassigns
 * the positions that section already held.
 */
object WorkoutSections {
    fun grouped(slots: List<CategorizedSlot>): List<List<CategorizedSlot>> {
        return ExerciseCategory.entries.map { category ->
            slots.filter { it.category == category }.sortedBy { it.position }
        }
    }

    fun positionsAfterReorder(
        slots: List<CategorizedSlot>,
        category: ExerciseCategory,
        orderedIds: List<Long>,
    ): Map<Long, Int> {
        val section = slots.filter { it.category == category }.sortedBy { it.position }
        val positions = section.map { it.position }
        require(orderedIds.size == section.size)
        require(orderedIds.toSet() == section.map { it.id }.toSet())
        return orderedIds.zip(positions).toMap()
    }
}

/**
 * Initial increment and rest values. The spec requires a sensible starting
 * point and that the user can change it. These numbers are not a locked default.
 * Wiederholungen are not listed: their step is always [RepsStep.STEP].
 */
object InitialNumbers {
    const val WEIGHT_INCREMENT_KG = 2.5
    const val DURATION_INCREMENT_SEC = 5
    const val DISTANCE_INCREMENT_KM = 0.1
    const val DURATION_INCREMENT_MIN = 1
    const val DEFAULT_REST_SECONDS = 90
}

data class SetValues(
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSec: Int? = null,
    val durationMin: Int? = null,
    val distanceKm: Double? = null,
)

data class Increments(
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSec: Int? = null,
    val distanceKm: Double? = null,
    val durationMin: Int? = null,
)

fun nonNegative(values: SetValues): Boolean {
    if (values.reps != null && values.reps < 0) return false
    if (values.weightKg != null && values.weightKg < 0.0) return false
    if (values.durationSec != null && values.durationSec < 0) return false
    if (values.durationMin != null && values.durationMin < 0) return false
    if (values.distanceKm != null && values.distanceKm < 0.0) return false
    return true
}

fun cardioHasMetric(distanceKm: Double?, durationMin: Int?): Boolean {
    return distanceKm != null || durationMin != null
}

/** Wiederholungen always move by 1. A stored reps increment is ignored. */
object RepsStep {
    const val STEP = 1

    fun nudge(current: Int?, delta: Int): Int = SetStepper.nudgeInt(current, STEP, delta)
}

/**
 * Values copied onto a new slot alternative. The result is a new list, so later
 * edits of the workout sets and of the exercise defaults do not share rows.
 */
object DefaultSets {
    fun copyForSlot(defaults: List<SetValues>): List<SetValues> = defaults.map { it.copy() }
}

object SetStepper {
    fun nudgeInt(current: Int?, increment: Int, delta: Int): Int {
        require(increment > 0)
        val base = current ?: 0
        return (base + delta * increment).coerceAtLeast(0)
    }

    fun nudgeDecimal(current: Double?, increment: Double, delta: Int): Double {
        require(increment > 0.0)
        val base = current?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ZERO
        val step = BigDecimal.valueOf(increment).multiply(BigDecimal.valueOf(delta.toLong()))
        val next = base.add(step)
        return if (next.signum() < 0) 0.0 else next.stripTrailingZeros().toDouble()
    }

    /** Minus on an unset optional metric leaves it unset. Plus starts from zero. */
    fun nudgeOptionalDecimal(current: Double?, increment: Double, delta: Int): Double? {
        if (current == null && delta < 0) return null
        return nudgeDecimal(current, increment, delta)
    }

    fun nudgeOptionalInt(current: Int?, increment: Int, delta: Int): Int? {
        if (current == null && delta < 0) return null
        return nudgeInt(current, increment, delta)
    }
}

object PlanAdjustment {
    /**
     * Moves the plan halfway toward the actual, then rounds as specified:
     * kilograms to 1 kg, reps, seconds and minutes to integers, distance to 0.1 km.
     *
     * A result that lands exactly on a half unit (for example 50.5 kg) is an open
     * product question. [RoundingMode.HALF_UP] is only here so the screen can store
     * a rounded number. It is not a product decision and is not covered by tests.
     * Metrics with no plan value are left unchanged.
     */
    fun adjust(plan: SetValues, actual: SetValues, type: ExerciseType): SetValues {
        return when (type) {
            ExerciseType.STRENGTH -> plan.copy(
                reps = adjustInt(plan.reps, actual.reps),
                weightKg = adjustDecimal(plan.weightKg, actual.weightKg, scale = 0),
            )
            ExerciseType.BODYWEIGHT -> plan.copy(
                reps = adjustInt(plan.reps, actual.reps),
            )
            ExerciseType.TIMED -> plan.copy(
                durationSec = adjustInt(plan.durationSec, actual.durationSec),
            )
            ExerciseType.CARDIO -> plan.copy(
                distanceKm = adjustDecimal(plan.distanceKm, actual.distanceKm, scale = 1),
                durationMin = adjustInt(plan.durationMin, actual.durationMin),
            )
        }
    }

    private fun adjustInt(plan: Int?, actual: Int?): Int? {
        if (plan == null || actual == null) return plan
        return move(BigDecimal(plan), BigDecimal(actual))
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()
    }

    private fun adjustDecimal(plan: Double?, actual: Double?, scale: Int): Double? {
        if (plan == null || actual == null) return plan
        return move(BigDecimal.valueOf(plan), BigDecimal.valueOf(actual))
            .setScale(scale, RoundingMode.HALF_UP)
            .toDouble()
    }

    private fun move(plan: BigDecimal, actual: BigDecimal): BigDecimal {
        return plan.add(actual.subtract(plan).multiply(BigDecimal("0.5")))
    }
}

object Rotation {
    fun normalize(index: Int, count: Int): Int {
        if (count <= 0) return 0
        return Math.floorMod(index, count)
    }

    fun advance(index: Int, count: Int): Int {
        if (count <= 0) return 0
        return Math.floorMod(index + 1, count)
    }

    fun afterSave(index: Int, count: Int, completedSetInSlot: Boolean): Int {
        return if (completedSetInSlot) advance(index, count) else normalize(index, count)
    }
}

data class SessionFact(
    val date: LocalDate,
    val makesUpForDate: LocalDate? = null,
    val completedSet: Boolean = true,
)

/**
 * Streak and makeup eligibility from an explicit day plan.
 * The caller decides which days were planned. This does not choose a workout.
 */
object StreakRules {
    fun streak(
        today: LocalDate,
        sessions: List<SessionFact>,
        isPlanned: (LocalDate) -> Boolean,
    ): Int {
        val trainings = sessions.filter { it.completedSet }
        if (trainings.isEmpty() && sessions.isEmpty()) return 0
        val covered = coveredDates(sessions)
        val earliest = sessions.minOfOrNull { it.date } ?: return 0
        var latestMiss: LocalDate? = null
        var day = earliest
        val yesterday = today.minusDays(1)
        while (!day.isAfter(yesterday)) {
            if (isPlanned(day) && day !in covered) {
                latestMiss = day
            }
            day = day.plusDays(1)
        }
        return if (latestMiss == null) {
            trainings.size
        } else {
            trainings.count { it.date > latestMiss }
        }
    }

    fun coveredDates(sessions: List<SessionFact>): Set<LocalDate> {
        return sessions.flatMap { session ->
            listOfNotNull(session.date, session.makesUpForDate)
        }.toSet()
    }

    /**
     * Open makeups on [today]. Empty when today is planned or already has a session.
     * A miss is a planned day that ended uncovered. Age is 1 through 7 days.
     */
    fun openMakeups(
        today: LocalDate,
        todayIsPlanned: Boolean,
        todayHasSession: Boolean,
        plannedDays: Set<LocalDate>,
        sessions: List<SessionFact>,
    ): List<LocalDate> {
        if (todayIsPlanned || todayHasSession) return emptyList()
        val covered = coveredDates(sessions)
        return plannedDays
            .filter { missed ->
                if (!missed.isBefore(today)) return@filter false
                val age = ChronoUnit.DAYS.between(missed, today)
                age in 1..7 && missed !in covered
            }
            .sorted()
    }
}

/**
 * The workout a makeup starts is the one currently assigned to the missed weekday.
 */
object MakeupTarget {
    fun workoutId(missedDate: LocalDate, weekdayWorkoutIds: Map<Int, Long?>): Long? {
        return weekdayWorkoutIds[missedDate.dayOfWeek.value]
    }
}

data class PlanTouch(
    val alternativeId: Long,
    val setPosition: Int,
    val before: SetValues,
)

data class RotationTouch(
    val slotId: Long,
    val beforeIndex: Int,
)

data class SessionTouch(
    val sessionId: Long,
    val date: LocalDate,
    val plans: List<PlanTouch>,
    val rotations: List<RotationTouch>,
)

data class Rewind(
    val plans: List<PlanTouch>,
    val rotations: List<RotationTouch>,
)

/**
 * Restores a deleted session's start snapshots unless a later session already
 * touched the same planned set or the same slot.
 */
object SessionRewind {
    fun restore(deleted: SessionTouch, others: List<SessionTouch>): Rewind {
        val laterPlans = mutableSetOf<Pair<Long, Int>>()
        val laterSlots = mutableSetOf<Long>()
        for (other in others) {
            if (!isLater(other, deleted)) continue
            for (plan in other.plans) {
                laterPlans += plan.alternativeId to plan.setPosition
            }
            for (rotation in other.rotations) {
                laterSlots += rotation.slotId
            }
        }
        return Rewind(
            plans = deleted.plans.filter { (it.alternativeId to it.setPosition) !in laterPlans },
            rotations = deleted.rotations.filter { it.slotId !in laterSlots },
        )
    }

    private fun isLater(candidate: SessionTouch, deleted: SessionTouch): Boolean {
        return candidate.date > deleted.date ||
            (candidate.date == deleted.date && candidate.sessionId > deleted.sessionId)
    }
}

interface AppClock {
    fun zone(): ZoneId
    fun instant(): Instant
    fun today(): LocalDate = instant().atZone(zone()).toLocalDate()
}

class SystemAppClock : AppClock {
    override fun zone(): ZoneId = ZoneId.systemDefault()
    override fun instant(): Instant = Instant.now()
}
