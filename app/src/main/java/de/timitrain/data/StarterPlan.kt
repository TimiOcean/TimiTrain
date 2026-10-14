package de.timitrain.data

import de.timitrain.domain.ExerciseCategory
import de.timitrain.domain.ExerciseType
import de.timitrain.domain.SetValues

data class StarterExercise(
    val name: String,
    val type: ExerciseType,
    val sets: List<SetValues>,
)

data class StarterSlot(
    val alternatives: List<String>,
    /** Slot sets that differ from the exercise default. Keyed by exercise name. */
    val plannedSets: Map<String, List<SetValues>> = emptyMap(),
)

data class StarterWorkout(
    val name: String,
    val slots: List<StarterSlot>,
)

/**
 * First-launch catalog. Dumbbell weights are the weight of one dumbbell.
 * A rep range would be stored as its lower number; every starter set already
 * names a single rep count. Ausfallschritte, Pallof Press, Dead Bug, and Bird Dog
 * are per side. Bird Dog (Mobility) is not.
 */
object StarterPlan {
    const val PUSH = "Push"
    const val PULL = "Pull"
    const val LEGS = "Beine"
    const val UPPER = "Oberkörper + Haltung"
    const val LOWER = "Unterkörper + Core + Cardio"
    const val MOBILITY_C = "Mobility C"
    const val CARDIO = "Zone-2-Cardio"
    const val DEFAULT_CARDIO_MINUTES = 10
    const val FRIDAY_CARDIO_MINUTES = 20

    val mobilityExerciseNames: Set<String> = setOf(
        "Cat-Cow",
        "Thread the Needle",
        "Open Books",
        "Wall Angels",
        "Schulterkreisen mit Band",
        "Deep Squat Hold",
        "90/90-Hip-Switch",
        "World's Greatest Stretch",
        "Glute Bridge",
        "Leg Swings",
        "Child's Pose",
        "Quadruped Rocking",
        "Foam-Roller-Extension",
        "Hüftbeuger-Dehnung",
        "Bird Dog (Mobility)",
    )

    private val routineA = listOf(
        "Cat-Cow",
        "Thread the Needle",
        "Open Books",
        "Wall Angels",
        "Schulterkreisen mit Band",
    ).map { slot(it) }

    private val routineB = listOf(
        "Deep Squat Hold",
        "90/90-Hip-Switch",
        "World's Greatest Stretch",
        "Glute Bridge",
        "Leg Swings",
    ).map { slot(it) }

    val exercises: List<StarterExercise> = listOf(
        strength("Kurzhantel-Schrägbankdrücken", sets = 3, reps = 8, kg = 12.5),
        strength("Schulterdrücken (Kurzhantel)", sets = 3, reps = 8, kg = 8.0),
        strength("Seitheben", sets = 3, reps = 12, kg = 5.0),
        strength("Trizepsdrücken am Kabel", sets = 3, reps = 10, kg = 15.0),
        strength("Face Pulls", sets = 3, reps = 15, kg = 12.5),
        strength("Latzug", sets = 3, reps = 8, kg = 40.0),
        strength("Rudern brustgestützt", sets = 3, reps = 8, kg = 20.0),
        strength("Kabelrudern", sets = 3, reps = 8, kg = 30.0),
        strength("Reverse Fly", sets = 3, reps = 12, kg = 5.0),
        strength("Bizeps-Curls", sets = 3, reps = 10, kg = 8.0),
        strength("Goblet-Kniebeuge", sets = 3, reps = 8, kg = 16.0),
        strength("Beinpresse", sets = 3, reps = 8, kg = 50.0),
        strength("Rumänisches Kreuzheben", sets = 3, reps = 8, kg = 20.0),
        strength("Wadenheben", sets = 3, reps = 12, kg = 20.0),
        strength("Brustpresse", sets = 3, reps = 10, kg = 25.0),
        strength("Einarmiges Rudern", sets = 3, reps = 10, kg = 12.5),
        strength("Arnold Press", sets = 2, reps = 10, kg = 8.0),
        strength("Schulterdrücken an der Maschine", sets = 2, reps = 10, kg = 15.0),
        strength("Pallof Press pro Seite", sets = 3, reps = 10, kg = 10.0),
        strength("Hip Thrust", sets = 3, reps = 10, kg = 40.0),
        strength("Beinbeuger", sets = 3, reps = 10, kg = 25.0),
        strength("Beinstrecker", sets = 3, reps = 12, kg = 20.0),
        bodyweight("Rückenstrecker", sets = 2, reps = 12),
        bodyweight("Ausfallschritte pro Seite", sets = 2, reps = 10),
        bodyweight("Dead Bug pro Seite", sets = 3, reps = 8),
        bodyweight("Bird Dog pro Seite", sets = 3, reps = 8),
        timed("Cat-Cow", seconds = 60),
        timed("Thread the Needle", seconds = 120),
        timed("Open Books", seconds = 120),
        timed("Wall Angels", seconds = 120),
        timed("Schulterkreisen mit Band", seconds = 60),
        timed("Deep Squat Hold", seconds = 60),
        timed("90/90-Hip-Switch", seconds = 120),
        timed("World's Greatest Stretch", seconds = 120),
        timed("Glute Bridge", seconds = 120),
        timed("Leg Swings", seconds = 60),
        timed("Child's Pose", seconds = 60),
        timed("Quadruped Rocking", seconds = 60),
        timed("Foam-Roller-Extension", seconds = 120),
        timed("Hüftbeuger-Dehnung", seconds = 120),
        timed("Bird Dog (Mobility)", seconds = 120),
        timed("Seitstütz", sets = 3, seconds = 30),
        StarterExercise(
            name = CARDIO,
            type = ExerciseType.CARDIO,
            sets = listOf(SetValues(durationMin = DEFAULT_CARDIO_MINUTES)),
        ),
    )

    val workouts: List<StarterWorkout> = listOf(
        StarterWorkout(
            PUSH,
            inTrainingOrder(
                routineA + listOf(
                    slot("Kurzhantel-Schrägbankdrücken"),
                    slot("Schulterdrücken (Kurzhantel)"),
                    slot("Seitheben"),
                    slot("Trizepsdrücken am Kabel"),
                    slot("Face Pulls"),
                    cardio(DEFAULT_CARDIO_MINUTES),
                ),
            ),
        ),
        StarterWorkout(
            PULL,
            inTrainingOrder(
                routineA + listOf(
                    slot("Latzug"),
                    slot("Rudern brustgestützt", "Kabelrudern"),
                    slot("Reverse Fly"),
                    slot("Bizeps-Curls"),
                    slot("Rückenstrecker"),
                    cardio(DEFAULT_CARDIO_MINUTES),
                ),
            ),
        ),
        StarterWorkout(
            LEGS,
            inTrainingOrder(
                routineB + listOf(
                    slot("Goblet-Kniebeuge", "Beinpresse"),
                    slot("Rumänisches Kreuzheben"),
                    slot("Ausfallschritte pro Seite"),
                    slot("Wadenheben"),
                    cardio(DEFAULT_CARDIO_MINUTES),
                ),
            ),
        ),
        StarterWorkout(
            UPPER,
            inTrainingOrder(
                routineA + listOf(
                    slot("Brustpresse"),
                    slot("Einarmiges Rudern"),
                    slot("Arnold Press", "Schulterdrücken an der Maschine"),
                    slot("Pallof Press pro Seite"),
                    slot("Dead Bug pro Seite"),
                    cardio(DEFAULT_CARDIO_MINUTES),
                ),
            ),
        ),
        StarterWorkout(
            LOWER,
            inTrainingOrder(
                routineB + listOf(
                    slot("Hip Thrust"),
                    slot("Beinbeuger"),
                    slot("Beinstrecker"),
                    slot("Bird Dog pro Seite"),
                    slot("Seitstütz"),
                    cardio(FRIDAY_CARDIO_MINUTES),
                ),
            ),
        ),
        StarterWorkout(
            MOBILITY_C,
            inTrainingOrder(
                listOf(
                    slot("Child's Pose"),
                    slot("Quadruped Rocking"),
                    slot("Foam-Roller-Extension"),
                    slot("Hüftbeuger-Dehnung"),
                    slot("Bird Dog (Mobility)"),
                ),
            ),
        ),
    )

    /** Cardio, then Kraft, then Mobility. Equal categories keep their current order. */
    private fun inTrainingOrder(slots: List<StarterSlot>): List<StarterSlot> {
        return slots.sortedBy { slot ->
            val name = slot.alternatives.first()
            val type = exercises.first { it.name == name }.type
            categoryFor(name, type).ordinal
        }
    }

    /**
     * Zone-2-Cardio is Cardio. The mobility routine, including Bird Dog (Mobility),
     * is Mobility. Strength, bodyweight, and Seitstütz are Kraft.
     */
    fun categoryFor(name: String, type: ExerciseType): ExerciseCategory = when {
        type == ExerciseType.CARDIO -> ExerciseCategory.CARDIO
        name in mobilityExerciseNames -> ExerciseCategory.MOBILITY
        else -> ExerciseCategory.STRENGTH
    }

    /** Weekday 1 is Monday. Saturday and Sunday stay rest days. */
    val week: Map<Int, String?> = mapOf(
        1 to PUSH,
        2 to PULL,
        3 to LEGS,
        4 to UPPER,
        5 to LOWER,
        6 to null,
        7 to null,
    )
}

private fun strength(name: String, sets: Int, reps: Int, kg: Double): StarterExercise {
    return StarterExercise(
        name = name,
        type = ExerciseType.STRENGTH,
        sets = List(sets) { SetValues(reps = reps, weightKg = kg) },
    )
}

private fun bodyweight(name: String, sets: Int, reps: Int): StarterExercise {
    return StarterExercise(
        name = name,
        type = ExerciseType.BODYWEIGHT,
        sets = List(sets) { SetValues(reps = reps) },
    )
}

private fun timed(name: String, seconds: Int, sets: Int = 1): StarterExercise {
    return StarterExercise(
        name = name,
        type = ExerciseType.TIMED,
        sets = List(sets) { SetValues(durationSec = seconds) },
    )
}

private fun slot(vararg names: String): StarterSlot = StarterSlot(names.toList())

private fun cardio(minutes: Int): StarterSlot {
    val override = if (minutes == StarterPlan.DEFAULT_CARDIO_MINUTES) {
        emptyMap()
    } else {
        mapOf(StarterPlan.CARDIO to listOf(SetValues(durationMin = minutes)))
    }
    return StarterSlot(alternatives = listOf(StarterPlan.CARDIO), plannedSets = override)
}
