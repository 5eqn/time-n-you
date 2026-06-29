package com.seqn.timewatcher.pomodoro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Schedules the pomodoro-end alarm via AlarmManager so it fires even if the app is killed.
 *  Uses [AlarmManager.setAlarmClock] — the strongest user-perceptible alarm primitive — which
 *  fires exactly on time even in Doze / with the screen off and needs no exact-alarm permission.
 *  This is what lets the ring trigger reliably when the app is backgrounded or the device is locked. */
class PomodoroScheduler(private val context: Context) : PomodoroAlarm {

    override fun schedule(endTs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val info = AlarmManager.AlarmClockInfo(endTs, null)
        am.setAlarmClock(info, pendingIntent())
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
