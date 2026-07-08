package com.seqn.timewatcher.stats

import com.google.common.truth.Truth.assertThat
import com.seqn.timewatcher.data.model.Category
import com.seqn.timewatcher.data.model.StateEvent
import org.junit.Test

private const val H = 3_600_000L
private const val DAY = 24 * H

class StatsCalculatorTest {

    private fun ev(id: Long, sid: String, name: String, cat: Category, ts: Long): StateEvent =
        StateEvent(id, sid, name, cat, ts, null, false)

    @Test
    fun `utilization excludes rest and exercise is waste, not excellent under 7h`() {
        // 0–8 sleep, 8–9 eat, 9–12 work, 12–13 slack, 13–14 exercise, 14–17 hobby (tail to now=17)
        val now = 17 * H
        val events = listOf(
            ev(1, "sleep", "睡觉", Category.REST, 0),
            ev(2, "eat", "吃饭", Category.REST, 8 * H),
            ev(3, "work", "主业", Category.EFFECTIVE, 9 * H),
            ev(4, "slack", "摆烂", Category.WASTE, 12 * H),
            ev(5, "exercise", "运动", Category.WASTE, 13 * H),
            ev(6, "hobby", "爱好", Category.EFFECTIVE, 14 * H),
        )
        val r = StatsCalculator.compute(events, 0, DAY, now)

        assertThat(r.restMs).isEqualTo(9 * H)       // sleep 8 + eat 1
        assertThat(r.effectiveMs).isEqualTo(6 * H)  // work 3 + hobby 3
        assertThat(r.wasteMs).isEqualTo(2 * H)      // slack 1 + exercise 1
        assertThat(r.availableMs).isEqualTo(8 * H)  // total 17 - rest 9
        assertThat(r.utilization).isWithin(1e-6).of(6.0 / 8.0) // 75%
        assertThat(r.isExcellent).isFalse()         // effective < 7h
    }

    @Test
    fun `excellent when at least 50 pct and over 7h effective`() {
        // effective 7.5h, available 9.5h
        val now = 18.5.toLong().let { (18 * H) + (30 * 60_000L) }
        val events = listOf(
            ev(1, "sleep", "睡觉", Category.REST, 0),
            ev(2, "eat", "吃饭", Category.REST, 8 * H),
            ev(3, "work", "主业", Category.EFFECTIVE, 9 * H),
            ev(4, "slack", "摆烂", Category.WASTE, 12 * H + 30 * 60_000L),
            ev(5, "exercise", "运动", Category.WASTE, 13 * H + 30 * 60_000L),
            ev(6, "hobby", "爱好", Category.EFFECTIVE, 14 * H + 30 * 60_000L),
        )
        val r = StatsCalculator.compute(events, 0, DAY, now)

        assertThat(r.effectiveMs).isEqualTo((7 * H) + (30 * 60_000L)) // 7.5h
        assertThat(r.utilization).isAtLeast(0.5)
        assertThat(r.isExcellent).isTrue()
    }

    @Test
    fun `week excellent threshold is rounded quarter of tracked time and remains strict greater than`() {
        // total tracked = 32h => threshold = 8.0h
        val now = 32 * H
        val events = listOf(
            ev(1, "sleep", "睡觉", Category.REST, 0),
            ev(2, "work", "主业", Category.EFFECTIVE, 8 * H),
            ev(3, "slack", "摆烂", Category.WASTE, 16 * H),
        )
        val r = StatsCalculator.compute(
            events = events,
            periodStart = 0,
            periodEnd = 7 * DAY,
            now = now,
            period = com.seqn.timewatcher.util.StatPeriod.WEEK,
        )

        assertThat(r.effectiveThresholdMs).isEqualTo(8 * H)
        assertThat(r.effectiveMs).isEqualTo(8 * H)
        assertThat(r.isExcellent).isFalse()
    }

    @Test
    fun `week excellent threshold rounds to one decimal hour`() {
        // total tracked = 25h => threshold = 6.25h, rounded to 6.3h
        val now = 25 * H
        val events = listOf(
            ev(1, "work", "主业", Category.EFFECTIVE, 0),
            ev(2, "slack", "摆烂", Category.WASTE, (6 * H) + (24 * 60_000L)),
        )
        val r = StatsCalculator.compute(
            events = events,
            periodStart = 0,
            periodEnd = 7 * DAY,
            now = now,
            period = com.seqn.timewatcher.util.StatPeriod.WEEK,
        )

        assertThat(r.effectiveThresholdMs).isEqualTo((6 * H) + (18 * 60_000L))
    }

    @Test
    fun `overnight interval splits across midnight into both days`() {
        // Align to a local midnight so the split happens at a known boundary in any timezone.
        val zone = java.time.ZoneId.systemDefault()
        val base = java.time.LocalDate.of(2024, 1, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        // sleep from day1 23:00 to day2 07:00; 2-day period
        val events = listOf(
            ev(1, "sleep", "睡觉", Category.REST, base + 23 * H),
            ev(2, "eat", "吃饭", Category.REST, base + 31 * H),
        )
        val now = base + 31 * H
        val r = StatsCalculator.compute(events, base, base + 2 * DAY, now)

        assertThat(r.restMs).isEqualTo(8 * H)
        assertThat(r.perDay).hasSize(2)
        assertThat(r.perDay[0].restMs).isEqualTo(1 * H) // 23:00 → 00:00
        assertThat(r.perDay[1].restMs).isEqualTo(7 * H) // 00:00 → 07:00
    }

    @Test
    fun `empty events yield zero utilization`() {
        val r = StatsCalculator.compute(emptyList(), 0, DAY, 12 * H)
        assertThat(r.totalMs).isEqualTo(0)
        assertThat(r.utilization).isEqualTo(0.0)
        assertThat(r.perState).isEmpty()
    }
}
