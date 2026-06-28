package com.seqn.timewatcher.pomodoro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Schedules the pomodoro-end alarm via AlarmManager so it fires even if the app is killed.
 *  Uses an exact alarm when permitted; otherwise falls back to an inexact alarm. The
 *  reconcile-on-open path corrects timing regardless. */
class PomodoroScheduler(private val context: Context) : PomodoroAlarm {

    override fun schedule(endTs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTs, pendingIntent())
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTs, pendingIntent())
        }
    }

    override fun cancel() {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, PomodoroReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val REQUEST_CODE = 7025
    }
}
