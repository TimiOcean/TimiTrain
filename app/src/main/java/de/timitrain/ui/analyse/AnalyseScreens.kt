package de.timitrain.ui.analyse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import de.timitrain.R
import de.timitrain.data.TimiTrainRepository
import de.timitrain.data.db.SessionSetEntity
import de.timitrain.domain.AppClock
import de.timitrain.domain.ExerciseType
import de.timitrain.domain.GermanNumbers
import de.timitrain.domain.SessionFact
import de.timitrain.domain.SetValues
import de.timitrain.domain.StreakRules
import de.timitrain.ui.Routes
import de.timitrain.ui.rememberApp
import de.timitrain.ui.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AnalyseUi(
    val month: YearMonth = YearMonth.now(),
    val monthTitle: String = "",
    val monthCount: Int = 0,
    val streak: Int = 0,
    val checked: Set<LocalDate> = emptySet(),
)

@Composable
fun AnalyseRoute(nav: NavHostController) {
    val app = rememberApp()
    val vm: AnalyseViewModel = viewModel(
        factory = viewModelFactory { AnalyseViewModel(app.repository, app.clock) },
    )
    val state by vm.state.collectAsState()
    AnalyseScreen(
        state = state,
        onPrevious = vm::previous,
        onNext = vm::next,
        onDay = { nav.navigate(Routes.day(it.toString())) },
    )
}

class AnalyseViewModel(
    repository: TimiTrainRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.from(clock.today()))
    val state = combine(month, repository.observeSessions(), repository.observeWeek()) { visible, sessions, week ->
        val today = clock.today()
        val facts = sessions.map { session ->
            SessionFact(
                date = LocalDate.parse(session.date),
                makesUpForDate = session.makesUpForDate?.let(LocalDate::parse),
            )
        }
        val planned = week.filter { it.workoutId != null }.map { it.weekday }.toSet()
        AnalyseUi(
            month = visible,
            monthTitle = visible.format(MONTH),
            monthCount = facts.count { YearMonth.from(it.date) == YearMonth.from(today) },
            streak = StreakRules.streak(today, facts) { date -> date.dayOfWeek.value in planned },
            checked = facts.map { it.date }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyseUi())

    fun previous() {
        month.value = month.value.minusMonths(1)
    }

    fun next() {
        month.value = month.value.plusMonths(1)
    }

    private companion object {
        val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY)
    }
}

@Composable
private fun AnalyseScreen(
    state: AnalyseUi,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDay: (LocalDate) -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.month_count, state.monthCount), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.streak_count, state.streak), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_month))
            }
            Text(
                state.monthTitle,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { label ->
                Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        val first = state.month.atDay(1)
        val offset = first.dayOfWeek.value - 1
        val cells = offset + state.month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val index = row * 7 + column
                    val dayNumber = index - offset + 1
                    Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (dayNumber in 1..state.month.lengthOfMonth()) {
                            val date = state.month.atDay(dayNumber)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onDay(date) }.padding(4.dp),
                            ) {
                                Text(dayNumber.toString())
                                if (date in state.checked) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = stringResource(R.string.done_today),
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class DetailBlock(val name: String, val sets: List<SessionSetEntity>)

data class DetailUi(
    val loading: Boolean = true,
    val dateLabel: String = "",
    val empty: Boolean = false,
    val workoutName: String = "",
    val blocks: List<DetailBlock> = emptyList(),
    val askDelete: Boolean = false,
    val deleted: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailRoute(date: String, onBack: () -> Unit) {
    val app = rememberApp()
    val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
    val vm: DetailViewModel = viewModel(
        key = "day-$date",
        factory = viewModelFactory { DetailViewModel(app.repository, parsed) },
    )
    val state by vm.state.collectAsState()
    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.dateLabel.ifBlank { stringResource(R.string.session_detail) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.empty) {
            Text(stringResource(R.string.no_session_day), modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(Modifier.padding(padding)) {
                item {
                    Text(
                        state.workoutName,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                state.blocks.forEach { block ->
                    item {
                        Text(
                            block.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    block.sets.forEach { set ->
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.set_label, set.position + 1)) },
                                supportingContent = {
                                    Text(GermanNumbers.formatSet(set.toDisplayValues(), guessType(set)))
                                },
                            )
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = vm::requestDelete,
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.delete_session))
                    }
                }
            }
        }
    }
    if (state.askDelete) {
        AlertDialog(
            onDismissRequest = vm::dismissDelete,
            title = { Text(stringResource(R.string.delete_session_title)) },
            text = { Text(stringResource(R.string.delete_session_body)) },
            confirmButton = {
                TextButton(onClick = vm::confirmDelete) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDelete) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

class DetailViewModel(
    private val repository: TimiTrainRepository,
    private val date: LocalDate?,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailUi())
    val state = _state
    private var sessionId: Long? = null

    init {
        viewModelScope.launch { load() }
    }

    fun requestDelete() {
        if (sessionId == null) return
        _state.value = _state.value.copy(askDelete = true)
    }

    fun dismissDelete() {
        _state.value = _state.value.copy(askDelete = false)
    }

    fun confirmDelete() {
        val id = sessionId ?: return
        viewModelScope.launch {
            repository.deleteSession(id)
            sessionId = null
            _state.value = _state.value.copy(askDelete = false, deleted = true, empty = true)
        }
    }

    private suspend fun load() {
        if (date == null) {
            _state.value = DetailUi(loading = false, empty = true)
            return
        }
        val detail = repository.sessionDetail(date)
        if (detail == null) {
            _state.value = DetailUi(loading = false, empty = true, dateLabel = date.format(DAY))
            return
        }
        sessionId = detail.session.id
        val blocks = mutableListOf<DetailBlock>()
        for (set in detail.sets.sortedBy { it.id }) {
            val last = blocks.lastOrNull()
            if (last != null && last.sets.last().exerciseId == set.exerciseId && last.sets.last().slotId == set.slotId) {
                blocks[blocks.lastIndex] = last.copy(sets = last.sets + set)
            } else {
                blocks += DetailBlock(set.exerciseName, listOf(set))
            }
        }
        _state.value = DetailUi(
            loading = false,
            dateLabel = date.format(DAY),
            workoutName = detail.session.workoutName,
            blocks = blocks,
        )
    }

    private companion object {
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMANY)
    }
}

private fun SessionSetEntity.toDisplayValues(): SetValues {
    return SetValues(reps, weightKg, durationSec, durationMin, distanceKm)
}

private fun guessType(set: SessionSetEntity): ExerciseType {
    return when {
        set.weightKg != null -> ExerciseType.STRENGTH
        set.durationSec != null -> ExerciseType.TIMED
        set.distanceKm != null || set.durationMin != null -> ExerciseType.CARDIO
        else -> ExerciseType.BODYWEIGHT
    }
}
