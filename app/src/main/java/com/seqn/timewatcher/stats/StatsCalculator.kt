package com.seqn.timewatcher.stats

import com.seqn.timewatcher.data.model.Category
import com.seqn.timewatcher.data.model.StateEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StateStat(val stateId: String, val stateName: String, val category: Category, val ms: Long)
data class DayStat(val date: LocalDate, val effectiveMs: Long, val wasteMs: Long, val restMs: Long, val availableMs: Long)
data class StatsResult(
    val totalMs: Long,
    val restMs: Long,
    val effectiveMs: Long,
    val wasteMs: Long,
    val availableMs: Long,
    val utilization: Double,
    val isExcellent: Boolean,
    val perState: List<StateStat>,
    val perDay: List<DayStat>,
)

/**
 * Pure aggregation over the append-only event log for a [periodStart, periodEnd) window.
 *
 * Semantics:
 *  - available time = total - REST (睡觉/吃饭)
 *  - effective time = EFFECTIVE (主业/爱好 only)
 *  - waste time     = WASTE (运动/摆烂)
 *  - utilization    = effective / available; excellent when >= 50% AND effective > 7h
 */
object StatsCalculator {

    fun compute(
        events: List<StateEvent>,
        periodStart: Long,
        periodEnd: Long,
        now: Long,
        targetRatio: Double = 0.5,
        targetMs: Long = 7L * 3_600_000L,
    ): StatsResult {
        val zone = ZoneId.systemDefault()
        val cap = minOf(now, periodEnd)

        // Pre-seed per-day buckets for every day in range (so charts show empty days too).
        val perDayMap = LinkedHashMap<LocalDate, DayStat>()
        var dayCursor = Instant.ofEpochMilli(periodStart).atZone(zone).toLocalDate()
        while (dayCursor.atStartOfDay(zone).toInstant().toEpochMilli() < cap) {
            perDayMap[dayCursor] = DayStat(dayCursor, 0, 0, 0, 0)
            dayCursor = dayCursor.plusDays(1)
        }

        if (events.isEmpty()) {
            return StatsResult(0, 0, 0, 0, 0, 0.0, false, emptyList(), perDayMap.values.toList())
        }

        val sorted = events.sortedBy { it.timestamp }
        var total = 0L
        var rest = 0L
        var effective = 0L
        var waste = 0L
        val perStateMap = LinkedHashMap<String, StateStat>()

        for (i in sorted.indices) {
            val e = sorted[i]
            val end = if (i + 1 < sorted.size) sorted[i + 1].timestamp else now
            val a = maxOf(e.timestamp, periodStart)
            val b = minOf(end, cap)
            if (a >= b) continue
            val dur = b - a
            total += dur
            when (e.category) {
                Category.REST -> rest += dur
                Category.EFFECTIVE -> effective += dur
                Category.WASTE -> waste += dur
            }

            // per-state aggregation
            val prev = perStateMap[e.stateId]
            perStateMap[e.stateId] = StateStat(e.stateId, e.stateName, e.category, (prev?.ms ?: 0) + dur)

            // per-day split at local midnight
            splitByDay(a, b, zone).forEach { (day, d) ->
                val ds = perDayMap[day] ?: DayStat(day, 0, 0, 0, 0).also { perDayMap[day] = it }
                perDayMap[day] = when (e.category) {
                    Category.REST -> ds.copy(restMs = ds.restMs + d)
                    Category.EFFECTIVE -> ds.copy(effectiveMs = ds.effectiveMs + d, availableMs = ds.availableMs + d)
                    Category.WASTE -> ds.copy(wasteMs = ds.wasteMs + d, availableMs = ds.availableMs + d)
                }
            }
        }

        val available = (total - rest).coerceAtLeast(0L)
        val utilization = if (available > 0) effective.toDouble() / available else 0.0
        val isExcellent = utilization >= targetRatio && effective >= targetMs

        return StatsResult(
            totalMs = total,
            restMs = rest,
            effectiveMs = effective,
            wasteMs = waste,
            availableMs = available,
            utilization = utilization,
            isExcellent = isExcellent,
            perState = perStateMap.values.sortedByDescending { it.ms },
            perDay = perDayMap.values.sortedBy { it.date },
        )
    }

    private fun splitByDay(a: Long, b: Long, zone: ZoneId): List<Pair<LocalDate, Long>> {
        val out = ArrayList<Pair<LocalDate, Long>>()
        var cur = a
        while (cur < b) {
            val day = Instant.ofEpochMilli(cur).atZone(zone).toLocalDate()
            val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val segEnd = minOf(b, dayEnd)
            out += day to (segEnd - cur)
            cur = segEnd
        }
        return out
    }
}
