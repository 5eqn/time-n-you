package com.seqn.timewatcher.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seqn.timewatcher.R
import com.seqn.timewatcher.data.model.Category
import com.seqn.timewatcher.data.model.StateDef
import com.seqn.timewatcher.ui.components.SectionCard
import com.seqn.timewatcher.util.ColorUtil
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel()
    val defs by vm.stateDefs.collectAsStateWithLifecycle()
    val exitId by vm.exitStateId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showReset by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<StateDef?>(null) }
    val defaultName = stringResource(R.string.new_state_default_name)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            val json = vm.exportJson()
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
            Toast.makeText(context, R.string.export_done, Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scope.launch {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
            val ok = text != null && vm.importJson(text)
            Toast.makeText(
                context,
                if (ok) R.string.import_done else R.string.import_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            SectionCard(stringResource(R.string.settings_states)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    defs.forEach { def ->
                        StateEditorRow(
                            def = def,
                            onDeleteRequest = { pendingDelete = def },
                            onUpdate = vm::updateState,
                        )
                    }
                    OutlinedButton(
                        onClick = { vm.addState(defaultName) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_add_state))
                    }
                }
            }
        }

        item {
            SectionCard(stringResource(R.string.settings_pomodoro)) {
                ExitStateSelector(
                    defs = defs,
                    selectedId = exitId,
                    onSelect = vm::setExit,
                )
            }
        }

        item {
            SectionCard(null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { showReset = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_reset))
                    }
                    OutlinedButton(
                        onClick = { exportLauncher.launch("timewatcher_export.json") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_export))
                    }
                    OutlinedButton(
                        onClick = { showImport = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_import))
                    }
                }
            }
        }
    }

    if (showReset) {
        ConfirmDialog(
            title = stringResource(R.string.reset_confirm_title),
            message = stringResource(R.string.reset_confirm_msg),
            onConfirm = { vm.resetDefaults(); showReset = false },
            onDismiss = { showReset = false },
        )
    }
    if (showImport) {
        ConfirmDialog(
            title = stringResource(R.string.import_confirm_title),
            message = stringResource(R.string.import_confirm_msg),
            onConfirm = { showImport = false; importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
            onDismiss = { showImport = false },
        )
    }
    pendingDelete?.let { def ->
        ConfirmDialog(
            title = stringResource(R.string.delete_state_title),
            message = stringResource(R.string.delete_state_msg),
            onConfirm = { vm.deleteState(def.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun StateEditorRow(
    def: StateDef,
    onDeleteRequest: () -> Unit,
    onUpdate: (StateDef) -> Unit,
) {
    var name by remember(def.id) { mutableStateOf(def.name) }
    var minutes by remember(def.id) { mutableStateOf(def.pomodoroMinutes.toString()) }

    Surface(tonalElevation = 1.dp, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(ColorUtil.colorFor(def.id)))
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; onUpdate(def.copy(name = it)) },
                    label = { Text(stringResource(R.string.state_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDeleteRequest) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.state_delete))
                }
            }
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c ->
                    FilterChip(
                        selected = def.category == c,
                        onClick = { onUpdate(def.copy(category = c)) },
                        label = { Text(categoryLabel(c)) },
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = def.pomodoroEnabled,
                        onCheckedChange = { onUpdate(def.copy(pomodoroEnabled = it)) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.state_pomodoro_enable))
                }
                if (def.pomodoroEnabled) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { v ->
                            minutes = v
                            v.toIntOrNull()?.let { onUpdate(def.copy(pomodoroMinutes = it.coerceAtLeast(1))) }
                        },
                        label = { Text(stringResource(R.string.state_pomodoro_minutes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExitStateSelector(defs: List<StateDef>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = defs.firstOrNull { it.id == selectedId }?.name ?: "—"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_exit_state)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            defs.forEach { d ->
                DropdownMenuItem(text = { Text(d.name) }, onClick = { onSelect(d.id); expanded = false })
            }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun categoryLabel(c: Category): String = when (c) {
    Category.REST -> "休息"
    Category.EFFECTIVE -> "有效"
    Category.WASTE -> "无效"
}
