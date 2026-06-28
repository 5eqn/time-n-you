package com.seqn.timewatcher.data.repo

import android.content.Context
import com.seqn.timewatcher.data.db.AppDatabase
import com.seqn.timewatcher.pomodoro.PomodoroScheduler

/** Lightweight service locator so ViewModels and BroadcastReceivers can reach the Repository
 *  without a DI framework. The Repository holds only DAO references + the alarm, so creating
 *  one per call is cheap; the underlying Room database is a singleton. */
object AppGraph {
    fun repository(context: Context): Repository {
        val db = AppDatabase.get(context)
        return Repository(db, PomodoroScheduler(context.applicationContext))
    }
}
