package com.seqn.timewatcher

import android.app.Application
import com.seqn.timewatcher.data.repo.AppGraph
import com.seqn.timewatcher.pomodoro.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimeWatcherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
        CoroutineScope(Dispatchers.IO).launch {
            AppGraph.repository(this@TimeWatcherApp).seedDefaultsIfEmpty()
        }
    }
}
