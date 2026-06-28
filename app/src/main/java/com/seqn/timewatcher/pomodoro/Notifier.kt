package com.seqn.timewatcher.pomodoro

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.seqn.timewatcher.MainActivity
import com.seqn.timewatcher.R

object Notifier {
    const val CHANNEL_ID = "pomodoro_end"
    private const val NOTIF_ID = 7725

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

    fun postPomodoroEnd(context: Context, exitStateName: String) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return // no permission: state still switches + ringtone still plays; just no system notification
        }
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(context.getString(R.string.notif_pomodoro_title))
            .setContentText(context.getString(R.string.notif_pomodoro_text, exitStateName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
    }
}
