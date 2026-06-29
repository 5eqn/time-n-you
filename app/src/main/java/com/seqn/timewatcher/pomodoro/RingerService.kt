package com.seqn.timewatcher.pomodoro

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat

/** Foreground service of type mediaPlayback that owns the pomodoro-end alarm sound. Started by
 *  [PomodoroReceiver] so the ring is audible whether the app is foregrounded, backgrounded, or the
 *  screen is locked — the system will not throttle or kill a mediaPlayback foreground service, and
 *  the USAGE_ALARM audio attributes let it play over DND / while the screen is off.
 *
 *  It also posts the full-screen-intent alarm notification (see [Notifier.buildAlarmNotification]),
 *  which surfaces over the lock screen via [AlarmActivity]. */
class RingerService : Service() {

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopSelf() }

    override fun onCreate() {
        super.onCreate()
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "timewatcher:ringer")
            .apply { setReferenceCounted(false); acquire(WAKE_TIMEOUT_MS) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val exitStateName = intent?.getStringExtra(EXTRA_EXIT_STATE_NAME) ?: ""
        // Always started via startForegroundService → promote to foreground immediately (within 5s).
        startForegroundCompat(exitStateName)

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (player == null) {
            acquireFocus()
            player = createPlayer()
            player?.start()
        }
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, RING_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopRunnable)
        try {
            player?.let { if (it.isPlaying) it.stop(); it.release() }
        } catch (_: Exception) {
        }
        player = null
        abandonFocus()
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(exitStateName: String) {
        val notification = Notifier.buildAlarmNotification(this, exitStateName)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }

    private fun acquireFocus() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .build()
                .also { am.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let {
                (getSystemService(AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it)
            }
        }
        focusRequest = null
    }

    private fun createPlayer(): MediaPlayer? {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return null
        return try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@RingerService, uri)
                isLooping = true
                prepare()
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val ACTION_STOP = "com.seqn.timewatcher.action.STOP_RINGER"
        const val EXTRA_EXIT_STATE_NAME = "exit_state_name"
        private const val NOTIF_ID = 7725
        private const val RING_MS = 8_000L
        private const val WAKE_TIMEOUT_MS = 30_000L

        fun intent(context: Context, exitStateName: String): Intent =
            Intent(context, RingerService::class.java)
                .putExtra(EXTRA_EXIT_STATE_NAME, exitStateName)
    }
}
