package com.seqn.timewatcher.pomodoro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.seqn.timewatcher.MainActivity
import com.seqn.timewatcher.R

object Notifier {
    const val CHANNEL_ID = "pomodoro_end"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val name = context.getString(R.string.notif_channel_pomodoro)
                val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = name
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 300, 500)
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    /** The full-screen-intent alarm notification posted by [RingerService] as its foreground
     *  notification. On a locked / screen-off device the system launches the full-screen intent
     *  ([AlarmActivity]); when the screen is on it shows as a high-priority heads-up. */
    fun buildAlarmNotification(context: Context, exitStateName: String, isTest: Boolean): Notification {
        ensureChannel(context)

        val fullScreenIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmActivity.EXTRA_EXIT_STATE_NAME, exitStateName)
                .putExtra(AlarmActivity.EXTRA_IS_TEST, isTest)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            context, 1,
            Intent(context, RingerService::class.java).setAction(RingerService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(
                context.getString(if (isTest) R.string.alarm_test_title else R.string.notif_pomodoro_title),
            )
            .setContentText(
                if (isTest) {
                    context.getString(R.string.alarm_test_message)
                } else {
                    context.getString(R.string.notif_pomodoro_text, exitStateName)
                },
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(R.drawable.ic_notify, context.getString(R.string.stop_alarm), stopIntent)
            .build()
    }
}
