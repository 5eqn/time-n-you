package com.seqn.timewatcher.ui.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seqn.timewatcher.R
import com.seqn.timewatcher.data.model.Category
import com.seqn.timewatcher.data.model.StateDef
import com.seqn.timewatcher.pomodoro.AlarmDebugLog
import com.seqn.timewatcher.pomodoro.AlarmLogEntry
import com.seqn.timewatcher.pomodoro.formatted
import com.seqn.timewatcher.tips.TipId
import com.seqn.timewatcher.tips.Tips
import com.seqn.timewatcher.ui.components.SectionCard
import com.seqn.timewatcher.util.ColorUtil
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel()
    val defs by vm.stateDefs.collectAsStateWithLifecycle()
    val exitId by vm.exitStateId.collectAsStateWithLifecycle()
    val tipEnabled by vm.tipEnabled.collectAsStateWithLifecycle()
    val targets by vm.targets.collectAsStateWithLifecycle()
    val alarmLog by vm.alarmLog.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showReset by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showBackfill by remember { mutableStateOf(false) }
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
                    OutlinedButton(onClick = { showBackfill = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_backfill))
                    }
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

        // --- alarm debug (Vivo diagnostic) ---
        item {
            AlarmDebugSection(
                entries = alarmLog,
                onTest = { vm.testAlarm() },
                onClear = { vm.clearAlarmLog() },
            )
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
    if (showBackfill) {
        BackfillDialog(
            defs = defs,
            onConfirm = { stateId, timestamp ->
                vm.addManualEvent(stateId, timestamp)
                Toast.makeText(context, R.string.backfill_done, Toast.LENGTH_SHORT).show()
                showBackfill = false
            },
            onDismiss = { showBackfill = false },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackfillDialog(
    defs: List<StateDef>,
    onConfirm: (String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    var selectedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedStateId by remember(defs) { mutableStateOf(defs.firstOrNull()?.id.orEmpty()) }
    var stateExpanded by remember { mutableStateOf(false) }
    var showFutureError by remember { mutableStateOf(false) }
    val selectedStateName = defs.firstOrNull { it.id == selectedStateId }?.name ?: "—"
    val localDateTime = Instant.ofEpochMilli(selectedAt).atZone(zone).toLocalDateTime()

    fun updateSelectedAt(dateTime: LocalDateTime) {
        selectedAt = dateTime.atZone(zone).toInstant().toEpochMilli()
        showFutureError = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backfill_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(expanded = stateExpanded, onExpandedChange = { stateExpanded = it }) {
                    OutlinedTextField(
                        value = selectedStateName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.backfill_state)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(stateExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = stateExpanded, onDismissRequest = { stateExpanded = false }) {
                        defs.forEach { def ->
                            DropdownMenuItem(
                                text = { Text(def.name) },
                                onClick = {
                                    selectedStateId = def.id
                                    stateExpanded = false
                                },
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    updateSelectedAt(
                                        LocalDateTime.of(
                                            year,
                                            month + 1,
                                            dayOfMonth,
                                            localDateTime.hour,
                                            localDateTime.minute,
                                        ),
                                    )
                                },
                                localDateTime.year,
                                localDateTime.monthValue - 1,
                                localDateTime.dayOfMonth,
                            ).apply {
                                datePicker.maxDate = System.currentTimeMillis()
                            }.show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${stringResource(R.string.backfill_date)} ${formatBackfillDate(selectedAt, zone)}")
                    }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    updateSelectedAt(
                                        LocalDateTime.of(
                                            localDateTime.year,
                                            localDateTime.monthValue,
                                            localDateTime.dayOfMonth,
                                            hourOfDay,
                                            minute,
                                        ),
                                    )
                                },
                                localDateTime.hour,
                                localDateTime.minute,
                                true,
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${stringResource(R.string.backfill_time)} ${formatBackfillTime(selectedAt, zone)}")
                    }
                }
                if (showFutureError) {
                    Text(
                        stringResource(R.string.backfill_future_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = defs.isNotEmpty(),
                onClick = {
                    if (selectedAt > System.currentTimeMillis()) {
                        showFutureError = true
                    } else {
                        onConfirm(selectedStateId, selectedAt)
                    }
                },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
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

private val backfillDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val backfillTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatBackfillDate(ms: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(ms).atZone(zone).format(backfillDateFormatter)

private fun formatBackfillTime(ms: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(ms).atZone(zone).format(backfillTimeFormatter)

@Composable
private fun AlarmDebugSection(
    entries: List<AlarmLogEntry>,
    onTest: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    // Newest lines are appended at the bottom; keep the view pinned there.
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue)
    }

    SectionCard(stringResource(R.string.settings_alarm_debug)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.alarm_debug_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onTest, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.alarm_debug_test))
                }
                OutlinedButton(
                    onClick = {
                        copyLog(context)
                        Toast.makeText(context, R.string.alarm_debug_copied, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.alarm_debug_copy))
                }
            }
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.alarm_debug_clear))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.alarm_debug_log, entries.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // The persistent trace itself: monospace, status-coloured, scrolls within a fixed height.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .verticalScroll(scroll),
            ) {
                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.alarm_debug_empty),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        entries.forEach { entry ->
                            Text(
                                text = entry.formatted(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = when (entry.status) {
                                    AlarmDebugLog.STATUS_FAIL -> MaterialTheme.colorScheme.error
                                    AlarmDebugLog.STATUS_OK -> Color(0xFF1B7F3C)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun copyLog(context: Context) {
    val text = AlarmDebugLog.asText()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("alarm_debug_log", text))
}
