package com.seqn.timewatcher.pomodoro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.seqn.timewatcher.MainActivity

/** Schedules the pomodoro-end alarm via AlarmManager so it fires even if the app is killed.
 *  Uses [AlarmManager.setAlarmClock] — the strongest user-perceptible alarm primitive — which
 *  fires exactly on time even in Doze / with the screen off.
 *
 *  P0 (whakaara-faithful): the alarm's operation is a **service** [PendingIntent] targeting
 *  [RingerService], not a broadcast. When the alarm fires the system starts the foreground
 *  service directly as the alarm delivery — no receiver→startForegroundService hop. That hop is
 *  what aggressive OEMs (Vivo/Funtouch) reject; delivering straight to the service is the path
 *  whakaara ships and that works on Vivo. */
class PomodoroScheduler(private val context: Context) : PomodoroAlarm {

    override fun canScheduleExactAlarms(): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.canScheduleExactAlarms()
            } else {
                true
            }
        } catch (t: Throwable) {
            AlarmDebugLog.log(
                "PERMISSION", AlarmDebugLog.STATUS_FAIL, "Exact alarm capability check threw",
                mapOf("exception" to t.javaClass.simpleName, "message" to (t.message ?: "")),
            )
            false
        }
    }

    override fun schedule(endTs: Long, isTest: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val am = context.getSystemService(AlarmManager::class.java)
        if (am == null) {
            AlarmDebugLog.log("SCHEDULE", AlarmDebugLog.STATUS_FAIL, "AlarmManager unavailable")
            return false
        }
        val perms = permissionSnapshot(am)
        AlarmDebugLog.log(
            "SCHEDULE", AlarmDebugLog.STATUS_INFO,
            if (isTest) "Test alarm requested (5s)" else "Pomodoro alarm requested",
            mapOf(
                "triggerAt" to endTs.toString(),
                "deltaMs" to (endTs - now).toString(),
                "isTest" to isTest.toString(),
            ) + perms,
        )
        val canExact = perms["canScheduleExactAlarms"] == "true"
        AlarmDebugLog.log(
            "PERMISSION",
            if (canExact) AlarmDebugLog.STATUS_OK else AlarmDebugLog.STATUS_FAIL,
            "canScheduleExactAlarms=$canExact",
            if (canExact) emptyMap() else mapOf("advice" to "grant 'Alarms & reminders' in system settings"),
        )
        if (!canExact) {
            AlarmDebugLog.log(
                "SCHEDULE", AlarmDebugLog.STATUS_FAIL,
                "Exact alarm capability unavailable; refusing to schedule",
            )
            return false
        }
        try {
            val info = AlarmManager.AlarmClockInfo(endTs, showIntent())
            am.setAlarmClock(info, pendingIntent(isTest))
            AlarmDebugLog.log(
                "SCHEDULE", AlarmDebugLog.STATUS_OK, "setAlarmClock armed",
                mapOf(
                    "operation" to "PendingIntent.getService(RingerService)",
                    "requestCode" to REQUEST_CODE.toString(),
                    "triggerAt" to endTs.toString(),
                ),
            )
            return true
        } catch (e: SecurityException) {
            AlarmDebugLog.log(
                "SCHEDULE", AlarmDebugLog.STATUS_FAIL, "setAlarmClock threw SecurityException",
                mapOf("exception" to e.javaClass.simpleName, "message" to (e.message ?: "")),
            )
            return false
        } catch (t: Throwable) {
            AlarmDebugLog.log(
                "SCHEDULE", AlarmDebugLog.STATUS_FAIL, "setAlarmClock threw",
                mapOf("exception" to t.javaClass.simpleName, "message" to (t.message ?: "")),
            )
            return false
        }
    }

    override fun cancel() {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            am.cancel(pendingIntent(false))
        } catch (_: Throwable) {
        }
        AlarmDebugLog.log("SCHEDULE", AlarmDebugLog.STATUS_INFO, "Previous alarm cancelled")
    }

    private fun permissionSnapshot(am: AlarmManager): Map<String, String> {
        val pm = context.packageManager
        val pkg = context.packageName
        fun granted(perm: String) =
            pm.checkPermission(perm, pkg) == PackageManager.PERMISSION_GRANTED
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true // exact alarms are unrestricted before Android 12
        }
        return mapOf(
            "canScheduleExactAlarms" to canExact.toString(),
            "USE_EXACT_ALARM" to granted("android.permission.USE_EXACT_ALARM").toString(),
            "SCHEDULE_EXACT_ALARM" to granted("android.permission.SCHEDULE_EXACT_ALARM").toString(),
            "POST_NOTIFICATIONS" to granted("android.permission.POST_NOTIFICATIONS").toString(),
            "USE_FULL_SCREEN_INTENT" to granted("android.permission.USE_FULL_SCREEN_INTENT").toString(),
            "sdkInt" to Build.VERSION.SDK_INT.toString(),
        )
    }

    /** The alarm's "show" intent — the system surfaces this as the alarm's open target. whakaara
     *  passes a real one; the old code passed null. */
    private fun showIntent(): PendingIntent? = try {
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    } catch (_: Throwable) {
        null
    }

    private fun pendingIntent(isTest: Boolean): PendingIntent {
        val intent = Intent(context, RingerService::class.java)
            .putExtra(RingerService.EXTRA_TEST, isTest)
        return PendingIntent.getService(
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
