package com.seqn.timewatcher.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeUtilTest {

    @Test
    fun `formatHm produces hours and minutes`() {
        assertThat(TimeUtil.formatHm(3 * 3_600_000L + 30 * 60_000L)).isEqualTo("3时30分")
        assertThat(TimeUtil.formatHm(0)).isEqualTo("0分")
        assertThat(TimeUtil.formatHm(45 * 60_000L)).isEqualTo("45分")
    }

    @Test
    fun `formatClock clamps and zero pads`() {
        assertThat(TimeUtil.formatClock(90_000L)).isEqualTo("01:30")
        assertThat(TimeUtil.formatClock(0)).isEqualTo("00:00")
        assertThat(TimeUtil.formatClock(-5_000L)).isEqualTo("00:00")
    }

    @Test
    fun `formatHours1 one decimal`() {
        assertThat(TimeUtil.formatHours1(3 * 3_600_000L + 36 * 60_000L)).isEqualTo("3.6时")
    }

    @Test
    fun `day period end is start plus 24h and shift is consistent`() {
        val now = 1_700_000_000_000L
        val start = TimeUtil.periodStart(StatPeriod.DAY, now)
        val end = TimeUtil.periodEnd(StatPeriod.DAY, start)
        assertThat(end - start).isEqualTo(24 * 3_600_000L)
        assertThat(TimeUtil.shift(StatPeriod.DAY, start, 1)).isEqualTo(end)
        assertThat(TimeUtil.shift(StatPeriod.DAY, start, -1)).isEqualTo(start - 24 * 3_600_000L)
    }

    @Test
    fun `month shift handles month boundaries`() {
        val now = 1_700_000_000_000L
        val start = TimeUtil.periodStart(StatPeriod.MONTH, now)
        val next = TimeUtil.shift(StatPeriod.MONTH, start, 1)
        assertThat(TimeUtil.periodEnd(StatPeriod.MONTH, start)).isEqualTo(next)
    }
}
