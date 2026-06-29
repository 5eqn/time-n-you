package com.seqn.timewatcher.pomodoro

/** Abstraction over scheduling the pomodoro-end alarm, so the Repository can depend on it
 *  without a circular dependency on the concrete scheduler/receivers. */
interface PomodoroAlarm {
    /** Arms the one-shot end-of-pomodoro alarm. [isTest] marks a debug test fire so the ringer
     *  service knows to ring even when no real pomodoro is pending. */
    fun schedule(endTs: Long, isTest: Boolean = false)
    fun cancel()
}
