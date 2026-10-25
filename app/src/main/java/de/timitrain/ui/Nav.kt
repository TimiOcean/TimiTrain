package de.timitrain.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.timitrain.R
import de.timitrain.ui.analyse.AnalyseRoute
import de.timitrain.ui.analyse.SessionDetailRoute
import de.timitrain.ui.planen.ExerciseCreateRoute
import de.timitrain.ui.planen.ExerciseEditRoute
import de.timitrain.ui.planen.ExerciseListRoute
import de.timitrain.ui.planen.PlanenRoute
import de.timitrain.ui.planen.WeekPlanRoute
import de.timitrain.ui.planen.WorkoutEditorRoute
import de.timitrain.ui.settings.SettingsRoute
import de.timitrain.ui.train.SessionRoute
import de.timitrain.ui.train.TrainRoute
import de.timitrain.ui.train.WorkoutPickerRoute
import java.time.LocalDate

object Routes {
    const val PLAN = "planen"
    const val TRAIN = "trainieren"
    const val ANALYSE = "analysieren"
    const val SETTINGS = "settings"
    const val WEEK = "week"
    const val EXERCISES = "exercises"
    const val EXERCISE_NEW = "exercise/new"
    const val EXERCISE_EDIT = "exercise/{exerciseId}?alternative={alternativeId}"
    const val WORKOUT = "workout/{workoutId}"
    const val PICK = "train/pick"
    const val SESSION = "session/{workoutId}?makeup={makeup}"
    const val DAY = "analyse/{date}"

    fun exerciseEdit(id: Long, alternativeId: Long = -1L): String {
        return if (alternativeId < 0) "exercise/$id" else "exercise/$id?alternative=$alternativeId"
    }
    fun workout(id: Long) = "workout/$id"
    fun session(workoutId: Long, makeupDate: String? = null): String {
        return if (makeupDate.isNullOrBlank()) {
            "session/$workoutId"
        } else {
            "session/$workoutId?makeup=$makeupDate"
        }
    }
    fun day(date: String) = "analyse/$date"
}

private val tabs = listOf(Routes.PLAN, Routes.TRAIN, Routes.ANALYSE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimiTrainNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showTabs = route in tabs
    val title = when (route) {
        Routes.PLAN -> stringResource(R.string.tab_plan)
        Routes.TRAIN -> stringResource(R.string.tab_train)
        Routes.ANALYSE -> stringResource(R.string.tab_analyze)
        else -> ""
    }
    Scaffold(
        topBar = {
            if (showTabs) {
                TopAppBar(
                    title = { Text(title) },
                    actions = {
                        IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings_cd),
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    TabItem(
                        selected = route == Routes.PLAN,
                        label = stringResource(R.string.tab_plan),
                        icon = Icons.AutoMirrored.Filled.ViewList,
                        onClick = { nav.navigateTab(Routes.PLAN) },
                    )
                    TabItem(
                        selected = route == Routes.TRAIN,
                        label = stringResource(R.string.tab_train),
                        icon = Icons.Filled.FitnessCenter,
                        onClick = { nav.navigateTab(Routes.TRAIN) },
                    )
                    TabItem(
                        selected = route == Routes.ANALYSE,
                        label = stringResource(R.string.tab_analyze),
                        icon = Icons.Filled.CalendarMonth,
                        onClick = { nav.navigateTab(Routes.ANALYSE) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.PLAN,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.PLAN) { PlanenRoute(nav) }
            composable(Routes.TRAIN) { TrainRoute(nav) }
            composable(Routes.ANALYSE) { AnalyseRoute(nav) }
            composable(Routes.SETTINGS) { SettingsRoute(onBack = { nav.popBackStack() }) }
            composable(Routes.WEEK) { WeekPlanRoute(onBack = { nav.popBackStack() }) }
            composable(Routes.EXERCISES) {
                ExerciseListRoute(
                    onBack = { nav.popBackStack() },
                    onCreate = { nav.navigate(Routes.EXERCISE_NEW) },
                    onEdit = { id -> nav.navigate(Routes.exerciseEdit(id)) },
                )
            }
            composable(Routes.EXERCISE_NEW) {
                ExerciseCreateRoute(
                    onBack = { nav.popBackStack() },
                    onCreated = { id ->
                        nav.previousBackStackEntry?.savedStateHandle?.set("createdExerciseId", id)
                        nav.popBackStack()
                    },
                )
            }
            composable(
                route = Routes.EXERCISE_EDIT,
                arguments = listOf(
                    navArgument("alternativeId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("exerciseId")?.toLongOrNull()
                val alternativeId = entry.arguments?.getLong("alternativeId") ?: -1L
                ExerciseEditRoute(
                    exerciseId = id,
                    alternativeId = alternativeId,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.WORKOUT) { entry ->
                val id = entry.arguments?.getString("workoutId")?.toLongOrNull()
                WorkoutEditorRoute(
                    workoutId = id,
                    nav = nav,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.PICK) {
                WorkoutPickerRoute(
                    onBack = { nav.popBackStack() },
                    onPick = { workoutId ->
                        nav.navigate(Routes.session(workoutId))
                    },
                )
            }
            composable(
                route = Routes.SESSION,
                arguments = listOf(
                    navArgument("makeup") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("workoutId")?.toLongOrNull()
                val makeup = entry.arguments?.getString("makeup")?.let { raw ->
                    runCatching { LocalDate.parse(raw) }.getOrNull()
                }
                SessionRoute(
                    workoutId = id,
                    makesUpForDate = makeup,
                    onFinished = {
                        nav.popBackStack(Routes.TRAIN, inclusive = false)
                    },
                    onEditExercise = { exerciseId, alternativeId ->
                        nav.navigate(Routes.exerciseEdit(exerciseId, alternativeId))
                    },
                )
            }
            composable(Routes.DAY) { entry ->
                val date = entry.arguments?.getString("date").orEmpty()
                SessionDetailRoute(date = date, onBack = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}

private fun androidx.navigation.NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
