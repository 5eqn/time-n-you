package com.seqn.timewatcher

import android.app.Application
import android.os.Build
import com.seqn.timewatcher.data.repo.AppGraph
import com.seqn.timewatcher.pomodoro.AlarmDebugLog
import com.seqn.timewatcher.pomodoro.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimeWatcherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmDebugLog.init(this)
        // Recorded at every process creation. If the alarm fires after the app was killed, a fresh
        // PROCESS entry appears right before the FIRE entries — proof of a cold start.
        AlarmDebugLog.log(
            "PROCESS", AlarmDebugLog.STATUS_INFO, "App process started",
            mapOf(
                "sdkInt" to Build.VERSION.SDK_INT.toString(),
                "processName" to processNameCompat(),
            ),
        )
        Notifier.ensureChannel(this)
        CoroutineScope(Dispatchers.IO).launch {
            AppGraph.repository(this@TimeWatcherApp).initialize()
        }
    }

    private fun processNameCompat(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) getProcessName() else "(pre-28)"
}
