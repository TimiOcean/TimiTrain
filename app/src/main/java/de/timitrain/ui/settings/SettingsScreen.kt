package de.timitrain.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import de.timitrain.R
import de.timitrain.data.TimiTrainRepository
import de.timitrain.data.backup.BackupCodec
import de.timitrain.data.backup.BackupDocument
import de.timitrain.data.backup.BackupRead
import de.timitrain.domain.InputProblem
import de.timitrain.ui.problemText
import de.timitrain.ui.rememberApp
import de.timitrain.ui.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUi(
    val restText: String = "",
    val darkMode: Boolean = false,
    val loaded: Boolean = false,
    val problem: InputProblem? = null,
    val notice: String? = null,
    val pendingImport: BackupDocument? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val app = rememberApp()
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory { SettingsViewModel(app.repository, app) },
    )
    val state by vm.state.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.stageImport(uri)
    }
    SettingsScreen(
        state = state,
        onBack = onBack,
        onRestChange = vm::onRestChange,
        onSaveRest = vm::saveRest,
        onDarkMode = vm::onDarkMode,
        onExport = { exportLauncher.launch("timitrain.json") },
        onImport = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
        onConfirmImport = vm::confirmImport,
        onCancelImport = vm::cancelImport,
    )
}

class SettingsViewModel(
    private val repository: TimiTrainRepository,
    private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUi())
    val state: StateFlow<SettingsUi> = _state

    init {
        viewModelScope.launch {
            val seconds = repository.restSeconds()
            val dark = repository.isDarkMode()
            _state.value = SettingsUi(restText = seconds.toString(), darkMode = dark, loaded = true)
        }
    }

    fun onDarkMode(dark: Boolean) {
        if (!_state.value.loaded) return
        _state.value = _state.value.copy(darkMode = dark, problem = null, notice = null)
        viewModelScope.launch { repository.setDarkMode(dark) }
    }

    fun onRestChange(text: String) {
        _state.value = _state.value.copy(restText = text, problem = null, notice = null)
    }

    fun saveRest() {
        val seconds = _state.value.restText.trim().toIntOrNull()
        if (seconds == null) {
            _state.value = _state.value.copy(problem = InputProblem.NUMBER)
            return
        }
        if (seconds < 0) {
            _state.value = _state.value.copy(problem = InputProblem.REST)
            return
        }
        viewModelScope.launch {
            repository.setRestSeconds(seconds)
            _state.value = _state.value.copy(problem = null, notice = null)
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = BackupCodec.encode(repository.exportDocument())
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("no stream")
                _state.value = _state.value.copy(problem = null, notice = "written")
            } catch (_: Exception) {
                _state.value = _state.value.copy(problem = InputProblem.BACKUP_WRITE, notice = null)
            }
        }
    }

    fun stageImport(uri: Uri) {
        viewModelScope.launch {
            val text = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (_: Exception) {
                null
            }
            if (text == null) {
                _state.value = _state.value.copy(problem = InputProblem.BACKUP_READ, pendingImport = null)
                return@launch
            }
            when (val read = BackupCodec.decode(text)) {
                is BackupRead.Ok -> _state.value = _state.value.copy(problem = null, pendingImport = read.document)
                BackupRead.Rejected -> _state.value = _state.value.copy(
                    problem = InputProblem.BACKUP_REJECT,
                    pendingImport = null,
                )
            }
        }
    }

    fun cancelImport() {
        _state.value = _state.value.copy(pendingImport = null)
    }

    fun confirmImport() {
        val document = _state.value.pendingImport ?: return
        viewModelScope.launch {
            try {
                repository.replaceAll(document)
                _state.value = _state.value.copy(pendingImport = null, problem = null, notice = "imported")
            } catch (_: Exception) {
                _state.value = _state.value.copy(pendingImport = null, problem = InputProblem.BACKUP_REJECT)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: SettingsUi,
    onBack: () -> Unit,
    onRestChange: (String) -> Unit,
    onSaveRest: () -> Unit,
    onDarkMode: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
) {
    var rest by remember(state.loaded, state.restText) { mutableStateOf(state.restText) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.appearance))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !state.darkMode,
                    onClick = { onDarkMode(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    enabled = state.loaded,
                ) {
                    Text(stringResource(R.string.theme_light))
                }
                SegmentedButton(
                    selected = state.darkMode,
                    onClick = { onDarkMode(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    enabled = state.loaded,
                ) {
                    Text(stringResource(R.string.theme_dark))
                }
            }
            OutlinedTextField(
                value = rest,
                onValueChange = {
                    rest = it
                    onRestChange(it)
                },
                label = { Text(stringResource(R.string.default_rest)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = onSaveRest, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save))
            }
            Text(stringResource(R.string.export_hint))
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.export_json))
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_json))
            }
            state.problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
            if (state.notice == "written") Text(stringResource(R.string.backup_written))
            if (state.notice == "imported") Text(stringResource(R.string.import_done))
        }
    }
    if (state.pendingImport != null) {
        AlertDialog(
            onDismissRequest = onCancelImport,
            title = { Text(stringResource(R.string.import_title)) },
            text = { Text(stringResource(R.string.import_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmImport) { Text(stringResource(R.string.import_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelImport) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
