package com.seqn.timewatcher.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.seqn.timewatcher.data.repo.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when the scheduled pomodoro ends — possibly long after the app was killed.
 *  Reconciles state (switching to the exit state if the pomodoro expired), then rings + notifies. */
class PomodoroReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "timewatcher:pomodoro_end",
        ).apply { acquire(20_000L) }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AppGraph.repository(context)
                val exit = repo.reconcilePomodoro(System.currentTimeMillis())
                if (exit != null) {
                    Notifier.postPomodoroEnd(context, exit.name)
                    Ringer.playFor(context, 8_000L)
                }
            } catch (_: Throwable) {
                // a receiver must never crash
            } finally {
                try { if (wakeLock.isHeld) wakeLock.release() } catch (_: Throwable) {}
                pendingResult.finish()
            }
        }
    }
}
