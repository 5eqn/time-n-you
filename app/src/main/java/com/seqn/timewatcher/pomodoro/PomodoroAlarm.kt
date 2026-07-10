package com.seqn.timewatcher.pomodoro

/** Abstraction over scheduling the pomodoro-end alarm, so the Repository can depend on it
 *  without a circular dependency on the concrete scheduler/receivers. */
interface PomodoroAlarm {
    /** Whether the platform currently guarantees exact alarm delivery. */
    fun canScheduleExactAlarms(): Boolean

    /** Arms the one-shot end-of-pomodoro alarm. [isTest] marks a debug test fire so the ringer
     *  service knows to ring even when no real pomodoro is pending. Returns true only when the
     *  exact alarm was accepted by the platform. */
    fun schedule(endTs: Long, isTest: Boolean = false): Boolean
    fun cancel()
}
