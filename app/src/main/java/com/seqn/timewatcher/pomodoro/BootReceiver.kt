package com.seqn.timewatcher.pomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seqn.timewatcher.data.repo.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** After a reboot, AlarmManager alarms are cleared. Re-arm a still-pending pomodoro, or
 *  reconcile immediately if it ended while the device was off. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppGraph.repository(context).reconcileAndArm(System.currentTimeMillis())
            } catch (_: Throwable) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
