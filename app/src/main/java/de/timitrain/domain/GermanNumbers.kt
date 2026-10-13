package de.timitrain.domain

import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale

object GermanNumbers {
    private val locale = Locale.GERMANY

    fun format(value: Double, fractionDigits: Int = 1): String {
        val format = NumberFormat.getNumberInstance(locale)
        format.maximumFractionDigits = fractionDigits
        format.minimumFractionDigits = 0
        format.isGroupingUsed = false
        return format.format(value)
    }

    fun parse(text: String): Double? {
        val trimmed = text.trim().replace(" ", "").replace('.', ',')
        if (trimmed.isEmpty()) return null
        val format = NumberFormat.getNumberInstance(locale)
        val position = ParsePosition(0)
        val parsed = format.parse(trimmed, position) ?: return null
        if (position.index != trimmed.length || position.errorIndex >= 0) return null
        return parsed.toDouble()
    }

    fun formatSet(values: SetValues, type: ExerciseType): String {
        return when (type) {
            ExerciseType.STRENGTH -> "${values.reps ?: 0} × ${format(values.weightKg ?: 0.0)} kg"
            ExerciseType.BODYWEIGHT -> "${values.reps ?: 0} Wdh."
            ExerciseType.TIMED -> "${values.durationSec ?: 0} s"
            ExerciseType.CARDIO -> {
                val parts = listOfNotNull(
                    values.distanceKm?.let { "${format(it)} km" },
                    values.durationMin?.let { "$it min" },
                )
                parts.joinToString(" · ").ifEmpty { "–" }
            }
        }
    }
}
