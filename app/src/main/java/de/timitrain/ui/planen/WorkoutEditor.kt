package de.timitrain.ui.planen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
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
import de.timitrain.domain.CategorizedSlot
import de.timitrain.domain.ExerciseCategory
import de.timitrain.domain.ExerciseType
import de.timitrain.domain.GermanNumbers
import de.timitrain.domain.InputProblem
import de.timitrain.domain.SetValues
import de.timitrain.domain.WorkoutSections
import de.timitrain.domain.toExerciseCategory
import de.timitrain.domain.toExerciseType
import de.timitrain.domain.validatePlanned
import de.timitrain.ui.Routes
import de.timitrain.ui.categoryLabel
import de.timitrain.ui.exerciseTypeLabel
import de.timitrain.ui.markColor
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
    val position: Int,
    val category: ExerciseCategory,
    val title: String,
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

    fun reorderWithin(positions: Map<Long, Int>) {
        viewModelScope.launch { repository.reorderSlotPositions(positions) }
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
                    val primary = slot.alternatives.firstOrNull()
                    SlotUi(
                        id = slot.slot.id,
                        position = slot.slot.position,
                        category = primary?.exercise?.category?.toExerciseCategory() ?: ExerciseCategory.STRENGTH,
                        title = primary?.exercise?.name.orEmpty(),
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
                    IconButton(onClick = vm::openNewSlotPicker) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_exercise))
                    }
                },
            )
        },
    ) { padding ->
        if (state.missing) {
            Text(stringResource(R.string.plan_empty), modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        var grouped by remember(state.slots) {
            mutableStateOf(state.slots.groupedByCategory())
        }
        var expanded by remember { mutableStateOf(setOf<Long>()) }
        val lazyListState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
            val toId = to.key as? Long ?: return@rememberReorderableLazyListState
            val fromSlot = grouped.values.flatten().find { it.id == fromId } ?: return@rememberReorderableLazyListState
            val toSlot = grouped.values.flatten().find { it.id == toId } ?: return@rememberReorderableLazyListState
            if (fromSlot.category != toSlot.category) return@rememberReorderableLazyListState
            val section = grouped.getValue(fromSlot.category).toMutableList()
            val fromIndex = section.indexOfFirst { it.id == fromId }
            val toIndex = section.indexOfFirst { it.id == toId }
            if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return@rememberReorderableLazyListState
            section.add(toIndex, section.removeAt(fromIndex))
            val catalog = grouped.values.flatten().map { CategorizedSlot(it.id, it.position, it.category) }
            val updates = WorkoutSections.positionsAfterReorder(catalog, fromSlot.category, section.map { it.id })
            val placed = section.map { slot -> slot.copy(position = updates.getValue(slot.id)) }
            grouped = grouped + (fromSlot.category to placed)
            expanded = emptySet()
            vm.reorderWithin(updates)
        }
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            ExerciseCategory.entries.forEach { category ->
                item(key = "section-$category") {
                    SectionHeader(category)
                }
                items(grouped.getValue(category), key = { it.id }) { slot ->
                    ReorderableItem(reorderState, key = slot.id) { _ ->
                        SlotCard(
                            slot = slot,
                            expanded = slot.id in expanded,
                            dragHandle = Modifier.draggableHandle(
                                onDragStarted = { expanded = emptySet() },
                            ),
                            onToggle = {
                                expanded = if (slot.id in expanded) expanded - slot.id else expanded + slot.id
                            },
                            onRemove = { vm.removeSlot(slot.id) },
                            onAddAlternative = { vm.openAlternativePicker(slot.id) },
                            onRemoveAlternative = vm::removeAlternative,
                            onRest = vm::setRest,
                            onAddSet = vm::openNewSet,
                            onEditSet = vm::openSet,
                        )
                    }
                }
            }
        }
    }
    if (state.pickerForNewSlot || state.pickerSlotId != null) {
        AlertDialog(
            onDismissRequest = vm::dismissPicker,
            title = {
                Text(
                    stringResource(
                        if (state.pickerForNewSlot) R.string.add_exercise else R.string.pick_exercise,
                    ),
                )
            },
            text = {
                Column {
                    if (state.exercises.isEmpty()) Text(stringResource(R.string.no_exercises))
                    state.exercises.forEach { exercise ->
                        TextButton(onClick = { vm.pickExercise(exercise.id) }) {
                            Text(exercise.name)
                            val category = exercise.category.toExerciseCategory()
                            Text(categoryLabel(category), color = category.markColor())
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
}

private fun List<SlotUi>.groupedByCategory(): Map<ExerciseCategory, List<SlotUi>> {
    return ExerciseCategory.entries.associateWith { category ->
        filter { it.category == category }.sortedBy { it.position }
    }
}

@Composable
private fun SectionHeader(category: ExerciseCategory) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(8.dp)
                .height(32.dp)
                .background(category.markColor(), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
        )
        Text(categoryLabel(category), style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun SlotCard(
    slot: SlotUi,
    expanded: Boolean,
    dragHandle: Modifier,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onAddAlternative: () -> Unit,
    onRemoveAlternative: (Long) -> Unit,
    onRest: (Long, Int?) -> Unit,
    onAddSet: (AlternativeUi) -> Unit,
    onEditSet: (AlternativeUi, SetUi) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggle, modifier = dragHandle) {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = stringResource(R.string.expand_slot),
                    )
                }
                Text(slot.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove_slot))
                }
            }
            if (expanded) {
                slot.alternatives.forEach { alternative ->
                    AlternativeBlock(
                        alternative = alternative,
                        onRemove = { onRemoveAlternative(alternative.id) },
                        onRest = { seconds -> onRest(alternative.id, seconds) },
                        onAddSet = { onAddSet(alternative) },
                        onEditSet = { set -> onEditSet(alternative, set) },
                    )
                }
                TextButton(onClick = onAddAlternative) { Text(stringResource(R.string.add_alternative)) }
            }
        }
    }
}

@Composable
private fun AlternativeBlock(
    alternative: AlternativeUi,
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
