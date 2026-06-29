package com.seqn.timewatcher.data.repo

import android.content.Context
import com.seqn.timewatcher.data.db.AppDatabase
import com.seqn.timewatcher.pomodoro.PomodoroScheduler

/** Lightweight service locator so ViewModels and BroadcastReceivers can reach the Repository
 *  without a DI framework. The Repository (and the Room database behind it) is a process-wide
 *  singleton, so we cache one instance instead of allocating on every call. */
object AppGraph {
    @Volatile
    private var cached: Repository? = null

    fun repository(context: Context): Repository = cached ?: synchronized(this) {
        cached ?: Repository(
            AppDatabase.get(context),
            PomodoroScheduler(context.applicationContext),
        ).also { cached = it }
    }
}
