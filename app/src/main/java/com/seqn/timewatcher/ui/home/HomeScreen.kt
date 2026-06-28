package com.seqn.timewatcher.ui.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seqn.timewatcher.R
import com.seqn.timewatcher.data.model.StateDef
import com.seqn.timewatcher.data.model.StateEvent
import com.seqn.timewatcher.tips.Tips
import com.seqn.timewatcher.tips.TipId
import com.seqn.timewatcher.ui.components.LabeledBar
import com.seqn.timewatcher.ui.components.SectionCard
import com.seqn.timewatcher.ui.components.StateChip
import com.seqn.timewatcher.ui.components.rememberNowTick
import com.seqn.timewatcher.util.ColorUtil
import com.seqn.timewatcher.util.TimeUtil
import kotlin.math.roundToInt

@Composable
fun HomeScreen() {
    val vm: HomeViewModel = viewModel()
    val defs by vm.stateDefs.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val today by vm.todayStats.collectAsStateWithLifecycle()
    val tipEnabled by vm.tipEnabled.collectAsStateWithLifecycle()
    val now by rememberNowTick(1_000)
    val context = LocalContext.current

    val latest = events.lastOrNull()
    val currentDef: StateDef? = latest?.let { l -> defs.firstOrNull { it.id == l.stateId } }
    val elapsed = latest?.let { now - it.timestamp } ?: 0L
    val remaining = latest?.pomodoroEndTs?.let { it - now } ?: Long.MAX_VALUE
    val pomodoroActive = latest != null && latest.pomodoroEndTs != null && remaining > 0
    val maxStateMs = today.perState.maxOfOrNull { it.ms }?.coerceAtLeast(1L) ?: 1L

    // Intercepting tip: holds the (tip, target stateId) awaiting user confirmation.
    var pendingTip by remember { mutableStateOf<Pair<TipId, String>?>(null) }
    var showChangelog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.missed.collect {
            Toast.makeText(context, R.string.missed_pomodoro_toast, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(Unit) { vm.changelog.collect { showChangelog = true } }

    val onStateTap: (StateDef) -> Unit = { def ->
        val tipId = Tips.stateTip[def.id]
        if (tipId != null && tipEnabled[tipId] != false) {
            pendingTip = tipId to def.id
        } else {
            vm.switchState(def.id)
        }
    }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            CurrentStateCard(
                latest = latest,
                currentDef = currentDef,
                elapsed = elapsed,
                remaining = remaining,
                pomodoroActive = pomodoroActive,
            )
        }
        item {
            SectionCard(stringResource(R.string.today_summary)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.today_utilization), style = MaterialTheme.typography.bodyLarge)
                    val ratio = today.utilization
                    Text(
                        "${(ratio * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (ratio >= 0.5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "${stringResource(R.string.effective_short)} ${TimeUtil.formatHours1(today.effectiveMs)}" +
                        "  ·  ${stringResource(R.string.available_short)} ${TimeUtil.formatHours1(today.availableMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (today.perState.isEmpty()) {
                    Text(stringResource(R.string.no_state_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    today.perState.forEach { st ->
                        LabeledBar(
                            label = st.stateName,
                            valueText = TimeUtil.formatHm(st.ms),
                            fraction = st.ms.toFloat() / maxStateMs.toFloat(),
                            color = ColorUtil.colorFor(st.stateId),
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.tap_to_switch),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        items(defs, key = { it.id }) { def ->
            StateChip(
                name = def.name,
                colorSeed = def.id,
                selected = latest?.stateId == def.id,
                showPomodoro = def.pomodoroEnabled,
                onClick = { onStateTap(def) },
            )
        }
    }

    pendingTip?.let { (tipId, stateId) ->
        val tip = Tips.get(tipId)
        AlertDialog(
            onDismissRequest = { pendingTip = null },
            title = { Text(tip.title, fontWeight = FontWeight.Bold) },
            text = { Text(tip.message) },
            confirmButton = {
                TextButton(onClick = { vm.switchState(stateId); pendingTip = null }) {
                    Text(stringResource(R.string.tip_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTip = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text(Tips.CHANGELOG_TITLE, fontWeight = FontWeight.Bold) },
            text = { Text(Tips.CHANGELOG) },
            confirmButton = {
                TextButton(onClick = { showChangelog = false }) { Text(stringResource(R.string.confirm)) }
            },
        )
    }
}

@Composable
private fun CurrentStateCard(
    latest: StateEvent?,
    currentDef: StateDef?,
    elapsed: Long,
    remaining: Long,
    pomodoroActive: Boolean,
) {
    val name = latest?.stateName ?: "—"
    val seed = currentDef?.id ?: latest?.stateId ?: "default"
    val bg = ColorUtil.colorFor(seed)
    val onColor = ColorUtil.OnFill
    Surface(
        color = bg,
        contentColor = onColor,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (latest == null) {
                Text(stringResource(R.string.no_state_yet))
            } else {
                Text(
                    "${stringResource(R.string.elapsed)}  ${TimeUtil.formatElapsed(elapsed)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (pomodoroActive) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Timer, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${stringResource(R.string.remaining)} ${TimeUtil.formatClock(remaining)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
