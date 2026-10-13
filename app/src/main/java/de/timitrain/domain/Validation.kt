package de.timitrain.domain

enum class InputProblem {
    NAME,
    STEP,
    NEGATIVE,
    CARDIO,
    NUMBER,
    REQUIRED,
    REST,
    BACKUP_READ,
    BACKUP_WRITE,
    BACKUP_REJECT,
}

fun validateIncrements(type: ExerciseType, increments: Increments): InputProblem? {
    return when (type) {
        ExerciseType.STRENGTH -> checkDecimal(increments.weightKg)
        ExerciseType.BODYWEIGHT -> null
        ExerciseType.TIMED -> checkStep(increments.durationSec)
        ExerciseType.CARDIO -> checkPair(increments.durationMin, increments.distanceKm)
    }
}

fun validatePlanned(type: ExerciseType, values: SetValues): InputProblem? {
    if (!nonNegative(values)) return InputProblem.NEGATIVE
    return when (type) {
        ExerciseType.STRENGTH ->
            if (values.reps == null || values.weightKg == null) InputProblem.REQUIRED else null
        ExerciseType.BODYWEIGHT ->
            if (values.reps == null) InputProblem.REQUIRED else null
        ExerciseType.TIMED ->
            if (values.durationSec == null) InputProblem.REQUIRED else null
        ExerciseType.CARDIO ->
            if (!cardioHasMetric(values.distanceKm, values.durationMin)) InputProblem.CARDIO else null
    }
}

fun defaultIncrements(type: ExerciseType): Increments {
    return when (type) {
        ExerciseType.STRENGTH -> Increments(weightKg = InitialNumbers.WEIGHT_INCREMENT_KG)
        ExerciseType.BODYWEIGHT -> Increments()
        ExerciseType.TIMED -> Increments(durationSec = InitialNumbers.DURATION_INCREMENT_SEC)
        ExerciseType.CARDIO -> Increments(
            distanceKm = InitialNumbers.DISTANCE_INCREMENT_KM,
            durationMin = InitialNumbers.DURATION_INCREMENT_MIN,
        )
    }
}

private fun checkDecimal(value: Double?): InputProblem? {
    return when {
        value == null -> InputProblem.REQUIRED
        value < 0.0 -> InputProblem.NEGATIVE
        value == 0.0 -> InputProblem.STEP
        else -> null
    }
}

private fun checkStep(value: Int?): InputProblem? {
    return when {
        value == null -> InputProblem.REQUIRED
        value < 0 -> InputProblem.NEGATIVE
        value == 0 -> InputProblem.STEP
        else -> null
    }
}

private fun checkPair(first: Int?, second: Double?): InputProblem? {
    if (first == null || second == null) return InputProblem.REQUIRED
    if (first < 0 || second < 0.0) return InputProblem.NEGATIVE
    if (first == 0 || second == 0.0) return InputProblem.STEP
    return null
}
