package de.timitrain.ui.train

import android.database.sqlite.SQLiteConstraintException
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import de.timitrain.R
import de.timitrain.data.CompletedSetDraft
import de.timitrain.data.SessionDraft
import de.timitrain.data.ExercisePhotos
import de.timitrain.data.TimiTrainRepository
import de.timitrain.data.db.WorkoutEntity
import de.timitrain.data.db.WorkoutGraph
import de.timitrain.data.sortedGraph
import de.timitrain.data.toValues
import de.timitrain.domain.AlternativeSwitch
import de.timitrain.domain.AppClock
import de.timitrain.domain.CountdownPhase
import de.timitrain.domain.ExerciseCategory
import de.timitrain.domain.ExerciseType
import de.timitrain.domain.TimedFlow
import de.timitrain.domain.countdownSeconds
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
import de.timitrain.domain.toExerciseCategory
import de.timitrain.domain.toExerciseType
import de.timitrain.timer.RestChime
import de.timitrain.timer.RestScheduler
import de.timitrain.ui.categoryLabel
import de.timitrain.ui.markColor
import de.timitrain.ui.theme.LocalTimiDarkTheme
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
    val category: ExerciseCategory,
    val setPosition: Int,
    val setNumber: Int,
    val setCount: Int,
    val plan: SetValues,
    val rotationIndex: Int,
    val alternativeIndex: Int,
    val alternativeCount: Int,
    val note: String? = null,
    val photoFile: String? = null,
    val perSide: Boolean = false,
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
    val adjusting: Boolean = false,
    val countdownEndsAt: Long? = null,
    val countdownPhase: CountdownPhase? = null,
    val countdownTotal: Int = 0,
    val photoOpen: Boolean = false,
)

@Composable
fun SessionRoute(
    workoutId: Long?,
    makesUpForDate: LocalDate? = null,
    onFinished: () -> Unit,
    onEditExercise: (Long, Long) -> Unit = { _, _ -> },
) {
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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler {
        if (state.photoOpen) vm.closePhoto()
        else if (state.completed.isEmpty()) onFinished() else vm.requestDiscard()
    }
    SessionScreen(state = state, vm = vm, onLeave = onFinished, onEditExercise = onEditExercise)
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
    private var countdownJob: Job? = null
    private var workoutGraph: WorkoutGraph? = null
    private var ready = false

    init {
        viewModelScope.launch {
            // Same-day sessions use the local date at Start. A session that crosses
            // midnight is an open product question and is not given a special rule.
            date = clock.today()
            if (repository.sessionOn(date) != null) {
                _state.value = SessionUi(loading = false, blocked = true)
                ready = true
                return@launch
            }
            val graph = repository.loadWorkout(workoutId)?.sortedGraph()
            if (graph == null) {
                _state.value = SessionUi(loading = false, missing = true)
                ready = true
                return@launch
            }
            defaultRest = repository.restSeconds()
            startedAt = clock.instant().toEpochMilli()
            workoutName = graph.workout.name
            workoutGraph = graph
            val steps = buildSteps(graph, emptyMap())
            _state.value = SessionUi(
                loading = false,
                workoutName = workoutName,
                steps = steps,
                actuals = steps.map { it.plan },
            )
            ready = true
        }
    }

    fun onResume() {
        if (!ready) return
        val current = _state.value
        if (current.loading || current.blocked || current.missing || current.saved) return
        viewModelScope.launch {
            val fresh = repository.loadWorkout(workoutId)?.sortedGraph() ?: return@launch
            workoutGraph = fresh
            val chosen = current.steps.groupBy { it.slotId }.mapValues { it.value.first().alternativeIndex }
            val newSteps = buildSteps(fresh, chosen)
            _state.value = rebase(current, newSteps, focusSlot = null, focusSet = null, keepCountdown = true)
        }
    }

    fun switchAlternative(delta: Int) {
        val current = _state.value
        val step = current.steps.getOrNull(current.index) ?: return
        if (!current.canSwitchAlternative()) return
        val graph = workoutGraph ?: return
        val next = Rotation.normalize(step.alternativeIndex + delta, step.alternativeCount)
        if (next == step.alternativeIndex) return
        val chosen = current.steps.groupBy { it.slotId }.mapValues { it.value.first().alternativeIndex }.toMutableMap()
        chosen[step.slotId] = next
        viewModelScope.launch {
            val newSteps = buildSteps(graph, chosen)
            _state.value = rebase(
                _state.value,
                newSteps,
                focusSlot = step.slotId,
                focusSet = 1,
                keepCountdown = false,
            )
        }
    }

    fun openPhoto() {
        val step = _state.value.steps.getOrNull(_state.value.index) ?: return
        if (step.photoFile == null) return
        _state.value = _state.value.copy(photoOpen = true)
    }

    fun closePhoto() {
        _state.value = _state.value.copy(photoOpen = false)
    }

    /** Skip a running rest without its chime. A timed set that has not started begins now. */
    fun play() {
        val current = _state.value
        val step = current.steps.getOrNull(current.index) ?: return
        if (current.index in current.completed) return
        val resting = restingNow(current)
        val timedWaiting = step.type == ExerciseType.TIMED && current.countdownPhase == null
        if (!resting && !timedWaiting) return
        chimeJob?.cancel()
        scheduler.cancel()
        _state.value = current.copy(restEndsAt = null)
        if (timedWaiting) startCountdown(step)
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

    fun startAdjusting() {
        val current = _state.value
        if (current.index !in current.steps.indices || current.index in current.completed) return
        _state.value = current.copy(adjusting = true)
    }

    /** Green check: store the planned values, the same result as a checkmark that was never nudged. */
    fun completeAsPlanned() {
        finishCurrentSet(usePlan = true)
    }

    /** Blue check after plus and minus: store the edited values, the same result as today's nudged checkmark. */
    fun confirmAdjusted() {
        finishCurrentSet(usePlan = false)
    }

    private fun finishCurrentSet(usePlan: Boolean) {
        val current = _state.value
        val index = current.index
        if (index !in current.steps.indices || index in current.completed) return
        val step = current.steps[index]
        val actuals = current.actuals.toMutableList()
        if (usePlan) actuals[index] = step.plan
        val completed = current.completed + index
        countdownJob?.cancel()
        _state.value = current.copy(
            actuals = actuals,
            completed = completed,
            index = index + 1,
            adjusting = false,
            countdownEndsAt = null,
            countdownPhase = null,
            countdownTotal = 0,
            photoOpen = false,
        )
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
                        alternativeIndex = step.alternativeIndex,
                    )
                },
            )
            try {
                repository.saveSession(draft)
                scheduler.cancel()
                chimeJob?.cancel()
                countdownJob?.cancel()
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
            maybeAutoStart()
            return
        }
        val endsAt = clock.instant().toEpochMilli() + seconds * 1000L
        _state.value = _state.value.copy(restEndsAt = endsAt)
        scheduler.schedule(endsAt)
        chimeJob = viewModelScope.launch {
            val wait = endsAt - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            if (_state.value.restEndsAt != endsAt) return@launch
            RestChime.playOnce(appContext, endsAt)
            maybeAutoStart()
        }
    }

    private fun maybeAutoStart() {
        val current = _state.value
        val step = current.steps.getOrNull(current.index) ?: return
        if (step.type != ExerciseType.TIMED || current.countdownPhase != null) return
        if (!TimedFlow.autoStarts(step.setNumber, restingNow(current))) return
        if (countdownSeconds(step.type, step.plan) == null) return
        startCountdown(step)
    }

    private fun startCountdown(step: SessionStep) {
        val seconds = countdownSeconds(step.type, step.plan) ?: return
        countdownJob?.cancel()
        val phase = if (step.perSide) CountdownPhase.LEFT else CountdownPhase.SINGLE
        val endsAt = clock.instant().toEpochMilli() + seconds * 1000L
        _state.value = _state.value.copy(
            restEndsAt = null,
            countdownEndsAt = endsAt,
            countdownPhase = phase,
            countdownTotal = seconds,
        )
        countdownJob = viewModelScope.launch {
            delayUntil(endsAt)
            if (!stillCounting(endsAt)) return@launch
            if (phase == CountdownPhase.LEFT && step.perSide) {
                RestChime.playSide(appContext)
                val rightEnds = clock.instant().toEpochMilli() + seconds * 1000L
                _state.value = _state.value.copy(
                    countdownEndsAt = rightEnds,
                    countdownPhase = CountdownPhase.RIGHT,
                )
                delayUntil(rightEnds)
                if (!stillCounting(rightEnds)) return@launch
                RestChime.playOnce(appContext, -rightEnds)
            } else {
                RestChime.playOnce(appContext, -endsAt)
            }
            if (_state.value.countdownPhase == CountdownPhase.RIGHT ||
                _state.value.countdownPhase == CountdownPhase.SINGLE ||
                _state.value.countdownPhase == phase
            ) {
                _state.value = _state.value.copy(countdownPhase = CountdownPhase.DONE, countdownEndsAt = null)
            }
        }
    }

    private suspend fun delayUntil(endsAt: Long) {
        val wait = endsAt - System.currentTimeMillis()
        if (wait > 0) delay(wait)
    }

    private fun stillCounting(endsAt: Long): Boolean = _state.value.countdownEndsAt == endsAt

    private fun restingNow(state: SessionUi): Boolean {
        val ends = state.restEndsAt ?: return false
        return ends > System.currentTimeMillis()
    }

    private suspend fun buildSteps(graph: WorkoutGraph, chosen: Map<Long, Int>): List<SessionStep> {
        val steps = mutableListOf<SessionStep>()
        for (slot in graph.slots) {
            if (slot.alternatives.isEmpty()) continue
            val offered = Rotation.normalize(slot.slot.rotationIndex, slot.alternatives.size)
            val visible = Rotation.normalize(chosen[slot.slot.id] ?: offered, slot.alternatives.size)
            val due = slot.alternatives[visible]
            val sets = due.sets
            sets.forEachIndexed { index, set ->
                val exercise = due.exercise
                steps += SessionStep(
                    slotId = slot.slot.id,
                    alternativeId = due.alternative.id,
                    exerciseId = exercise.id,
                    exerciseName = exercise.name,
                    type = exercise.type.toExerciseType(),
                    category = exercise.category.toExerciseCategory(),
                    setPosition = set.position,
                    setNumber = index + 1,
                    setCount = sets.size,
                    plan = set.toValues(),
                    rotationIndex = offered,
                    alternativeIndex = visible,
                    alternativeCount = slot.alternatives.size,
                    note = exercise.note,
                    photoFile = exercise.photoFile,
                    perSide = exercise.perSide,
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
        return steps
    }

    private fun rebase(
        current: SessionUi,
        newSteps: List<SessionStep>,
        focusSlot: Long?,
        focusSet: Int?,
        keepCountdown: Boolean,
    ): SessionUi {
        val oldByKey = current.steps.mapIndexed { index, step -> (step.slotId to step.setNumber) to index }.toMap()
        val actuals = newSteps.map { step ->
            val old = oldByKey[step.slotId to step.setNumber]
            if (old != null && old in current.completed) current.actuals[old] else step.plan
        }
        val completed = newSteps.mapIndexedNotNullTo(mutableSetOf()) { index, step ->
            val old = oldByKey[step.slotId to step.setNumber]
            if (old != null && old in current.completed) index else null
        }
        val oldCurrent = current.steps.getOrNull(current.index)
        val rawIndex = when {
            focusSlot != null -> indexOfSet(newSteps, focusSlot, focusSet ?: 1)
            oldCurrent == null -> current.index.coerceAtMost(newSteps.size)
            else -> {
                val exact = newSteps.indexOfFirst {
                    it.slotId == oldCurrent.slotId && it.setNumber == oldCurrent.setNumber
                }
                if (exact >= 0) exact else {
                    val last = newSteps.indexOfLast { it.slotId == oldCurrent.slotId }
                    if (last >= 0) last else current.index.coerceAtMost(newSteps.size)
                }
            }
        }
        val missingSet = oldCurrent != null && focusSlot == null &&
            newSteps.none { it.slotId == oldCurrent.slotId && it.setNumber == oldCurrent.setNumber }
        var index = rawIndex.coerceIn(0, newSteps.size)
        if (missingSet && index in completed) {
            index = (index + 1..newSteps.size).firstOrNull { it == newSteps.size || it !in completed } ?: newSteps.size
        }
        val landed = newSteps.getOrNull(index)
        val sameCountdown = keepCountdown && oldCurrent != null && landed != null &&
            landed.slotId == oldCurrent.slotId &&
            landed.setNumber == oldCurrent.setNumber &&
            landed.alternativeId == oldCurrent.alternativeId &&
            landed.perSide == oldCurrent.perSide &&
            landed.plan.durationSec == oldCurrent.plan.durationSec
        if (!sameCountdown) countdownJob?.cancel()
        return current.copy(
            loading = false,
            workoutName = workoutName,
            steps = newSteps,
            actuals = actuals,
            completed = completed,
            index = index,
            adjusting = false,
            countdownEndsAt = if (sameCountdown) current.countdownEndsAt else null,
            countdownPhase = if (sameCountdown) current.countdownPhase else null,
            countdownTotal = if (sameCountdown) current.countdownTotal else 0,
            photoOpen = false,
        )
    }

    private fun indexOfSet(steps: List<SessionStep>, slotId: Long, setNumber: Int): Int {
        val matches = steps.indices.filter { steps[it].slotId == slotId }
        return matches.firstOrNull { steps[it].setNumber == setNumber } ?: matches.lastOrNull() ?: steps.size
    }

    override fun onCleared() {
        chimeJob?.cancel()
        countdownJob?.cancel()
        scheduler.cancel()
    }
}

private fun SessionUi.canSwitchAlternative(): Boolean {
    val step = steps.getOrNull(index) ?: return false
    val started = steps.indices.any { i -> steps[i].slotId == step.slotId && i in completed }
    return AlternativeSwitch.allowed(step.alternativeCount, started, countdownPhase != null, adjusting)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionScreen(
    state: SessionUi,
    vm: SessionViewModel,
    onLeave: () -> Unit,
    onEditExercise: (Long, Long) -> Unit,
) {
    val step = state.steps.getOrNull(state.index)
    val sessionOpen = !state.loading && !state.blocked && !state.missing
    var menuOpen by remember { mutableStateOf(false) }
    val restEndsAt = state.restEndsAt
    var restNow by remember(restEndsAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(restEndsAt) {
        if (restEndsAt == null) return@LaunchedEffect
        while (System.currentTimeMillis() < restEndsAt) {
            restNow = System.currentTimeMillis()
            delay(200)
        }
        restNow = System.currentTimeMillis()
    }
    val resting = restEndsAt != null && restNow < restEndsAt
    val restTint = if (LocalTimiDarkTheme.current) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.14f)
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SessionTitle(
                        workoutName = state.workoutName.ifBlank { stringResource(R.string.tab_train) },
                        category = if (sessionOpen) step?.category else null,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.photoOpen) vm.closePhoto()
                        else if (state.completed.isEmpty()) onLeave() else vm.requestDiscard()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (sessionOpen) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.discard)) },
                                onClick = {
                                    menuOpen = false
                                    vm.requestDiscard()
                                },
                            )
                            if (step != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_exercise)) },
                                    onClick = {
                                        menuOpen = false
                                        onEditExercise(step.exerciseId, step.alternativeId)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_and_finish)) },
                                enabled = state.completed.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    vm.save()
                                },
                            )
                        }
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
            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                val canSwitch = state.canSwitchAlternative()
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = maxHeight)
                            .verticalScroll(rememberScrollState())
                            .then(alternativeSwipe(canSwitch, step, vm))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    ) {
                        RestBanner(state.restEndsAt)
                        if (step == null) {
                            Text(
                                if (state.steps.isEmpty()) stringResource(R.string.empty_session) else stringResource(R.string.session_done),
                                style = trainingText(MaterialTheme.typography.headlineSmall),
                                textAlign = TextAlign.Center,
                            )
                            if (state.steps.isNotEmpty() && state.completed.isNotEmpty()) {
                                FinishButton(onClick = vm::save)
                            }
                        } else {
                            val actual = state.actuals[state.index]
                            val timed = step.type == ExerciseType.TIMED
                            val phase = state.countdownPhase
                            val started = phase != null
                            val showPlay = TimedFlow.showPlay(timed, started, resting)
                            val showChecks = TimedFlow.showChecks(timed, started, phase == CountdownPhase.DONE, resting)
                            Text(
                                step.exerciseName,
                                style = trainingText(MaterialTheme.typography.headlineSmall),
                                textAlign = TextAlign.Center,
                            )
                            ExerciseNoteRow(
                                note = step.note,
                                hasPhoto = step.photoFile != null,
                                onPhoto = vm::openPhoto,
                            )
                            ParameterLine(
                                stringResource(R.string.set_name),
                                stringResource(R.string.set_progress, step.setNumber, step.setCount),
                            )
                            if (!state.adjusting) {
                                PlannedParameters(step)
                            }
                            if (step.type == ExerciseType.CARDIO) {
                                countdownSeconds(step.type, step.plan)?.let { seconds ->
                                    ExerciseCountdown(stepKey = state.index, totalSeconds = seconds)
                                }
                            }
                            if (timed && phase != null && phase != CountdownPhase.DONE) {
                                TimedCountdown(phase = phase, endsAt = state.countdownEndsAt)
                            }
                            if (state.adjusting) {
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
                            }
                            if (showPlay) {
                                PlayButton(onClick = vm::play)
                            }
                            if (state.adjusting) {
                                CheckButton(
                                    onClick = vm::confirmAdjusted,
                                    description = stringResource(R.string.confirm_actual),
                                    container = Color(0xFF2E7D32),
                                    modifier = Modifier.fillMaxWidth(0.3f),
                                )
                            } else if (showChecks) {
                                Row(
                                    Modifier.fillMaxWidth(0.6f),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    CheckButton(
                                        onClick = vm::completeAsPlanned,
                                        description = stringResource(R.string.done_as_planned),
                                        container = Color(0xFF2E7D32),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Button(
                                        onClick = vm::startAdjusting,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF1565C0),
                                            contentColor = Color.White,
                                        ),
                                    ) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = stringResource(R.string.done_differently),
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (step != null && step.alternativeCount > 0) {
                    AlternativeDots(count = step.alternativeCount, selected = step.alternativeIndex)
                }
            }
        }
    }
    if (resting) {
        Box(Modifier.fillMaxSize().background(restTint))
    }
    if (state.photoOpen) {
        val name = step?.photoFile
        if (name != null) PhotoOverlay(name = name, onClose = vm::closePhoto)
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
private fun alternativeSwipe(enabled: Boolean, step: SessionStep?, vm: SessionViewModel): Modifier {
    if (!enabled || step == null) return Modifier
    return Modifier.pointerInput(step.slotId, step.alternativeIndex) {
        var accumulated = 0f
        val threshold = 64.dp.toPx()
        detectHorizontalDragGestures(
            onDragEnd = {
                when {
                    accumulated <= -threshold -> vm.switchAlternative(1)
                    accumulated >= threshold -> vm.switchAlternative(-1)
                }
                accumulated = 0f
            },
            onDragCancel = { accumulated = 0f },
            onHorizontalDrag = { _, amount -> accumulated += amount },
        )
    }
}

@Composable
private fun ExerciseNoteRow(note: String?, hasPhoto: Boolean, onPhoto: () -> Unit) {
    if (note.isNullOrBlank() && !hasPhoto) return
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (!note.isNullOrBlank()) {
            Text(
                note,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (hasPhoto) {
            IconButton(onClick = onPhoto) {
                Icon(Icons.Filled.Image, contentDescription = stringResource(R.string.photo_show))
            }
        }
    }
}

@Composable
private fun PlayButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(0.3f).height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF9A825),
            contentColor = Color.Black,
        ),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.play), modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun FinishButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(0.3f).height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2E7D32),
            contentColor = Color.White,
        ),
    ) {
        Icon(
            Icons.Filled.SportsScore,
            contentDescription = stringResource(R.string.save_and_finish),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun TimedCountdown(phase: CountdownPhase, endsAt: Long?) {
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
    val label = when (phase) {
        CountdownPhase.LEFT -> stringResource(R.string.side_left)
        CountdownPhase.RIGHT -> stringResource(R.string.side_right)
        else -> stringResource(R.string.exercise_timer)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = trainingText(MaterialTheme.typography.titleMedium), textAlign = TextAlign.Center)
        Text(
            "%d:%02d".format(remaining / 60, remaining % 60),
            style = trainingText(MaterialTheme.typography.displaySmall),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AlternativeDots(count: Int, selected: Int) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val emphasized = index == selected
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (emphasized) 10.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (emphasized) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun PhotoOverlay(name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(name) {
        val file = ExercisePhotos.file(context, name)
        if (!file.exists()) null else BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.photo),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
        }
    }
}

@Composable
private fun SessionTitle(workoutName: String, category: ExerciseCategory?) {
    if (category == null) {
        Text(workoutName, maxLines = 1)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(workoutName, maxLines = 1)
        Text(" / ")
        Text(categoryLabel(category), color = category.markColor(), maxLines = 1)
    }
}

@Composable
private fun CheckButton(
    onClick: () -> Unit,
    description: String,
    container: Color,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Color.White),
    ) {
        Icon(Icons.Filled.Check, contentDescription = description, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun PlannedParameters(step: SessionStep) {
    val plan = step.plan
    if (step.type == ExerciseType.STRENGTH || step.type == ExerciseType.BODYWEIGHT) {
        ParameterLine(stringResource(R.string.reps), (plan.reps ?: 0).toString())
    }
    if (step.type == ExerciseType.STRENGTH) {
        ParameterLine(stringResource(R.string.weight_kg), GermanNumbers.format(plan.weightKg ?: 0.0))
    }
    if (step.type == ExerciseType.TIMED) {
        ParameterLine(stringResource(R.string.duration_sec), (plan.durationSec ?: 0).toString())
    }
    if (step.type == ExerciseType.CARDIO) {
        plan.distanceKm?.let { ParameterLine(stringResource(R.string.distance_km), GermanNumbers.format(it)) }
        plan.durationMin?.let { ParameterLine(stringResource(R.string.duration_min), it.toString()) }
    }
}

@Composable
private fun ParameterLine(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = trainingText(MaterialTheme.typography.titleMedium), textAlign = TextAlign.Center)
        Text(value, style = trainingText(MaterialTheme.typography.displaySmall), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ExerciseCountdown(stepKey: Int, totalSeconds: Int) {
    var endsAt by remember(stepKey, totalSeconds) {
        mutableLongStateOf(System.currentTimeMillis() + totalSeconds * 1000L)
    }
    var now by remember(endsAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) {
            now = System.currentTimeMillis()
            delay(200)
        }
        now = System.currentTimeMillis()
    }
    val remaining = ((endsAt - now).coerceAtLeast(0) + 999) / 1000
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.exercise_timer),
            style = trainingText(MaterialTheme.typography.titleMedium),
            textAlign = TextAlign.Center,
        )
        Text(
            "%d:%02d".format(remaining / 60, remaining % 60),
            style = trainingText(MaterialTheme.typography.displaySmall),
            textAlign = TextAlign.Center,
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(if (remaining == 0L) R.string.rest_done else R.string.rest_timer),
            style = trainingText(MaterialTheme.typography.bodyLarge),
            textAlign = TextAlign.Center,
        )
        if (remaining > 0) {
            Text(
                "%d:%02d".format(remaining / 60, remaining % 60),
                style = trainingText(MaterialTheme.typography.displaySmall),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = trainingText(MaterialTheme.typography.titleMedium), textAlign = TextAlign.Center)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilledTonalButton(onClick = onMinus, modifier = Modifier.size(72.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.minus))
            }
            Text(value, style = trainingText(MaterialTheme.typography.displaySmall), textAlign = TextAlign.Center)
            FilledTonalButton(onClick = onPlus, modifier = Modifier.size(72.dp)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.plus))
            }
        }
    }
}

/** Training copy is one and a half times the theme size. Button labels are not passed through this. */
@Composable
private fun trainingText(style: TextStyle): TextStyle {
    return style.copy(fontSize = style.fontSize * 1.5f, lineHeight = style.lineHeight * 1.5f)
}
