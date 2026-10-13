package de.timitrain.ui.train

import android.database.sqlite.SQLiteConstraintException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import de.timitrain.ui.theme.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import de.timitrain.R
import de.timitrain.data.CompletedSetDraft
import de.timitrain.data.SessionDraft
import de.timitrain.data.TimiTrainRepository
import de.timitrain.data.db.WorkoutEntity
import de.timitrain.data.sortedGraph
import de.timitrain.data.toValues
import de.timitrain.domain.AppClock
import de.timitrain.domain.ExerciseType
import de.timitrain.domain.RepsStep
import de.timitrain.domain.GermanNumbers
import de.timitrain.domain.Increments
import de.timitrain.domain.InitialNumbers
import de.timitrain.domain.MakeupTarget
import de.timitrain.domain.Rotation
import de.timitrain.domain.SessionFact
import de.timitrain.domain.SetStepper
import de.timitrain.domain.SetValues
import de.timitrain.domain.StreakRules
import de.timitrain.domain.toExerciseType
import de.timitrain.timer.RestChime
import de.timitrain.timer.RestScheduler
import de.timitrain.ui.rememberApp
import de.timitrain.ui.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class Metric { REPS, WEIGHT, SECONDS, MINUTES, DISTANCE }

data class SessionStep(
    val slotId: Long,
    val alternativeId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val type: ExerciseType,
    val setPosition: Int,
    val setNumber: Int,
    val setCount: Int,
    val plan: SetValues,
    val rotationIndex: Int,
    val lastTime: SetValues?,
    val increments: Increments,
    val restSecondsOverride: Int?,
)

data class MakeupOffer(
    val date: LocalDate,
    val label: String,
    val workoutId: Long,
    val workoutName: String,
)

sealed interface TrainKind {
    data class Rest(val makeups: List<MakeupOffer> = emptyList()) : TrainKind
    data class Planned(val workoutId: Long, val name: String, val preview: List<String>) : TrainKind
    data class Done(val workoutName: String) : TrainKind
}

data class TrainUi(
    val loading: Boolean = true,
    val dateLabel: String = "",
    val kind: TrainKind = TrainKind.Rest(),
)

@Composable
fun TrainRoute(nav: NavHostController) {
    val app = rememberApp()
    val vm: TrainViewModel = viewModel(
        factory = viewModelFactory { TrainViewModel(app.repository, app.clock) },
    )
    val state by vm.state.collectAsState()
    TrainScreen(
        state = state,
        onStart = { id -> nav.navigate(de.timitrain.ui.Routes.session(id)) },
        onMakeup = { id, date -> nav.navigate(de.timitrain.ui.Routes.session(id, date.toString())) },
        onOther = { nav.navigate(de.timitrain.ui.Routes.PICK) },
    )
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TrainViewModel(
    repository: TimiTrainRepository,
    private val clock: AppClock,
) : ViewModel() {
    val state: StateFlow<TrainUi> = combine(
        repository.observeWeek(),
        repository.observeSessions(),
    ) { week, sessions -> week to sessions }
        .flatMapLatest { (week, sessions) ->
            val today = clock.today()
            val label = today.format(DATE)
            val existing = sessions.find { it.date == today.toString() }
            if (existing != null) {
                flowOf(TrainUi(loading = false, dateLabel = label, kind = TrainKind.Done(existing.workoutName)))
            } else {
                val day = week.find { it.weekday == today.dayOfWeek.value }
                val workoutId = day?.workoutId
                if (workoutId == null) {
                    val byWeekday = week.associate { it.weekday to it.workoutId }
                    val names = week.associate { it.weekday to it.workoutName }
                    val plannedDays = (1..7).mapNotNull { age ->
                        val missed = today.minusDays(age.toLong())
                        if (byWeekday[missed.dayOfWeek.value] != null) missed else null
                    }.toSet()
                    val facts = sessions.map { session ->
                        SessionFact(
                            date = LocalDate.parse(session.date),
                            makesUpForDate = session.makesUpForDate?.let(LocalDate::parse),
                        )
                    }
                    val makeups = StreakRules.openMakeups(
                        today = today,
                        todayIsPlanned = false,
                        todayHasSession = false,
                        plannedDays = plannedDays,
                        sessions = facts,
                    ).mapNotNull { missed ->
                        val id = MakeupTarget.workoutId(missed, byWeekday) ?: return@mapNotNull null
                        MakeupOffer(
                            date = missed,
                            label = missed.format(DATE),
                            workoutId = id,
                            workoutName = names[missed.dayOfWeek.value].orEmpty(),
                        )
                    }
                    flowOf(TrainUi(loading = false, dateLabel = label, kind = TrainKind.Rest(makeups)))
                } else {
                    repository.observeWorkout(workoutId).map { graph ->
                        val preview = graph?.sortedGraph()?.slots.orEmpty().mapNotNull { slot ->
                            val alternatives = slot.alternatives
                            if (alternatives.isEmpty()) null
                            else {
                                val index = Rotation.normalize(slot.slot.rotationIndex, alternatives.size)
                                alternatives[index].exercise.name
                            }
                        }
                        TrainUi(
                            loading = false,
                            dateLabel = label,
                            kind = TrainKind.Planned(
                                workoutId = workoutId,
                                name = day.workoutName.orEmpty(),
                                preview = preview,
                            ),
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainUi())

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)
    }
}

@Composable
private fun TrainScreen(
    state: TrainUi,
    onStart: (Long) -> Unit,
    onMakeup: (Long, LocalDate) -> Unit,
    onOther: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(state.dateLabel, style = MaterialTheme.typography.titleMedium)
        when (val kind = state.kind) {
            is TrainKind.Rest -> {
                Text(stringResource(R.string.rest_day), style = MaterialTheme.typography.headlineMedium)
                if (kind.makeups.isNotEmpty()) {
                    Text(stringResource(R.string.makeup_heading), style = MaterialTheme.typography.titleMedium)
                    kind.makeups.forEach { offer ->
                        FilledTonalButton(
                            onClick = { onMakeup(offer.workoutId, offer.date) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.makeup_item, offer.label, offer.workoutName))
                        }
                    }
                }
                OutlinedButton(onClick = onOther, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.other_workout))
                }
            }
            is TrainKind.Planned -> {
                Text(kind.name, style = MaterialTheme.typography.headlineMedium)
                kind.preview.forEach { name ->
                    Text(name, style = MaterialTheme.typography.titleLarge)
                }
                Button(onClick = { onStart(kind.workoutId) }, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                    Text(stringResource(R.string.start))
                }
                OutlinedButton(onClick = onOther, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.other_workout))
                }
            }
            is TrainKind.Done -> {
                Text(stringResource(R.string.done_today), style = MaterialTheme.typography.headlineMedium)
                Text(kind.workoutName, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPickerRoute(onBack: () -> Unit, onPick: (Long) -> Unit) {
    val app = rememberApp()
    val vm: PickerViewModel = viewModel(factory = viewModelFactory { PickerViewModel(app.repository) })
    val workouts by vm.workouts.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.other_workout)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (workouts.isEmpty()) {
            Text(stringResource(R.string.no_workouts_pick), modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(workouts, key = { it.id }) { workout ->
                    ListItem(
                        headlineContent = { Text(workout.name) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .then(Modifier),
                        trailingContent = {
                            TextButton(onClick = { onPick(workout.id) }) {
                                Text(stringResource(R.string.start))
                            }
                        },
                    )
                }
            }
        }
    }
}

class PickerViewModel(repository: TimiTrainRepository) : ViewModel() {
    val workouts: StateFlow<List<WorkoutEntity>> = repository.observeWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class SessionUi(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val blocked: Boolean = false,
    val workoutName: String = "",
    val steps: List<SessionStep> = emptyList(),
    val index: Int = 0,
    val actuals: List<SetValues> = emptyList(),
    val completed: Set<Int> = emptySet(),
    val restEndsAt: Long? = null,
    val askDiscard: Boolean = false,
    val saved: Boolean = false,
    val failed: Boolean = false,
)

@Composable
fun SessionRoute(workoutId: Long?, makesUpForDate: LocalDate? = null, onFinished: () -> Unit) {
    val app = rememberApp()
    if (workoutId == null) {
        onFinished()
        return
    }
    val vm: SessionViewModel = viewModel(
        key = "session-$workoutId-${makesUpForDate ?: "none"}",
        factory = viewModelFactory {
            SessionViewModel(
                app.repository,
                app.clock,
                app.restScheduler,
                app,
                workoutId,
                makesUpForDate,
            )
        },
    )
    val state by vm.state.collectAsState()
    LaunchedEffect(state.saved) {
        if (state.saved) onFinished()
    }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler {
        if (state.completed.isEmpty()) onFinished() else vm.requestDiscard()
    }
    SessionScreen(state = state, vm = vm, onLeave = onFinished)
}

class SessionViewModel(
    private val repository: TimiTrainRepository,
    private val clock: AppClock,
    private val scheduler: RestScheduler,
    private val appContext: android.content.Context,
    private val workoutId: Long,
    private val makesUpForDate: LocalDate?,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionUi())
    val state: StateFlow<SessionUi> = _state
    private var defaultRest = InitialNumbers.DEFAULT_REST_SECONDS
    private var startedAt = 0L
    private var date: LocalDate = clock.today()
    private var workoutName = ""
    private var chimeJob: Job? = null

    init {
        viewModelScope.launch {
            // Same-day sessions use the local date at Start. A session that crosses
            // midnight is an open product question and is not given a special rule.
            date = clock.today()
            if (repository.sessionOn(date) != null) {
                _state.value = SessionUi(loading = false, blocked = true)
                return@launch
            }
            val graph = repository.loadWorkout(workoutId)?.sortedGraph()
            if (graph == null) {
                _state.value = SessionUi(loading = false, missing = true)
                return@launch
            }
            defaultRest = repository.restSeconds()
            startedAt = clock.instant().toEpochMilli()
            workoutName = graph.workout.name
            val steps = mutableListOf<SessionStep>()
            for (slot in graph.slots) {
                if (slot.alternatives.isEmpty()) continue
                val rotationIndex = slot.slot.rotationIndex
                val due = slot.alternatives[Rotation.normalize(rotationIndex, slot.alternatives.size)]
                val sets = due.sets
                sets.forEachIndexed { index, set ->
                    val exercise = due.exercise
                    steps += SessionStep(
                        slotId = slot.slot.id,
                        alternativeId = due.alternative.id,
                        exerciseId = exercise.id,
                        exerciseName = exercise.name,
                        type = exercise.type.toExerciseType(),
                        setPosition = set.position,
                        setNumber = index + 1,
                        setCount = sets.size,
                        plan = set.toValues(),
                        rotationIndex = rotationIndex,
                        lastTime = repository.lastValues(exercise.id, set.position),
                        increments = Increments(
                            weightKg = exercise.weightIncrementKg,
                            durationSec = exercise.durationIncrementSec,
                            distanceKm = exercise.distanceIncrementKm,
                            durationMin = exercise.durationIncrementMin,
                        ),
                        restSecondsOverride = due.alternative.restSecondsOverride,
                    )
                }
            }
            _state.value = SessionUi(
                loading = false,
                workoutName = workoutName,
                steps = steps,
                actuals = steps.map { it.plan },
            )
        }
    }

    internal fun nudge(metric: Metric, delta: Int) {
        val current = _state.value
        val index = current.index
        val step = current.steps.getOrNull(index) ?: return
        if (index in current.completed) return
        val values = current.actuals[index]
        val updated = when (metric) {
            Metric.REPS -> values.copy(reps = RepsStep.nudge(values.reps, delta))
            Metric.WEIGHT -> values.copy(
                weightKg = SetStepper.nudgeDecimal(
                    values.weightKg,
                    step.increments.weightKg ?: InitialNumbers.WEIGHT_INCREMENT_KG,
                    delta,
                ),
            )
            Metric.SECONDS -> values.copy(
                durationSec = SetStepper.nudgeInt(
                    values.durationSec,
                    step.increments.durationSec ?: InitialNumbers.DURATION_INCREMENT_SEC,
                    delta,
                ),
            )
            Metric.MINUTES -> values.copy(
                durationMin = SetStepper.nudgeOptionalInt(
                    values.durationMin,
                    step.increments.durationMin ?: InitialNumbers.DURATION_INCREMENT_MIN,
                    delta,
                ),
            )
            Metric.DISTANCE -> values.copy(
                distanceKm = SetStepper.nudgeOptionalDecimal(
                    values.distanceKm,
                    step.increments.distanceKm ?: InitialNumbers.DISTANCE_INCREMENT_KM,
                    delta,
                ),
            )
        }
        _state.value = current.copy(actuals = current.actuals.toMutableList().also { it[index] = updated })
    }

    fun completeSet() {
        val current = _state.value
        val index = current.index
        if (index !in current.steps.indices || index in current.completed) return
        val step = current.steps[index]
        val completed = current.completed + index
        val next = index + 1
        _state.value = current.copy(completed = completed, index = next)
        armRest(step.restSecondsOverride ?: defaultRest)
    }

    fun requestDiscard() {
        _state.value = _state.value.copy(askDiscard = true)
    }

    fun dismissDiscard() {
        _state.value = _state.value.copy(askDiscard = false)
    }

    fun save() {
        val current = _state.value
        if (current.completed.isEmpty() || current.saved) return
        viewModelScope.launch {
            val draft = SessionDraft(
                workoutId = workoutId,
                workoutName = workoutName,
                date = date,
                startedAt = startedAt,
                finishedAt = clock.instant().toEpochMilli(),
                makesUpForDate = makesUpForDate,
                completed = current.completed.sorted().map { index ->
                    val step = current.steps[index]
                    CompletedSetDraft(
                        slotId = step.slotId,
                        alternativeId = step.alternativeId,
                        exerciseId = step.exerciseId,
                        exerciseName = step.exerciseName,
                        position = step.setPosition,
                        type = step.type,
                        plan = step.plan,
                        actual = current.actuals[index],
                        rotationIndex = step.rotationIndex,
                    )
                },
            )
            try {
                repository.saveSession(draft)
                scheduler.cancel()
                chimeJob?.cancel()
                _state.value = current.copy(saved = true, askDiscard = false)
            } catch (_: SQLiteConstraintException) {
                _state.value = current.copy(failed = true, blocked = true)
            }
        }
    }

    private fun armRest(seconds: Int) {
        chimeJob?.cancel()
        scheduler.cancel()
        if (seconds <= 0) {
            _state.value = _state.value.copy(restEndsAt = null)
            return
        }
        val endsAt = clock.instant().toEpochMilli() + seconds * 1000L
        _state.value = _state.value.copy(restEndsAt = endsAt)
        scheduler.schedule(endsAt)
        chimeJob = viewModelScope.launch {
            val wait = endsAt - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            if (_state.value.restEndsAt == endsAt) RestChime.playOnce(appContext, endsAt)
        }
    }

    override fun onCleared() {
        chimeJob?.cancel()
        scheduler.cancel()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionScreen(state: SessionUi, vm: SessionViewModel, onLeave: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.workoutName.ifBlank { stringResource(R.string.tab_train) }) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.completed.isEmpty()) onLeave() else vm.requestDiscard()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Text(stringResource(R.string.train_empty), modifier = Modifier.padding(padding).padding(16.dp))
            state.blocked -> Text(
                stringResource(R.string.done_today),
                modifier = Modifier.padding(padding).padding(24.dp),
                style = MaterialTheme.typography.headlineMedium,
            )
            state.missing -> Text(stringResource(R.string.plan_empty), modifier = Modifier.padding(padding).padding(16.dp))
            else -> Column(
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RestBanner(state.restEndsAt)
                val step = state.steps.getOrNull(state.index)
                if (step == null) {
                    Text(
                        if (state.steps.isEmpty()) stringResource(R.string.empty_session) else stringResource(R.string.session_done),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                } else {
                    val actual = state.actuals[state.index]
                    Text(step.exerciseName, style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.set_of, step.setNumber, step.setCount))
                    Text(stringResource(R.string.plan_label), style = MaterialTheme.typography.labelLarge)
                    Text(
                        GermanNumbers.formatSet(step.plan, step.type),
                        style = MaterialTheme.typography.displaySmall,
                    )
                    step.lastTime?.let { last ->
                        Text(stringResource(R.string.last_time, GermanNumbers.formatSet(last, step.type)))
                    }
                    when (step.type) {
                        ExerciseType.STRENGTH, ExerciseType.BODYWEIGHT -> Stepper(
                            label = stringResource(R.string.reps),
                            value = (actual.reps ?: 0).toString(),
                            onMinus = { vm.nudge(Metric.REPS, -1) },
                            onPlus = { vm.nudge(Metric.REPS, 1) },
                        )
                        else -> Unit
                    }
                    if (step.type == ExerciseType.STRENGTH) {
                        Stepper(
                            label = stringResource(R.string.weight_kg),
                            value = GermanNumbers.format(actual.weightKg ?: 0.0),
                            onMinus = { vm.nudge(Metric.WEIGHT, -1) },
                            onPlus = { vm.nudge(Metric.WEIGHT, 1) },
                        )
                    }
                    if (step.type == ExerciseType.TIMED) {
                        Stepper(
                            label = stringResource(R.string.duration_sec),
                            value = (actual.durationSec ?: 0).toString(),
                            onMinus = { vm.nudge(Metric.SECONDS, -1) },
                            onPlus = { vm.nudge(Metric.SECONDS, 1) },
                        )
                    }
                    if (step.type == ExerciseType.CARDIO) {
                        Stepper(
                            label = stringResource(R.string.distance_km),
                            value = actual.distanceKm?.let(GermanNumbers::format) ?: "–",
                            onMinus = { vm.nudge(Metric.DISTANCE, -1) },
                            onPlus = { vm.nudge(Metric.DISTANCE, 1) },
                        )
                        Stepper(
                            label = stringResource(R.string.duration_min),
                            value = actual.durationMin?.toString() ?: "–",
                            onMinus = { vm.nudge(Metric.MINUTES, -1) },
                            onPlus = { vm.nudge(Metric.MINUTES, 1) },
                        )
                    }
                    Button(
                        onClick = vm::completeSet,
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.complete_set),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = vm::requestDiscard,
                        modifier = Modifier.weight(1f).height(64.dp),
                    ) { Text(stringResource(R.string.discard)) }
                    FilledTonalButton(
                        onClick = vm::save,
                        enabled = state.completed.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(64.dp),
                    ) { Text(stringResource(R.string.save_and_finish), textAlign = TextAlign.Center) }
                }
            }
        }
    }
    if (state.askDiscard) {
        AlertDialog(
            onDismissRequest = vm::dismissDiscard,
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_body)) },
            confirmButton = {
                TextButton(onClick = onLeave) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDiscard) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun RestBanner(endsAt: Long?) {
    if (endsAt == null) return
    var now by remember(endsAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) {
            now = System.currentTimeMillis()
            delay(200)
        }
        now = System.currentTimeMillis()
    }
    val remaining = ((endsAt - now).coerceAtLeast(0) + 999) / 1000
    Column {
        Text(stringResource(if (remaining == 0L) R.string.rest_done else R.string.rest_timer))
        if (remaining > 0) {
            Text("%d:%02d".format(remaining / 60, remaining % 60), style = MaterialTheme.typography.displaySmall)
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilledTonalButton(onClick = onMinus, modifier = Modifier.size(72.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.minus))
            }
            Text(value, style = MaterialTheme.typography.displaySmall)
            FilledTonalButton(onClick = onPlus, modifier = Modifier.size(72.dp)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.plus))
            }
        }
    }
}
