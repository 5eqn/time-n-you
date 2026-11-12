package com.seqn.timewatcher.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seqn.timewatcher.R
import com.seqn.timewatcher.data.model.Category
import com.seqn.timewatcher.stats.DayStat
import com.seqn.timewatcher.ui.components.LabeledBar
import com.seqn.timewatcher.ui.components.SectionCard
import com.seqn.timewatcher.ui.components.UtilizationGauge
import com.seqn.timewatcher.util.ColorUtil
import com.seqn.timewatcher.util.StatPeriod
import com.seqn.timewatcher.util.TimeUtil
import java.time.LocalDate
import java.time.YearMonth

private const val DAY_MS = 24L * 3_600_000L

private enum class BreakdownMode { SUMMARY, ACTUAL }

private data class MonthSummary(
    val label: String,
    val averageEffectiveMs: Long,
    val averageAvailableMs: Long,
)

@Composable
fun StatsScreen() {
    val vm: StatsViewModel = viewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val stateColors by vm.stateColors.collectAsStateWithLifecycle()
    val r = ui.result
    var breakdownMode by rememberSaveable { mutableStateOf(BreakdownMode.SUMMARY) }

    LazyColumn(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item { PeriodToggle(ui.period, vm::setPeriod) }
        item { PeriodNav(ui.label, ui.isCurrentPeriod, vm::prev, vm::next) }

        item {
            SectionCard(null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    UtilizationGauge(r.utilization, r.isExcellent, ui.lambdaLevel.targetRatio)
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
                    stringResource(
                        R.string.lambda_excellent_standard,
                        ui.lambdaLevel.value,
                        ui.lambdaLevel.displayPercent,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    stringResource(R.string.effective_time),
                    TimeUtil.formatHours1(r.effectiveMs),
                    MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f),
                )
                StatTile(
                    stringResource(R.string.waste_time),
                    TimeUtil.formatHours1(r.wasteMs),
                    MaterialTheme.colorScheme.error,
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    stringResource(R.string.rest_time),
                    TimeUtil.formatHours1(r.restMs),
                    MaterialTheme.colorScheme.secondary,
                    Modifier.weight(1f),
                )
                StatTile(
                    stringResource(R.string.total_tracked),
                    TimeUtil.formatHours1(r.totalMs),
                    MaterialTheme.colorScheme.tertiary,
                    Modifier.weight(1f),
                )
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
                            color = ColorUtil.colorFor(st.stateId, stateColors[st.stateId]),
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        if (ui.period != StatPeriod.DAY) {
            item {
                SectionCard(stringResource(R.string.daily_breakdown_title)) {
                    if (ui.period != StatPeriod.YEAR) {
                        BreakdownModeToggle(breakdownMode) { breakdownMode = it }
                        Spacer(Modifier.height(10.dp))
                    }
                    when {
                        ui.period == StatPeriod.YEAR -> YearSummaryRows(r.perDay)
                        breakdownMode == BreakdownMode.ACTUAL -> DailyTimelineRows(ui.period, r.perDay, stateColors)
                        else -> DailySummaryRows(ui.period, r.perDay)
                    }
                }
            }
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
            textAlign = TextAlign.Center,
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
@OptIn(ExperimentalMaterial3Api::class)
private fun BreakdownModeToggle(mode: BreakdownMode, onModeChange: (BreakdownMode) -> Unit) {
    val options = listOf(
        BreakdownMode.SUMMARY to "专注 / 可支配",
        BreakdownMode.ACTUAL to "实际使用",
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onModeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    inactiveBorderColor = MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DailySummaryRows(period: StatPeriod, days: List<DayStat>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        days.forEachIndexed { index, day ->
            SummaryRow(
                label = dayLabel(period, index, days, day.date),
                effectiveMs = day.effectiveMs,
                availableMs = day.availableMs,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, effectiveMs: Long, availableMs: Long) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val availableColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val effectiveColor = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(42.dp),
        )
        Canvas(Modifier.weight(1f).height(10.dp)) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(trackColor, cornerRadius = radius)
            val availableWidth = size.width * (availableMs.toFloat() / DAY_MS.toFloat()).coerceIn(0f, 1f)
            if (availableWidth > 0f) {
                drawRoundRect(
                    color = availableColor,
                    size = Size(availableWidth, size.height),
                    cornerRadius = radius,
                )
            }
            val effectiveWidth = size.width * (effectiveMs.toFloat() / DAY_MS.toFloat()).coerceIn(0f, 1f)
            if (effectiveWidth > 0f) {
                drawRoundRect(
                    color = effectiveColor,
                    size = Size(effectiveWidth, size.height),
                    cornerRadius = radius,
                )
            }
        }
        Text(
            "${TimeUtil.formatHours1(effectiveMs)} / ${TimeUtil.formatHours1(availableMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(78.dp),
        )
    }
}

@Composable
private fun DailyTimelineRows(period: StatPeriod, days: List<DayStat>, stateColors: Map<String, Int>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEachIndexed { index, day ->
            TimelineRow(
                label = dayLabel(period, index, days, day.date),
                day = day,
                stateColors = stateColors,
            )
        }
    }
}

@Composable
private fun TimelineRow(label: String, day: DayStat, stateColors: Map<String, Int>) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = onSurfaceVariant,
            modifier = Modifier.width(34.dp),
        )
        Text(
            "0",
            style = MaterialTheme.typography.labelSmall,
            color = onSurfaceVariant,
            modifier = Modifier.width(10.dp),
            textAlign = TextAlign.Start,
        )
        Canvas(Modifier.weight(1f).height(12.dp)) {
            val centerY = size.height / 2f
            val trackHeight = 1.dp.toPx()
            val activeHeight = size.height * 0.75f
            val restHeight = 2.dp.toPx()
            drawRect(
                color = trackColor,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width, trackHeight),
            )
            day.segments.forEach { segment ->
                val x = size.width * (segment.startOffsetMs.toFloat() / DAY_MS.toFloat()).coerceIn(0f, 1f)
                val endX = size.width * (segment.endOffsetMs.toFloat() / DAY_MS.toFloat()).coerceIn(0f, 1f)
                val width = (endX - x).coerceAtLeast(1f)
                val barHeight = if (segment.category == Category.REST) restHeight else activeHeight
                drawRect(
                    color = ColorUtil.colorFor(segment.stateId, stateColors[segment.stateId]),
                    topLeft = Offset(x, centerY - barHeight / 2f),
                    size = Size(width, barHeight),
                )
            }
        }
        Text(
            "24",
            style = MaterialTheme.typography.labelSmall,
            color = onSurfaceVariant,
            modifier = Modifier.width(18.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun YearSummaryRows(days: List<DayStat>) {
    val months = remember(days) {
        days.groupBy { YearMonth.from(it.date) }
            .toSortedMap()
            .map { (month, monthDays) ->
                MonthSummary(
                    label = "${month.monthValue}月",
                    averageEffectiveMs = monthDays.sumOf { it.effectiveMs } / monthDays.size.coerceAtLeast(1),
                    averageAvailableMs = monthDays.sumOf { it.availableMs } / monthDays.size.coerceAtLeast(1),
                )
            }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        months.forEach { month ->
            SummaryRow(
                label = month.label,
                effectiveMs = month.averageEffectiveMs,
                availableMs = month.averageAvailableMs,
            )
        }
    }
}

private fun dayLabel(period: StatPeriod, index: Int, days: List<DayStat>, date: LocalDate): String {
    if (index == days.lastIndex && date == LocalDate.now()) return "今"
    return when (period) {
        StatPeriod.WEEK -> "${TimeUtil.weekdayShort(date)} ${date.dayOfMonth}"
        StatPeriod.MONTH -> date.dayOfMonth.toString()
        else -> date.dayOfMonth.toString()
    }
}
