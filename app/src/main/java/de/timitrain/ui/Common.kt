package de.timitrain.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.timitrain.R
import de.timitrain.TimiTrainApp
import de.timitrain.domain.ExerciseType
import de.timitrain.domain.InputProblem

@Composable
fun rememberApp(): TimiTrainApp {
    return LocalContext.current.applicationContext as TimiTrainApp
}

fun <T : ViewModel> viewModelFactory(create: () -> T): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
}

@Composable
fun exerciseTypeLabel(type: ExerciseType): String {
    return when (type) {
        ExerciseType.STRENGTH -> stringResource(R.string.type_strength)
        ExerciseType.BODYWEIGHT -> stringResource(R.string.type_bodyweight)
        ExerciseType.TIMED -> stringResource(R.string.type_timed)
        ExerciseType.CARDIO -> stringResource(R.string.type_cardio)
    }
}

@Composable
fun weekdayLabel(weekday: Int): String {
    return when (weekday) {
        1 -> stringResource(R.string.weekday_mon)
        2 -> stringResource(R.string.weekday_tue)
        3 -> stringResource(R.string.weekday_wed)
        4 -> stringResource(R.string.weekday_thu)
        5 -> stringResource(R.string.weekday_fri)
        6 -> stringResource(R.string.weekday_sat)
        else -> stringResource(R.string.weekday_sun)
    }
}

@Composable
fun problemText(problem: InputProblem): String {
    return when (problem) {
        InputProblem.NAME -> stringResource(R.string.name_required)
        InputProblem.STEP -> stringResource(R.string.step_positive)
        InputProblem.NEGATIVE -> stringResource(R.string.value_negative)
        InputProblem.CARDIO -> stringResource(R.string.cardio_required)
        InputProblem.NUMBER -> stringResource(R.string.number_invalid)
        InputProblem.REQUIRED -> stringResource(R.string.required_value)
        InputProblem.REST -> stringResource(R.string.rest_negative)
        InputProblem.BACKUP_READ -> stringResource(R.string.import_failed)
        InputProblem.BACKUP_WRITE -> stringResource(R.string.backup_failed)
        InputProblem.BACKUP_REJECT -> stringResource(R.string.import_rejected)
    }
}
