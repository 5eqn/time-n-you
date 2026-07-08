package com.seqn.timewatcher.stats

import com.seqn.timewatcher.data.model.Category
import com.seqn.timewatcher.data.model.StateEvent
import com.seqn.timewatcher.util.StatPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

data class StateStat(val stateId: String, val stateName: String, val category: Category, val ms: Long)
data class DaySegment(val startOffsetMs: Long, val endOffsetMs: Long, val stateId: String, val category: Category)
data class DayStat(
    val date: LocalDate,
    val effectiveMs: Long,
    val wasteMs: Long,
    val restMs: Long,
    val availableMs: Long,
    val segments: List<DaySegment>,
)
data class StatsResult(
    val totalMs: Long,
    val restMs: Long,
    val effectiveMs: Long,
    val wasteMs: Long,
    val availableMs: Long,
    val utilization: Double,
    val isExcellent: Boolean,
    val effectiveThresholdMs: Long,
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
        period: StatPeriod = StatPeriod.DAY,
        targetRatio: Double = 0.5,
        targetMs: Long = 7L * 3_600_000L,
    ): StatsResult {
        val zone = ZoneId.systemDefault()
        val cap = minOf(now, periodEnd)

        // Pre-seed per-day buckets for every day in range (so charts show empty days too).
        val perDayMap = LinkedHashMap<LocalDate, DayAcc>()
        var dayCursor = Instant.ofEpochMilli(periodStart).atZone(zone).toLocalDate()
        while (dayCursor.atStartOfDay(zone).toInstant().toEpochMilli() < cap) {
            perDayMap[dayCursor] = DayAcc()
            dayCursor = dayCursor.plusDays(1)
        }

        if (events.isEmpty()) {
            val thresholdMs = effectiveThresholdMs(period, 0L, targetMs)
            return StatsResult(0, 0, 0, 0, 0, 0.0, false, thresholdMs, emptyList(), perDayMap.toDayStats())
        }

        // events are already timestamp-ascending (DAO-ordered / windowed); no re-sort needed.
        var total = 0L
        var rest = 0L
        var effective = 0L
        var waste = 0L
        val perStateMap = LinkedHashMap<String, StateStat>()

        for (i in events.indices) {
            val e = events[i]
            val end = if (i + 1 < events.size) events[i + 1].timestamp else now
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

            // per-day split at local midnight — mutate the accumulator in place (no copy()).
            splitByDay(a, b, zone).forEach { slice ->
                val day = slice.date
                val d = slice.durationMs
                val acc = perDayMap.getOrPut(day) { DayAcc() }
                when (e.category) {
                    Category.REST -> acc.restMs += d
                    Category.EFFECTIVE -> { acc.effectiveMs += d; acc.availableMs += d }
                    Category.WASTE -> { acc.wasteMs += d; acc.availableMs += d }
                }
                acc.addSegment(slice.startOffsetMs, slice.endOffsetMs, e.stateId, e.category)
            }
        }

        val available = (total - rest).coerceAtLeast(0L)
        val utilization = if (available > 0) effective.toDouble() / available else 0.0
        val thresholdMs = effectiveThresholdMs(period, total, targetMs)
        val isExcellent = utilization >= targetRatio && effective > thresholdMs

        return StatsResult(
            totalMs = total,
            restMs = rest,
            effectiveMs = effective,
            wasteMs = waste,
            availableMs = available,
            utilization = utilization,
            isExcellent = isExcellent,
            effectiveThresholdMs = thresholdMs,
            perState = perStateMap.values.sortedByDescending { it.ms },
            perDay = perDayMap.toDayStats(),
        )
    }

    /** Mutable per-day accumulator — avoids a DayStat copy() on every event segment. */
    private class DayAcc(
        var effectiveMs: Long = 0L,
        var wasteMs: Long = 0L,
        var restMs: Long = 0L,
        var availableMs: Long = 0L,
        val segments: MutableList<DaySegment> = ArrayList(),
    ) {
        fun addSegment(startOffsetMs: Long, endOffsetMs: Long, stateId: String, category: Category) {
            val last = segments.lastOrNull()
            if (last != null && last.endOffsetMs == startOffsetMs && last.stateId == stateId && last.category == category) {
                segments[segments.lastIndex] = last.copy(endOffsetMs = endOffsetMs)
            } else {
                segments += DaySegment(startOffsetMs, endOffsetMs, stateId, category)
            }
        }
    }

    private fun LinkedHashMap<LocalDate, DayAcc>.toDayStats(): List<DayStat> =
        entries.sortedBy { it.key }.map { (date, acc) ->
            DayStat(date, acc.effectiveMs, acc.wasteMs, acc.restMs, acc.availableMs, acc.segments.toList())
        }

    private data class DaySlice(
        val date: LocalDate,
        val startOffsetMs: Long,
        val endOffsetMs: Long,
    ) {
        val durationMs: Long get() = endOffsetMs - startOffsetMs
    }

    private fun splitByDay(a: Long, b: Long, zone: ZoneId): List<DaySlice> {
        val out = ArrayList<DaySlice>()
        var cur = a
        while (cur < b) {
            val zoned = Instant.ofEpochMilli(cur).atZone(zone)
            val day = zoned.toLocalDate()
            val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val segEnd = minOf(b, dayEnd)
            val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
            out += DaySlice(day, cur - dayStart, segEnd - dayStart)
            cur = segEnd
        }
        return out
    }

    private fun effectiveThresholdMs(period: StatPeriod, totalMs: Long, dayTargetMs: Long): Long =
        if (period == StatPeriod.DAY) {
            dayTargetMs
        } else {
            roundToTenthHour(totalMs / 4.0)
        }

    private fun roundToTenthHour(ms: Double): Long =
        ((ms / 360_000.0).roundToLong() * 360_000L).coerceAtLeast(0L)
}
