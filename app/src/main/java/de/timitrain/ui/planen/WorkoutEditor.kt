package de.timitrain.ui.planen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import de.timitrain.R
import de.timitrain.data.TimiTrainRepository
import de.timitrain.data.db.ExerciseEntity
import de.timitrain.data.sortedGraph
import de.timitrain.data.toValues
import de.timitrain.domain.ExerciseType
import de.timitrain.domain.GermanNumbers
import de.timitrain.domain.InputProblem
import de.timitrain.domain.SetValues
import de.timitrain.domain.toExerciseType
import de.timitrain.domain.validatePlanned
import de.timitrain.ui.Routes
import de.timitrain.ui.exerciseTypeLabel
import de.timitrain.ui.problemText
import de.timitrain.ui.rememberApp
import de.timitrain.ui.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class SlotUi(
    val id: Long,
    val alternatives: List<AlternativeUi>,
)

data class AlternativeUi(
    val id: Long,
    val exerciseName: String,
    val type: ExerciseType,
    val restSecondsOverride: Int?,
    val sets: List<SetUi>,
)

data class SetUi(
    val id: Long,
    val position: Int,
    val values: SetValues,
)

data class SetDialog(
    val alternativeId: Long,
    val setId: Long?,
    val type: ExerciseType,
    val reps: String = "",
    val weight: String = "",
    val seconds: String = "",
    val minutes: String = "",
    val distance: String = "",
    val error: InputProblem? = null,
)

data class EditorUi(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val name: String = "",
    val slots: List<SlotUi> = emptyList(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val pickerForNewSlot: Boolean = false,
    val pickerSlotId: Long? = null,
    val setDialog: SetDialog? = null,
    val error: InputProblem? = null,
    val deleteDays: List<Int>? = null,
)

@Composable
fun WorkoutEditorRoute(
    workoutId: Long?,
    nav: NavHostController,
    onBack: () -> Unit,
) {
    val app = rememberApp()
    if (workoutId == null) {
        onBack()
        return
    }
    val vm: WorkoutEditorViewModel = viewModel(
        key = "workout-$workoutId",
        factory = viewModelFactory { WorkoutEditorViewModel(app.repository, workoutId) },
    )
    val state by vm.state.collectAsState()
    val entry = nav.currentBackStackEntry
    LaunchedEffect(entry) {
        val handle = entry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow("createdExerciseId", 0L).collect { id ->
            if (id != 0L) {
                vm.onExerciseCreated(id)
                handle["createdExerciseId"] = 0L
            }
        }
    }
    WorkoutEditorScreen(
        state = state,
        vm = vm,
        onBack = onBack,
        onCreateExercise = {
            vm.prepareCreateExercise()
            nav.navigate(Routes.EXERCISE_NEW)
        },
    )
}

class WorkoutEditorViewModel(
    private val repository: TimiTrainRepository,
    private val workoutId: Long,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorUi())
    val state: StateFlow<EditorUi> = _state
    private var pendingNewSlot = false
    private var pendingSlotId: Long? = null

    init {
        reload()
    }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.value = _state.value.copy(error = InputProblem.NAME)
            return
        }
        viewModelScope.launch {
            repository.renameWorkout(workoutId, trimmed)
            _state.value = _state.value.copy(error = null, name = trimmed)
        }
    }

    fun reorderSlots(orderedIds: List<Long>) {
        viewModelScope.launch { repository.reorderSlots(workoutId, orderedIds) }
    }

    fun reorderAlternatives(slotId: Long, orderedIds: List<Long>) {
        viewModelScope.launch { repository.reorderAlternatives(slotId, orderedIds) }
    }

    fun openNewSlotPicker() {
        pendingNewSlot = true
        pendingSlotId = null
        _state.value = _state.value.copy(pickerForNewSlot = true, pickerSlotId = null)
    }

    fun openAlternativePicker(slotId: Long) {
        pendingNewSlot = false
        pendingSlotId = slotId
        _state.value = _state.value.copy(pickerForNewSlot = false, pickerSlotId = slotId)
    }

    fun dismissPicker() {
        pendingNewSlot = false
        pendingSlotId = null
        _state.value = _state.value.copy(pickerForNewSlot = false, pickerSlotId = null)
    }

    fun prepareCreateExercise() {
        _state.value = _state.value.copy(pickerForNewSlot = false, pickerSlotId = null)
    }

    fun pickExercise(exerciseId: Long) {
        viewModelScope.launch {
            if (pendingNewSlot) repository.addSlot(workoutId, exerciseId)
            else pendingSlotId?.let { repository.addAlternative(it, exerciseId) }
            pendingNewSlot = false
            pendingSlotId = null
            _state.value = _state.value.copy(pickerForNewSlot = false, pickerSlotId = null)
            reload()
        }
    }

    fun onExerciseCreated(exerciseId: Long) {
        if (!pendingNewSlot && pendingSlotId == null) return
        pickExercise(exerciseId)
    }

    fun removeSlot(slotId: Long) {
        viewModelScope.launch {
            repository.removeSlot(slotId)
            reload()
        }
    }

    fun removeAlternative(alternativeId: Long) {
        viewModelScope.launch {
            repository.removeAlternative(alternativeId)
            reload()
        }
    }

    fun setRest(alternativeId: Long, seconds: Int?) {
        viewModelScope.launch {
            repository.setRestOverride(alternativeId, seconds)
            reload()
        }
    }

    fun openNewSet(alternative: AlternativeUi) {
        _state.value = _state.value.copy(
            setDialog = SetDialog(alternativeId = alternative.id, setId = null, type = alternative.type),
            error = null,
        )
    }

    fun openSet(alternative: AlternativeUi, set: SetUi) {
        _state.value = _state.value.copy(
            setDialog = SetDialog(
                alternativeId = alternative.id,
                setId = set.id,
                type = alternative.type,
                reps = set.values.reps?.toString().orEmpty(),
                weight = set.values.weightKg?.let(GermanNumbers::format).orEmpty(),
                seconds = set.values.durationSec?.toString().orEmpty(),
                minutes = set.values.durationMin?.toString().orEmpty(),
                distance = set.values.distanceKm?.let(GermanNumbers::format).orEmpty(),
            ),
        )
    }

    fun editDialog(dialog: SetDialog) {
        _state.value = _state.value.copy(setDialog = dialog)
    }

    fun dismissSet() {
        _state.value = _state.value.copy(setDialog = null)
    }

    fun saveSet() {
        val dialog = _state.value.setDialog ?: return
        val values = dialog.toValues() ?: run {
            _state.value = _state.value.copy(setDialog = dialog.copy(error = InputProblem.NUMBER))
            return
        }
        val problem = validatePlanned(dialog.type, values)
        if (problem != null) {
            _state.value = _state.value.copy(setDialog = dialog.copy(error = problem))
            return
        }
        viewModelScope.launch {
            val id = dialog.setId
            if (id == null) repository.addPlannedSet(dialog.alternativeId, values)
            else repository.updatePlannedSet(id, values)
            _state.value = _state.value.copy(setDialog = null)
            reload()
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            repository.deletePlannedSet(setId)
            _state.value = _state.value.copy(setDialog = null)
            reload()
        }
    }

    fun requestDelete() {
        viewModelScope.launch {
            _state.value = _state.value.copy(deleteDays = repository.weekdaysUsing(workoutId))
        }
    }

    fun dismissDelete() {
        _state.value = _state.value.copy(deleteDays = null)
    }

    fun confirmDelete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteWorkout(workoutId)
            onDeleted()
        }
    }

    fun duplicate(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            repository.duplicateWorkout(workoutId)?.let(onCreated)
        }
    }

    private fun reload() {
        viewModelScope.launch {
            val graph = repository.loadWorkout(workoutId)?.sortedGraph()
            if (graph == null) {
                _state.value = _state.value.copy(loading = false, missing = true)
                return@launch
            }
            val exerciseList = repository.observeExercises().first()
            _state.value = _state.value.copy(
                loading = false,
                missing = false,
                name = graph.workout.name,
                slots = graph.slots.map { slot ->
                    SlotUi(
                        id = slot.slot.id,
                        alternatives = slot.alternatives.map { alternative ->
                            AlternativeUi(
                                id = alternative.alternative.id,
                                exerciseName = alternative.exercise.name,
                                type = alternative.exercise.type.toExerciseType(),
                                restSecondsOverride = alternative.alternative.restSecondsOverride,
                                sets = alternative.sets.map { set ->
                                    SetUi(set.id, set.position, set.toValues())
                                },
                            )
                        },
                    )
                },
                exercises = exerciseList,
            )
        }
    }
}

private fun SetDialog.toValues(): SetValues? {
    val reps = parseInt(reps) ?: return null
    val weight = parseDecimal(weight) ?: return null
    val seconds = parseInt(seconds) ?: return null
    val minutes = parseInt(minutes) ?: return null
    val distance = parseDecimal(distance) ?: return null
    return SetValues(
        reps = reps.value,
        weightKg = weight.value,
        durationSec = seconds.value,
        durationMin = minutes.value,
        distanceKm = distance.value,
    )
}

private data class Parsed<T>(val value: T?)

private fun parseInt(text: String): Parsed<Int>? {
    if (text.isBlank()) return Parsed(null)
    val value = text.trim().toIntOrNull() ?: return null
    return Parsed(value)
}

private fun parseDecimal(text: String): Parsed<Double>? {
    if (text.isBlank()) return Parsed(null)
    val value = GermanNumbers.parse(text) ?: return null
    return Parsed(value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutEditorScreen(
    state: EditorUi,
    vm: WorkoutEditorViewModel,
    onBack: () -> Unit,
    onCreateExercise: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name.ifBlank { stringResource(R.string.workouts) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { vm.duplicate { } }) { Text(stringResource(R.string.duplicate)) }
                    TextButton(onClick = vm::requestDelete) { Text(stringResource(R.string.delete)) }
                },
            )
        },
    ) { padding ->
        if (state.missing) {
            Text(stringResource(R.string.plan_empty), modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        var slots by remember(state.slots) { mutableStateOf(state.slots) }
        val lazyListState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
            slots = slots.toMutableList().apply {
                if (from.index in indices && to.index in indices) {
                    add(to.index, removeAt(from.index))
                }
            }
            vm.reorderSlots(slots.map { it.id })
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            var name by remember(state.name) { mutableStateOf(state.name) }
            var focused by remember { mutableStateOf(false) }
            LaunchedEffect(state.name, focused) {
                if (!focused) name = state.name
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .onFocusChanged {
                        val wasFocused = focused
                        focused = it.isFocused
                        if (wasFocused && !it.isFocused) vm.rename(name)
                    },
                singleLine = true,
            )
            state.error?.let {
                Text(problemText(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(slots, key = { it.id }) { slot ->
                    ReorderableItem(reorderState, key = slot.id) { _ ->
                        SlotCard(
                            slot = slot,
                            index = slots.indexOfFirst { it.id == slot.id } + 1,
                            dragHandle = Modifier.draggableHandle(),
                            onRemove = { vm.removeSlot(slot.id) },
                            onReorderAlternatives = { ids -> vm.reorderAlternatives(slot.id, ids) },
                            onAddAlternative = { vm.openAlternativePicker(slot.id) },
                            onRemoveAlternative = vm::removeAlternative,
                            onRest = vm::setRest,
                            onAddSet = vm::openNewSet,
                            onEditSet = vm::openSet,
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = vm::openNewSlotPicker,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text(stringResource(R.string.add_slot)) }
        }
    }
    if (state.pickerForNewSlot || state.pickerSlotId != null) {
        AlertDialog(
            onDismissRequest = vm::dismissPicker,
            title = { Text(stringResource(R.string.pick_exercise)) },
            text = {
                Column {
                    if (state.exercises.isEmpty()) Text(stringResource(R.string.no_exercises))
                    state.exercises.forEach { exercise ->
                        TextButton(onClick = { vm.pickExercise(exercise.id) }) {
                            Text("${exercise.name} · ${exerciseTypeLabel(exercise.type.toExerciseType())}")
                        }
                    }
                    TextButton(onClick = onCreateExercise) { Text(stringResource(R.string.new_exercise)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = vm::dismissPicker) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    state.setDialog?.let { dialog ->
        SetEditorDialog(dialog = dialog, onChange = vm::editDialog, onSave = vm::saveSet, onDelete = {
            dialog.setId?.let(vm::deleteSet)
        }, onDismiss = vm::dismissSet)
    }
    val deleteDays = state.deleteDays
    if (deleteDays != null) {
        val name = state.name
        val dayNames = listOf(
            stringResource(R.string.weekday_mon),
            stringResource(R.string.weekday_tue),
            stringResource(R.string.weekday_wed),
            stringResource(R.string.weekday_thu),
            stringResource(R.string.weekday_fri),
            stringResource(R.string.weekday_sat),
            stringResource(R.string.weekday_sun),
        )
        val dayText = deleteDays.joinToString(", ") { day -> dayNames.getOrElse(day - 1) { day.toString() } }
        AlertDialog(
            onDismissRequest = vm::dismissDelete,
            title = { Text(stringResource(R.string.delete_workout_title)) },
            text = {
                Text(
                    if (deleteDays.isEmpty()) stringResource(R.string.delete_workout_body, name)
                    else stringResource(R.string.delete_workout_plan_body, name, dayText),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmDelete(onBack) }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDelete) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SlotCard(
    slot: SlotUi,
    index: Int,
    dragHandle: Modifier,
    onRemove: () -> Unit,
    onReorderAlternatives: (List<Long>) -> Unit,
    onAddAlternative: () -> Unit,
    onRemoveAlternative: (Long) -> Unit,
    onRest: (Long, Int?) -> Unit,
    onAddSet: (AlternativeUi) -> Unit,
    onEditSet: (AlternativeUi, SetUi) -> Unit,
) {
    var alternatives by remember(slot.alternatives) { mutableStateOf(slot.alternatives) }
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        alternatives = alternatives.toMutableList().apply {
            if (from.index in indices && to.index in indices) {
                add(to.index, removeAt(from.index))
            }
        }
        onReorderAlternatives(alternatives.map { it.id })
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DragHandle, contentDescription = stringResource(R.string.drag_handle), modifier = dragHandle)
                Text(stringResource(R.string.slot_label, index), modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove_slot))
                }
            }
            val lines = alternatives.sumOf { 4 + it.sets.size }.coerceAtLeast(1)
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.height(lines.times(64).dp),
            ) {
                items(alternatives, key = { it.id }) { alternative ->
                    ReorderableItem(reorderState, key = alternative.id) { _ ->
                        AlternativeBlock(
                            alternative = alternative,
                            dragHandle = Modifier.draggableHandle(),
                            onRemove = { onRemoveAlternative(alternative.id) },
                            onRest = { seconds -> onRest(alternative.id, seconds) },
                            onAddSet = { onAddSet(alternative) },
                            onEditSet = { set -> onEditSet(alternative, set) },
                        )
                    }
                }
            }
            TextButton(onClick = onAddAlternative) { Text(stringResource(R.string.add_alternative)) }
        }
    }
}

@Composable
private fun AlternativeBlock(
    alternative: AlternativeUi,
    dragHandle: Modifier,
    onRemove: () -> Unit,
    onRest: (Int?) -> Unit,
    onAddSet: () -> Unit,
    onEditSet: (SetUi) -> Unit,
) {
    var ownRest by remember(alternative.restSecondsOverride) { mutableStateOf(alternative.restSecondsOverride != null) }
    var restText by remember(alternative.restSecondsOverride) {
        mutableStateOf(alternative.restSecondsOverride?.toString().orEmpty())
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DragHandle, contentDescription = stringResource(R.string.drag_handle), modifier = dragHandle)
            Column(Modifier.weight(1f)) {
                Text(alternative.exerciseName, style = MaterialTheme.typography.titleMedium)
                Text(exerciseTypeLabel(alternative.type), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove_alternative))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rest_override), modifier = Modifier.weight(1f))
            Switch(
                checked = ownRest,
                onCheckedChange = { checked ->
                    ownRest = checked
                    if (!checked) onRest(null)
                    else restText.toIntOrNull()?.let { if (it >= 0) onRest(it) }
                },
            )
        }
        if (ownRest) {
            OutlinedTextField(
                value = restText,
                onValueChange = { restText = it },
                label = { Text(stringResource(R.string.rest_seconds)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                    if (!focus.isFocused) {
                        val seconds = restText.toIntOrNull()
                        if (seconds != null && seconds >= 0) onRest(seconds)
                    }
                },
            )
        }
        alternative.sets.forEach { set ->
            ListItem(
                headlineContent = { Text(stringResource(R.string.set_label, set.position + 1)) },
                supportingContent = { Text(GermanNumbers.formatSet(set.values, alternative.type)) },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    TextButton(onClick = { onEditSet(set) }) { Text(stringResource(R.string.edit)) }
                },
            )
        }
        TextButton(onClick = onAddSet) { Text(stringResource(R.string.add_set)) }
    }
}

@Composable
private fun SetEditorDialog(
    dialog: SetDialog,
    onChange: (SetDialog) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (dialog.setId == null) stringResource(R.string.add_set) else stringResource(R.string.edit_set),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dialog.type == ExerciseType.STRENGTH || dialog.type == ExerciseType.BODYWEIGHT) {
                    OutlinedTextField(
                        value = dialog.reps,
                        onValueChange = { onChange(dialog.copy(reps = it, error = null)) },
                        label = { Text(stringResource(R.string.reps)) },
                        singleLine = true,
                    )
                }
                if (dialog.type == ExerciseType.STRENGTH) {
                    OutlinedTextField(
                        value = dialog.weight,
                        onValueChange = { onChange(dialog.copy(weight = it, error = null)) },
                        label = { Text(stringResource(R.string.weight_kg)) },
                        singleLine = true,
                    )
                }
                if (dialog.type == ExerciseType.TIMED) {
                    OutlinedTextField(
                        value = dialog.seconds,
                        onValueChange = { onChange(dialog.copy(seconds = it, error = null)) },
                        label = { Text(stringResource(R.string.duration_sec)) },
                        singleLine = true,
                    )
                }
                if (dialog.type == ExerciseType.CARDIO) {
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
                if (dialog.setId != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.remove_set)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
