package com.seqn.timewatcher.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seqn.timewatcher.R
import com.seqn.timewatcher.stats.DayStat
import com.seqn.timewatcher.stats.StatsResult
import com.seqn.timewatcher.ui.components.LabeledBar
import com.seqn.timewatcher.ui.components.SectionCard
import com.seqn.timewatcher.ui.components.UtilizationGauge
import com.seqn.timewatcher.util.ColorUtil
import com.seqn.timewatcher.util.StatPeriod
import com.seqn.timewatcher.util.TimeUtil
import kotlin.math.roundToInt

@Composable
fun StatsScreen() {
    val vm: StatsViewModel = viewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val r = ui.result

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item { PeriodToggle(ui.period, vm::setPeriod) }
        item { PeriodNav(ui.label, ui.isCurrentPeriod, vm::prev, vm::next) }

        item {
            SectionCard(null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    UtilizationGauge(r.utilization, r.isExcellent, ui.targetRatio)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${stringResource(R.string.effective_time)} ${TimeUtil.formatHours1(r.effectiveMs)}" +
                        "  /  ${stringResource(R.string.available_time)} ${TimeUtil.formatHours1(r.availableMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "优秀标准：利用率 ≥ ${(ui.targetRatio * 100).roundToInt()}% 且 有效时间 > " +
                        "${(ui.targetMs.toDouble() / 3_600_000.0).let { "%.1f".format(it) }} 小时",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(R.string.effective_time), TimeUtil.formatHours1(r.effectiveMs), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatTile(stringResource(R.string.waste_time), TimeUtil.formatHours1(r.wasteMs), MaterialTheme.colorScheme.error, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stringResource(R.string.rest_time), TimeUtil.formatHours1(r.restMs), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                StatTile(stringResource(R.string.total_tracked), TimeUtil.formatHours1(r.totalMs), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            }
        }

        if (ui.period == StatPeriod.DAY && r.totalMs > 0) {
            item {
                val hours = r.restMs.toDouble() / 3_600_000.0
                val msg = when {
                    r.restMs < 10 * 3_600_000L ->
                        "休息偏少（${"%.1f".format(hours)} 小时）。建议把更多日常事务纳入「生活」，并保证休息、规律运动。"
                    r.restMs <= 14 * 3_600_000L ->
                        "休息时长合理（推荐接近 12 小时，当前 ${"%.1f".format(hours)} 小时）。"
                    else ->
                        "休息偏多（${"%.1f".format(hours)} 小时）。建议不要赖床、如厕时不玩手机、吃饭时不看视频。"
                }
                SectionCard("休息建议") {
                    Text(msg, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            SectionCard(stringResource(R.string.breakdown_title)) {
                if (r.perState.isEmpty()) {
                    Text("本周期暂无记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val maxMs = r.perState.maxOfOrNull { it.ms }?.coerceAtLeast(1L) ?: 1L
                    r.perState.forEach { st ->
                        LabeledBar(
                            label = st.stateName,
                            valueText = TimeUtil.formatHm(st.ms),
                            fraction = st.ms.toFloat() / maxMs.toFloat(),
                            color = ColorUtil.colorFor(st.stateId),
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        if (ui.period != StatPeriod.DAY) {
            item { SectionCard(stringResource(R.string.daily_breakdown_title)) { DailyBars(r.perDay) } }
        }
    }
}

@Composable
private fun PeriodToggle(current: StatPeriod, onSelect: (StatPeriod) -> Unit) {
    val items = listOf(
        StatPeriod.DAY to R.string.period_day,
        StatPeriod.WEEK to R.string.period_week,
        StatPeriod.MONTH to R.string.period_month,
        StatPeriod.YEAR to R.string.period_year,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (p, labelRes) ->
            FilterChip(
                selected = p == current,
                onClick = { onSelect(p) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

@Composable
private fun PeriodNav(label: String, isCurrent: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一个")
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onNext, enabled = !isCurrent) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一个")
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DailyBars(days: List<DayStat>) {
    if (days.isEmpty()) return
    val maxAvailable = (days.maxOfOrNull { it.availableMs } ?: 0L).coerceAtLeast(1L)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val effColor = MaterialTheme.colorScheme.primary
    val maxHeight = 120.dp
    val showLabels = days.size <= 31 // skip per-day labels for YEAR (too many)

    Column(Modifier.fillMaxWidth()) {
        // All bars drawn in one Canvas — one composable regardless of day count (vs ~2N Boxes).
        Canvas(Modifier.fillMaxWidth().height(maxHeight)) {
            val n = days.size
            val gap = 2.dp.toPx()
            val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
            val maxH = maxHeight.toPx()
            val bottom = size.height
            days.forEachIndexed { i, d ->
                val ratio = d.availableMs.toFloat() / maxAvailable.toFloat()
                val h = maxH * ratio.coerceAtLeast(0.02f)
                val x = i * (barW + gap)
                drawRect(trackColor, topLeft = Offset(x, bottom - h), size = Size(barW, h))
                if (d.availableMs > 0 && d.effectiveMs > 0) {
                    val effFrac = (d.effectiveMs.toFloat() / d.availableMs.toFloat()).coerceIn(0f, 1f)
                    drawRect(effColor, topLeft = Offset(x, bottom - h * effFrac), size = Size(barW, h * effFrac))
                }
            }
        }
        if (showLabels) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                days.forEachIndexed { i, d ->
                    Text(
                        if (i == days.lastIndex) "今" else d.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}
