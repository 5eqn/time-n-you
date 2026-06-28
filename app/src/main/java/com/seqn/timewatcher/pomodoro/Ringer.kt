package com.seqn.timewatcher.pomodoro

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import kotlinx.coroutines.delay

/** Plays the device's default alarm ringtone for a bounded duration. Called from the
 *  pomodoro receiver while holding a wake lock so the ring is audible. */
object Ringer {
    suspend fun playFor(context: Context, millis: Long) {
        val ringtone = try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            uri?.let { RingtoneManager.getRingtone(context, it) }
        } catch (_: Exception) {
            null
        } ?: return

        try {
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
            delay(millis)
        } catch (_: Exception) {
            // ignore playback errors
        } finally {
            try {
                if (ringtone.isPlaying) ringtone.stop()
            } catch (_: Exception) {
            }
        }
    }
}
