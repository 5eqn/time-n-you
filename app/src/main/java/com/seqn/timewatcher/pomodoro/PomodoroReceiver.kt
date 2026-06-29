package com.seqn.timewatcher.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.seqn.timewatcher.data.repo.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when the scheduled pomodoro ends — possibly long after the app was killed.
 *  Reconciles state (switching to the exit state if the pomodoro expired), then starts the
 *  [RingerService] foreground service to ring + notify. The setAlarmClock-triggered broadcast is
 *  an allowed context for starting a foreground service from the background. */
class PomodoroReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "timewatcher:pomodoro_end",
        ).apply { acquire(5_000L) }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AppGraph.repository(context)
                val exit = repo.reconcilePomodoro(System.currentTimeMillis())
                if (exit != null) {
                    ContextCompat.startForegroundService(context, RingerService.intent(context, exit.name))
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
