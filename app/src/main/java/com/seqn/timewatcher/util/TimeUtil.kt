package com.seqn.timewatcher.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class StatPeriod { DAY, WEEK, MONTH, YEAR }

object TimeUtil {
    private fun zone(): ZoneId = ZoneId.systemDefault()
    private fun date(epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone()).toLocalDate()
    private fun toMs(date: LocalDate): Long = date.atStartOfDay(zone()).toInstant().toEpochMilli()

    fun periodStart(period: StatPeriod, anchor: Long): Long {
        val d = date(anchor)
        val start = when (period) {
            StatPeriod.DAY -> d
            StatPeriod.WEEK -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            StatPeriod.MONTH -> d.withDayOfMonth(1)
            StatPeriod.YEAR -> d.withDayOfYear(1)
        }
        return toMs(start)
    }

    fun periodEnd(period: StatPeriod, start: Long): Long {
        val d = date(start)
        val end = when (period) {
            StatPeriod.DAY -> d.plusDays(1)
            StatPeriod.WEEK -> d.plusWeeks(1)
            StatPeriod.MONTH -> d.plusMonths(1)
            StatPeriod.YEAR -> d.plusYears(1)
        }
        return toMs(end)
    }

    fun shift(period: StatPeriod, start: Long, delta: Int): Long {
        val d = date(start)
        val moved = when (period) {
            StatPeriod.DAY -> d.plusDays(delta.toLong())
            StatPeriod.WEEK -> d.plusWeeks(delta.toLong())
            StatPeriod.MONTH -> d.plusMonths(delta.toLong())
            StatPeriod.YEAR -> d.plusYears(delta.toLong())
        }
        return toMs(moved)
    }

    // ---- formatting ----
    private val mdFmt = DateTimeFormatter.ofPattern("M月d日", Locale.ROOT)
    private val ymdFmt = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.ROOT)
    private val ymFmt = DateTimeFormatter.ofPattern("yyyy年M月", Locale.ROOT)
    private val yFmt = DateTimeFormatter.ofPattern("yyyy年", Locale.ROOT)

    fun periodLabel(period: StatPeriod, start: Long, endCapped: Long): String = when (period) {
        StatPeriod.DAY -> date(start).format(ymdFmt)
        StatPeriod.WEEK -> "${date(start).format(mdFmt)} – ${date(endCapped - 1).format(mdFmt)}"
        StatPeriod.MONTH -> date(start).format(ymFmt)
        StatPeriod.YEAR -> date(start).format(yFmt)
    }

    /** "X时Y分" (or "Y分"); seconds dropped. */
    fun formatHm(ms: Long): String {
        if (ms < 0) return "0分"
        val totalMin = (ms + 30_000) / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> "${h}时${m}分"
            else -> "${m}分"
        }
    }

    /** "X.X时" for compact stat cards. */
    fun formatHours1(ms: Long): String = "%.1f时".format(ms.toDouble() / 3_600_000.0)

    /** "MM:SS" countdown, clamped to >= 0. */
    fun formatClock(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }

    /** Wall-clock "H:mm" (24h) for the given epoch-millis, e.g. 14:05. */
    fun formatWallClock(ms: Long): String {
        val m = Instant.ofEpochMilli(ms).atZone(zone()).toLocalTime()
        return "%d:%02d".format(m.hour, m.minute)
    }

    /** Short elapsed like "1时5分" or "12分" or "45秒". */
    fun formatElapsed(ms: Long): String {
        if (ms < 60_000) return "${ms / 1000}秒"
        return formatHm(ms)
    }

    /** weekday short label (一/二/…) for a date, used in weekly charts. */
    fun weekdayShort(epochMs: Long): String =
        date(epochMs).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.CHINESE)

    fun weekdayShort(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.CHINESE)
}
