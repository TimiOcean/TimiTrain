package de.timitrain.ui.planen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import de.timitrain.R
import de.timitrain.data.db.ExerciseEntity
import de.timitrain.data.db.WeekDayRow
import de.timitrain.data.db.WorkoutEntity
import de.timitrain.domain.ExerciseType
import de.timitrain.domain.GermanNumbers
import de.timitrain.domain.Increments
import de.timitrain.domain.InputProblem
import de.timitrain.domain.SetValues
import de.timitrain.domain.defaultIncrements
import de.timitrain.domain.toExerciseType
import de.timitrain.domain.validateIncrements
import de.timitrain.domain.validatePlanned
import de.timitrain.ui.Routes
import de.timitrain.ui.exerciseTypeLabel
import de.timitrain.ui.problemText
import de.timitrain.ui.rememberApp
import de.timitrain.ui.viewModelFactory
import de.timitrain.ui.weekdayLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun PlanenRoute(nav: NavHostController) {
    val app = rememberApp()
    val vm: PlanenViewModel = viewModel(factory = viewModelFactory { PlanenViewModel(app.repository) })
    val workouts by vm.workouts.collectAsState()
    val dialog by vm.dialog.collectAsState()
    PlanenScreen(
        workouts = workouts,
        dialog = dialog,
        onWeek = { nav.navigate(Routes.WEEK) },
        onExercises = { nav.navigate(Routes.EXERCISES) },
        onOpen = { nav.navigate(Routes.workout(it)) },
        onCreate = vm::create,
        onDuplicate = vm::duplicate,
        onRequestDelete = vm::requestDelete,
        onConfirmDelete = vm::confirmDelete,
        onDismissDelete = vm::dismissDelete,
    )
}

class PlanenViewModel(
    private val repository: de.timitrain.data.TimiTrainRepository,
) : ViewModel() {
    val workouts: StateFlow<List<WorkoutEntity>> = repository.observeWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _dialog = kotlinx.coroutines.flow.MutableStateFlow<DeleteWorkout?>(null)
    val dialog: StateFlow<DeleteWorkout?> = _dialog

    fun create(name: String, onCreated: (Long) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            onCreated(repository.createWorkout(trimmed))
        }
    }

    fun duplicate(id: Long) {
        viewModelScope.launch { repository.duplicateWorkout(id) }
    }

    fun requestDelete(workout: WorkoutEntity) {
        viewModelScope.launch {
            val days = repository.weekdaysUsing(workout.id)
            _dialog.value = DeleteWorkout(workout.id, workout.name, days)
        }
    }

    fun dismissDelete() {
        _dialog.value = null
    }

    fun confirmDelete() {
        val pending = _dialog.value ?: return
        viewModelScope.launch {
            repository.deleteWorkout(pending.id)
            _dialog.value = null
        }
    }
}

data class DeleteWorkout(
    val id: Long,
    val name: String,
    val weekdays: List<Int>,
)

@Composable
private fun PlanenScreen(
    workouts: List<WorkoutEntity>,
    dialog: DeleteWorkout?,
    onWeek: () -> Unit,
    onExercises: () -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: (String, (Long) -> Unit) -> Unit,
    onDuplicate: (Long) -> Unit,
    onRequestDelete: (WorkoutEntity) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    var askName by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onWeek) { Text(stringResource(R.string.week_plan)) }
                    OutlinedButton(onClick = onExercises) { Text(stringResource(R.string.exercises)) }
                }
            }
            item {
                Text(stringResource(R.string.workouts), style = MaterialTheme.typography.titleMedium)
            }
            if (workouts.isEmpty()) {
                item {
                    Text(stringResource(R.string.plan_empty))
                    Text(stringResource(R.string.plan_empty_hint))
                }
            }
            items(workouts, key = { it.id }) { workout ->
                WorkoutRow(
                    workout = workout,
                    onOpen = { onOpen(workout.id) },
                    onDuplicate = { onDuplicate(workout.id) },
                    onDelete = { onRequestDelete(workout) },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { askName = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.new_workout)) },
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
    if (askName) {
        AlertDialog(
            onDismissRequest = { askName = false },
            title = { Text(stringResource(R.string.new_workout)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreate(name) { id ->
                            askName = false
                            name = ""
                            onOpen(id)
                        }
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { askName = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (dialog != null) {
        val dayNames = listOf(
            stringResource(R.string.weekday_mon),
            stringResource(R.string.weekday_tue),
            stringResource(R.string.weekday_wed),
            stringResource(R.string.weekday_thu),
            stringResource(R.string.weekday_fri),
            stringResource(R.string.weekday_sat),
            stringResource(R.string.weekday_sun),
        )
        val days = dialog.weekdays.joinToString(", ") { day -> dayNames.getOrElse(day - 1) { day.toString() } }
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.delete_workout_title)) },
            text = {
                Text(
                    if (dialog.weekdays.isEmpty()) {
                        stringResource(R.string.delete_workout_body, dialog.name)
                    } else {
                        stringResource(R.string.delete_workout_plan_body, dialog.name, days)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun WorkoutRow(
    workout: WorkoutEntity,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(workout.name) },
        modifier = Modifier.clickable(onClick = onOpen),
        trailingContent = {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.duplicate)) },
                    onClick = {
                        menu = false
                        onDuplicate()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekPlanRoute(onBack: () -> Unit) {
    val app = rememberApp()
    val vm: WeekPlanViewModel = viewModel(factory = viewModelFactory { WeekPlanViewModel(app.repository) })
    val days by vm.days.collectAsState()
    val workouts by vm.workouts.collectAsState()
    var picking by remember { mutableStateOf<Int?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.week_plan)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(days, key = { it.weekday }) { day ->
                ListItem(
                    headlineContent = { Text(weekdayLabel(day.weekday)) },
                    supportingContent = { Text(day.workoutName ?: stringResource(R.string.rest_day)) },
                    modifier = Modifier.clickable { picking = day.weekday },
                )
            }
        }
    }
    val weekday = picking
    if (weekday != null) {
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(weekdayLabel(weekday)) },
            text = {
                Column {
                    TextButton(onClick = {
                        vm.assign(weekday, null)
                        picking = null
                    }) { Text(stringResource(R.string.rest_day)) }
                    workouts.forEach { workout ->
                        TextButton(onClick = {
                            vm.assign(weekday, workout.id)
                            picking = null
                        }) { Text(workout.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { picking = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

class WeekPlanViewModel(
    private val repository: de.timitrain.data.TimiTrainRepository,
) : ViewModel() {
    val days: StateFlow<List<WeekDayRow>> = repository.observeWeek()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workouts: StateFlow<List<WorkoutEntity>> = repository.observeWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun assign(weekday: Int, workoutId: Long?) {
        viewModelScope.launch { repository.setWeekday(weekday, workoutId) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseListRoute(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val app = rememberApp()
    val vm: ExerciseListViewModel = viewModel(factory = viewModelFactory { ExerciseListViewModel(app.repository) })
    val exercises by vm.exercises.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exercises)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_exercise)) },
            )
        },
    ) { padding ->
        if (exercises.isEmpty()) {
            Text(
                stringResource(R.string.no_exercises),
                modifier = Modifier.padding(padding).padding(16.dp),
            )
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(exercises, key = { it.id }) { exercise ->
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = { Text(exerciseTypeLabel(exercise.type.toExerciseType())) },
                        modifier = Modifier.clickable { onEdit(exercise.id) },
                    )
                }
            }
        }
    }
}

class ExerciseListViewModel(
    repository: de.timitrain.data.TimiTrainRepository,
) : ViewModel() {
    val exercises: StateFlow<List<ExerciseEntity>> = repository.observeExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseCreateRoute(onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val app = rememberApp()
    val vm: ExerciseCreateViewModel = viewModel(
        factory = viewModelFactory { ExerciseCreateViewModel(app.repository) },
    )
    val error by vm.error.collectAsState()
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ExerciseType.STRENGTH) }
    var increments by remember { mutableStateOf(defaultIncrements(ExerciseType.STRENGTH)) }
    var sets by remember { mutableStateOf(listOf<SetValues>()) }
    var setDialog by remember { mutableStateOf<DefaultSetDialogState?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_exercise)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.exercise_type))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseType.entries.forEach { candidate ->
                    FilterChip(
                        selected = type == candidate,
                        onClick = {
                            type = candidate
                            increments = defaultIncrements(candidate)
                            sets = emptyList()
                            setDialog = null
                        },
                        label = { Text(exerciseTypeLabel(candidate)) },
                    )
                }
            }
            IncrementFields(type = type, increments = increments, onChange = { increments = it })
            DefaultSetList(
                type = type,
                sets = sets,
                onEdit = { index -> setDialog = sets[index].toDialog(index) },
                onAdd = { setDialog = DefaultSetDialogState(index = null) },
            )
            error?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
            OutlinedButton(
                onClick = { vm.save(name, type, increments, sets, onCreated) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
    setDialog?.let { dialog ->
        DefaultSetDialog(
            type = type,
            dialog = dialog,
            onChange = { setDialog = it },
            onDismiss = { setDialog = null },
            onDelete = {
                dialog.index?.let { index -> sets = sets.filterIndexed { i, _ -> i != index } }
                setDialog = null
            },
            onSave = {
                val values = dialog.toValues()
                if (values == null) {
                    setDialog = dialog.copy(error = InputProblem.NUMBER)
                    return@DefaultSetDialog
                }
                val problem = validatePlanned(type, values)
                if (problem != null) {
                    setDialog = dialog.copy(error = problem)
                    return@DefaultSetDialog
                }
                sets = if (dialog.index == null) {
                    sets + values
                } else {
                    sets.toMutableList().also { it[dialog.index] = values }
                }
                setDialog = null
            },
        )
    }
}

class ExerciseCreateViewModel(
    private val repository: de.timitrain.data.TimiTrainRepository,
) : ViewModel() {
    private val _error = kotlinx.coroutines.flow.MutableStateFlow<InputProblem?>(null)
    val error: StateFlow<InputProblem?> = _error

    fun save(
        name: String,
        type: ExerciseType,
        increments: Increments,
        defaultSets: List<SetValues>,
        onDone: (Long) -> Unit,
    ) {
        if (name.isBlank()) {
            _error.value = InputProblem.NAME
            return
        }
        val problem = validateIncrements(type, increments)
        if (problem != null) {
            _error.value = problem
            return
        }
        viewModelScope.launch {
            val id = repository.createExercise(name, type, increments, defaultSets)
            onDone(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditRoute(exerciseId: Long?, onBack: () -> Unit) {
    val app = rememberApp()
    if (exerciseId == null) {
        onBack()
        return
    }
    val vm: ExerciseEditViewModel = viewModel(
        key = "exercise-$exerciseId",
        factory = viewModelFactory { ExerciseEditViewModel(app.repository, exerciseId) },
    )
    val form by vm.form.collectAsState()
    val error by vm.error.collectAsState()
    val current = form
    var increments by remember { mutableStateOf(Increments()) }
    var sets by remember { mutableStateOf(listOf<SetValues>()) }
    var ready by remember { mutableStateOf(false) }
    var setDialog by remember { mutableStateOf<DefaultSetDialogState?>(null) }
    LaunchedEffect(current) {
        val loaded = current ?: return@LaunchedEffect
        if (!ready) {
            increments = loaded.increments
            sets = loaded.sets
            ready = true
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_exercise)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (current == null || !ready) {
            Text(stringResource(R.string.no_exercises), modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            val type = current.type
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(current.name, style = MaterialTheme.typography.headlineSmall)
                Text(exerciseTypeLabel(type))
                Text(stringResource(R.string.increments_fixed_name))
                IncrementFields(type = type, increments = increments, onChange = { increments = it })
                DefaultSetList(
                    type = type,
                    sets = sets,
                    onEdit = { index -> setDialog = sets[index].toDialog(index) },
                    onAdd = { setDialog = DefaultSetDialogState(index = null) },
                )
                error?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
                OutlinedButton(
                    onClick = { vm.save(type, increments, sets, onBack) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
    val type = current?.type
    if (type != null) {
        setDialog?.let { dialog ->
            DefaultSetDialog(
                type = type,
                dialog = dialog,
                onChange = { setDialog = it },
                onDismiss = { setDialog = null },
                onDelete = {
                    dialog.index?.let { index -> sets = sets.filterIndexed { i, _ -> i != index } }
                    setDialog = null
                },
                onSave = {
                    val values = dialog.toValues()
                    if (values == null) {
                        setDialog = dialog.copy(error = InputProblem.NUMBER)
                        return@DefaultSetDialog
                    }
                    val problem = validatePlanned(type, values)
                    if (problem != null) {
                        setDialog = dialog.copy(error = problem)
                        return@DefaultSetDialog
                    }
                    sets = if (dialog.index == null) {
                        sets + values
                    } else {
                        sets.toMutableList().also { it[dialog.index] = values }
                    }
                    setDialog = null
                },
            )
        }
    }
}

data class ExerciseForm(
    val name: String,
    val type: ExerciseType,
    val increments: Increments,
    val sets: List<SetValues>,
)

class ExerciseEditViewModel(
    private val repository: de.timitrain.data.TimiTrainRepository,
    private val exerciseId: Long,
) : ViewModel() {
    private val _form = kotlinx.coroutines.flow.MutableStateFlow<ExerciseForm?>(null)
    val form: StateFlow<ExerciseForm?> = _form
    private val _error = kotlinx.coroutines.flow.MutableStateFlow<InputProblem?>(null)
    val error: StateFlow<InputProblem?> = _error

    init {
        viewModelScope.launch {
            val exercise = repository.exercise(exerciseId) ?: return@launch
            _form.value = ExerciseForm(
                name = exercise.name,
                type = exercise.type.toExerciseType(),
                increments = Increments(
                    weightKg = exercise.weightIncrementKg,
                    durationSec = exercise.durationIncrementSec,
                    distanceKm = exercise.distanceIncrementKm,
                    durationMin = exercise.durationIncrementMin,
                ),
                sets = repository.defaultSets(exerciseId),
            )
        }
    }

    fun save(type: ExerciseType, increments: Increments, defaultSets: List<SetValues>, onDone: () -> Unit) {
        val problem = validateIncrements(type, increments)
        if (problem != null) {
            _error.value = problem
            return
        }
        viewModelScope.launch {
            repository.updateExercise(exerciseId, increments, defaultSets)
            onDone()
        }
    }
}

@Composable
fun IncrementFields(
    type: ExerciseType,
    increments: Increments,
    onChange: (Increments) -> Unit,
) {
    if (type == ExerciseType.STRENGTH) {
        DecimalField(
            label = stringResource(R.string.weight_step),
            value = increments.weightKg,
            resetKey = type,
            onValue = { onChange(increments.copy(weightKg = it)) },
        )
    }
    if (type == ExerciseType.TIMED) {
        IntField(
            label = stringResource(R.string.seconds_step),
            value = increments.durationSec,
            resetKey = type,
            onValue = { onChange(increments.copy(durationSec = it)) },
        )
    }
    if (type == ExerciseType.CARDIO) {
        DecimalField(
            label = stringResource(R.string.distance_step),
            value = increments.distanceKm,
            resetKey = type,
            onValue = { onChange(increments.copy(distanceKm = it)) },
        )
        IntField(
            label = stringResource(R.string.minutes_step),
            value = increments.durationMin,
            resetKey = type,
            onValue = { onChange(increments.copy(durationMin = it)) },
        )
    }
}

@Composable
private fun IntField(label: String, value: Int?, resetKey: Any, onValue: (Int?) -> Unit) {
    var text by remember(label, resetKey) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            onValue(raw.toIntOrNull())
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DecimalField(label: String, value: Double?, resetKey: Any, onValue: (Double?) -> Unit) {
    var text by remember(label, resetKey) { mutableStateOf(value?.let { de.timitrain.domain.GermanNumbers.format(it) }.orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            onValue(if (raw.isBlank()) null else de.timitrain.domain.GermanNumbers.parse(raw))
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DefaultSetList(
    type: ExerciseType,
    sets: List<SetValues>,
    onEdit: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    Text(stringResource(R.string.default_sets), style = MaterialTheme.typography.titleMedium)
    if (sets.isEmpty()) {
        Text(stringResource(R.string.no_default_sets))
    }
    sets.forEachIndexed { index, values ->
        ListItem(
            headlineContent = { Text(stringResource(R.string.set_label, index + 1)) },
            supportingContent = { Text(GermanNumbers.formatSet(values, type)) },
            trailingContent = {
                TextButton(onClick = { onEdit(index) }) { Text(stringResource(R.string.edit)) }
            },
        )
    }
    TextButton(onClick = onAdd) { Text(stringResource(R.string.add_set)) }
}

private data class DefaultSetDialogState(
    val index: Int?,
    val reps: String = "",
    val weight: String = "",
    val seconds: String = "",
    val minutes: String = "",
    val distance: String = "",
    val error: InputProblem? = null,
)

private fun SetValues.toDialog(index: Int): DefaultSetDialogState {
    return DefaultSetDialogState(
        index = index,
        reps = reps?.toString().orEmpty(),
        weight = weightKg?.let(GermanNumbers::format).orEmpty(),
        seconds = durationSec?.toString().orEmpty(),
        minutes = durationMin?.toString().orEmpty(),
        distance = distanceKm?.let(GermanNumbers::format).orEmpty(),
    )
}

private fun DefaultSetDialogState.toValues(): SetValues? {
    val reps = draftWhole(reps) ?: return null
    val weight = draftDecimal(weight) ?: return null
    val seconds = draftWhole(seconds) ?: return null
    val minutes = draftWhole(minutes) ?: return null
    val distance = draftDecimal(distance) ?: return null
    return SetValues(
        reps = reps.value,
        weightKg = weight.value,
        durationSec = seconds.value,
        durationMin = minutes.value,
        distanceKm = distance.value,
    )
}

private data class DraftNumber<T>(val value: T?)

private fun draftWhole(text: String): DraftNumber<Int>? {
    if (text.isBlank()) return DraftNumber(null)
    val value = text.trim().toIntOrNull() ?: return null
    return DraftNumber(value)
}

private fun draftDecimal(text: String): DraftNumber<Double>? {
    if (text.isBlank()) return DraftNumber(null)
    val value = GermanNumbers.parse(text) ?: return null
    return DraftNumber(value)
}

@Composable
private fun DefaultSetDialog(
    type: ExerciseType,
    dialog: DefaultSetDialogState,
    onChange: (DefaultSetDialogState) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (dialog.index == null) stringResource(R.string.add_set) else stringResource(R.string.edit_set))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (type == ExerciseType.STRENGTH || type == ExerciseType.BODYWEIGHT) {
                    OutlinedTextField(
                        value = dialog.reps,
                        onValueChange = { onChange(dialog.copy(reps = it, error = null)) },
                        label = { Text(stringResource(R.string.reps)) },
                        singleLine = true,
                    )
                }
                if (type == ExerciseType.STRENGTH) {
                    OutlinedTextField(
                        value = dialog.weight,
                        onValueChange = { onChange(dialog.copy(weight = it, error = null)) },
                        label = { Text(stringResource(R.string.weight_kg)) },
                        singleLine = true,
                    )
                }
                if (type == ExerciseType.TIMED) {
                    OutlinedTextField(
                        value = dialog.seconds,
                        onValueChange = { onChange(dialog.copy(seconds = it, error = null)) },
                        label = { Text(stringResource(R.string.duration_sec)) },
                        singleLine = true,
                    )
                }
                if (type == ExerciseType.CARDIO) {
                    OutlinedTextField(
                        value = dialog.distance,
                        onValueChange = { onChange(dialog.copy(distance = it, error = null)) },
                        label = { Text(stringResource(R.string.distance_km)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = dialog.minutes,
                        onValueChange = { onChange(dialog.copy(minutes = it, error = null)) },
                        label = { Text(stringResource(R.string.duration_min)) },
                        singleLine = true,
                    )
                }
                dialog.error?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (dialog.index != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.remove_set)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
