package com.seqn.timewatcher.pomodoro

/** Abstraction over scheduling the pomodoro-end alarm, so the Repository can depend on it
 *  without a circular dependency on the concrete scheduler/receivers. */
interface PomodoroAlarm {
    fun schedule(endTs: Long)
    fun cancel()
}
