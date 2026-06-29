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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.material3.Slider
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
import com.seqn.timewatcher.tips.TipId
import com.seqn.timewatcher.tips.Tips
import com.seqn.timewatcher.ui.components.SectionCard
import com.seqn.timewatcher.util.ColorUtil
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel()
    val defs by vm.stateDefs.collectAsStateWithLifecycle()
    val exitId by vm.exitStateId.collectAsStateWithLifecycle()
    val tipEnabled by vm.tipEnabled.collectAsStateWithLifecycle()
    val targets by vm.targets.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showReset by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<StateDef?>(null) }
    val defaultName = stringResource(R.string.new_state_default_name)

    val philosophyEnabled = tipEnabled[TipId.PHILOSOPHY] != false
    // Stable callbacks so StateEditorRow can skip recomposition when its def is unchanged.
    val onMove: (String, Int) -> Unit = remember(vm) { { id, delta -> vm.moveState(id, delta) } }
    val onUpdate: (StateDef) -> Unit = remember(vm) { { d -> vm.updateState(d) } }
    val requestDelete: (StateDef) -> Unit = remember { { d -> pendingDelete = d } }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            val json = vm.exportJson()
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
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
            Toast.makeText(context, if (ok) R.string.import_done else R.string.import_failed, Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        // --- state list editor (lazy + keyed: only visible rows compose on the first nav frame) ---
        item {
            Text(
                stringResource(R.string.settings_states),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        itemsIndexed(defs, key = { _, d -> d.id }) { index, def ->
            StateEditorRow(
                def = def,
                canMoveUp = index > 0,
                canMoveDown = index < defs.lastIndex,
                philosophyEnabled = philosophyEnabled,
                onMove = onMove,
                onDeleteRequest = requestDelete,
                onUpdate = onUpdate,
            )
        }
        item {
            OutlinedButton(onClick = { vm.addState(defaultName) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_add_state))
            }
        }

        // --- pomodoro ---
        item {
            SectionCard(stringResource(R.string.settings_pomodoro)) {
                ExitStateSelector(defs = defs, selectedId = exitId, philosophyEnabled = philosophyEnabled, onSelect = vm::setExit)
            }
        }

        // --- targets ---
        item {
            SectionCard(stringResource(R.string.settings_targets)) {
                val ratio = targets.first.toFloat()
                val hours = (targets.second / 3_600_000.0)
                Text(
                    "有效时间目标：${"%.1f".format(hours)} 小时",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = hours.toFloat().coerceIn(1f, 12f),
                    onValueChange = { vm.setTargetMs((it.toDouble() * 3_600_000).toLong()) },
                    valueRange = 1f..12f,
                    steps = 21,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "目标利用率：${(ratio * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = ratio.coerceIn(0.1f, 0.9f),
                    onValueChange = { vm.setTargetRatio(it.toDouble()) },
                    valueRange = 0.1f..0.9f,
                    steps = 15,
                )
            }
        }

        // --- tip management ---
        item {
            SectionCard(stringResource(R.string.settings_tips)) {
                Tips.manageable.forEach { tip ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(tip.settingLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(
                            checked = tipEnabled[tip.id] != false,
                            onCheckedChange = { vm.setTipEnabled(tip.id, it) },
                        )
                    }
                }
            }
        }

        // --- data ---
        item {
            SectionCard(null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { showReset = true }, modifier = Modifier.fillMaxWidth()) {
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
                    OutlinedButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth()) {
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    philosophyEnabled: Boolean,
    onMove: (String, Int) -> Unit,
    onDeleteRequest: (StateDef) -> Unit,
    onUpdate: (StateDef) -> Unit,
) {
    var name by remember(def.id) { mutableStateOf(def.name) }
    var minutes by remember(def.id) { mutableStateOf(def.pomodoroMinutes.toString()) }
    var note by remember(def.id) { mutableStateOf(def.note ?: "") }
    var pendingDef by remember(def.id) { mutableStateOf<StateDef?>(null) }

    // Only intercept the *transition into* an "effective but unmanaged" state.
    fun tryApply(newDef: StateDef) {
        val wasBad = Tips.triggersPhilosophy(def.category.name, def.pomodoroEnabled)
        val isBad = Tips.triggersPhilosophy(newDef.category.name, newDef.pomodoroEnabled)
        if (!wasBad && isBad && philosophyEnabled) {
            pendingDef = newDef
        } else {
            onUpdate(newDef)
        }
    }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMove(def.id, -1) }, enabled = canMoveUp) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "上移")
                }
                IconButton(onClick = { onMove(def.id, 1) }, enabled = canMoveDown) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "下移")
                }
                Box(Modifier.size(22.dp).clip(CircleShape).background(ColorUtil.colorFor(def.id)))
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; onUpdate(def.copy(name = it)) },
                    label = { Text(stringResource(R.string.state_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onDeleteRequest(def) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.state_delete))
                }
            }
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c ->
                    FilterChip(
                        selected = def.category == c,
                        onClick = { tryApply(def.copy(category = c)) },
                        label = { Text(categoryLabel(c)) },
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = def.pomodoroEnabled,
                        onCheckedChange = { tryApply(def.copy(pomodoroEnabled = it)) },
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
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it; onUpdate(def.copy(note = it.ifBlank { null })) },
                label = { Text(stringResource(R.string.state_note)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

    pendingDef?.let { pd ->
        AlertDialog(
            onDismissRequest = { pendingDef = null },
            title = { Text(Tips.philosophy.title, fontWeight = FontWeight.Bold) },
            text = { Text(Tips.philosophy.message) },
            confirmButton = {
                TextButton(onClick = { onUpdate(pd); pendingDef = null }) { Text(stringResource(R.string.tip_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDef = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExitStateSelector(
    defs: List<StateDef>,
    selectedId: String,
    philosophyEnabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<StateDef?>(null) }
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
                DropdownMenuItem(
                    text = { Text(d.name) },
                    onClick = {
                        expanded = false
                        // Item 15: setting an EFFECTIVE state as the pomodoro exit violates the design
                        // philosophy — confirm via the same philosophy tip first.
                        if (d.category == Category.EFFECTIVE && philosophyEnabled) {
                            pending = d
                        } else {
                            onSelect(d.id)
                        }
                    },
                )
            }
        }
    }
    pending?.let { pd ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(Tips.philosophy.title, fontWeight = FontWeight.Bold) },
            text = { Text(Tips.philosophy.message) },
            confirmButton = {
                TextButton(onClick = { onSelect(pd.id); pending = null }) { Text(stringResource(R.string.tip_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
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
