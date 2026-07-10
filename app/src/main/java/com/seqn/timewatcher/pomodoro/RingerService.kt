package com.seqn.timewatcher.pomodoro

import android.app.Notification
import android.app.Service
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
import com.seqn.timewatcher.data.repo.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Foreground service of type mediaPlayback that owns the pomodoro-end alarm sound.
 *
 *  P0 (whakaara-faithful): started **directly by AlarmManager** via a service PendingIntent — the
 *  alarm delivery is the foreground-service start. The old code hopped through [PomodoroReceiver]
 *  and called startForegroundService from a background broadcast, which Vivo/Funtouch rejects.
 *
 *  On a fire it reconciles the pomodoro state (so the displayed exit-state name and the recorded
 *  exit event are correct), promotes to foreground, then rings via MediaPlayer (USAGE_ALARM,
 *  looping) + audio focus + a partial wake lock, auto-stopping after [RING_MS]. The foreground
 *  notification carries a full-screen intent that surfaces [AlarmActivity] over the lock screen.
 *
 *  Every step is traced to [AlarmDebugLog], including any ForegroundServiceStartNotAllowedException
 *  (the Vivo failure signature), so a single customer test run is self-diagnosing. */
class RingerService : Service() {

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopSelf() }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        AlarmDebugLog.log("FIRE", AlarmDebugLog.STATUS_INFO, "RingerService.onCreate")
        try {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "timewatcher:ringer")
                .apply { setReferenceCounted(false); acquire(WAKE_TIMEOUT_MS) }
            AlarmDebugLog.log(
                "WAKELOCK", AlarmDebugLog.STATUS_OK, "Partial wake lock acquired",
                mapOf("timeoutMs" to WAKE_TIMEOUT_MS.toString()),
            )
        } catch (t: Throwable) {
            AlarmDebugLog.log(
                "WAKELOCK", AlarmDebugLog.STATUS_FAIL, "Wake lock acquire threw",
                mapOf("exception" to t.javaClass.simpleName, "message" to (t.message ?: "")),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Stop path: launched by the notification action / AlarmActivity dismiss button.
        if (intent?.action == ACTION_STOP) {
            AlarmDebugLog.log("STOP", AlarmDebugLog.STATUS_INFO, "Stop requested")
            stopSelf()
            return START_NOT_STICKY
        }

        // Fire path: launched by AlarmManager delivering the alarm directly into this service.
        val isTest = intent?.getBooleanExtra(EXTRA_TEST, false) ?: false
        AlarmDebugLog.log(
            "FIRE", AlarmDebugLog.STATUS_INFO, "Alarm delivered to RingerService",
            mapOf(
                "action" to (intent?.action ?: "null"),
                "flags" to flags.toString(),
                "startId" to startId.toString(),
                "isTest" to isTest.toString(),
                "delivery" to "AlarmManager -> service PendingIntent (P0)",
            ),
        )

        serviceScope.launch {
            val exitName = resolveExitName(isTest)
            if (exitName == null) {
                AlarmDebugLog.log("FIRE", AlarmDebugLog.STATUS_INFO, "Nothing to ring for; stopping")
                withContext(Dispatchers.Main) { stopSelf() }
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (startForegroundCompat(exitName)) {
                    beginRinging()
                    handler.removeCallbacks(stopRunnable)
                    handler.postDelayed(stopRunnable, RING_MS)
                } else {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Reads pending pomodoro state and (for real fires) records the exit event. Returns the name
     *  to display, or null if there is nothing to ring for (and this isn't a test fire). */
    private suspend fun resolveExitName(isTest: Boolean): String? {
        return try {
            val repo = AppGraph.repository(this)
            val exit = repo.reconcilePomodoro(System.currentTimeMillis())
            AlarmDebugLog.log(
                "RECONCILE", AlarmDebugLog.STATUS_OK,
                if (exit != null) "Exit state resolved" else "No pending pomodoro",
                mapOf("exitName" to (exit?.name ?: "null")),
            )
            when {
                exit != null -> exit.name
                isTest -> TEST_LABEL
                else -> null
            }
        } catch (t: Throwable) {
            AlarmDebugLog.log(
                "RECONCILE", AlarmDebugLog.STATUS_FAIL, "reconcilePomodoro threw",
                mapOf("exception" to t.javaClass.simpleName, "message" to (t.message ?: "")),
            )
            if (isTest) TEST_LABEL else null
        }
    }

    /** Promotes the service to foreground. Returns false (and stops the service by the caller) if
     *  the system blocks the background foreground-service start — the Vivo failure mode. The
     *  platform-specific [android.app.ForegroundServiceStartNotAllowedException] (API 31+) is
     *  matched by class name so the reference is safe on older devices too. */
    private fun startForegroundCompat(exitStateName: String): Boolean {
        return try {
            val notification: Notification = Notifier.buildAlarmNotification(this, exitStateName)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
            AlarmDebugLog.log(
                "FOREGROUND", AlarmDebugLog.STATUS_OK, "startForeground succeeded",
                mapOf("type" to "mediaPlayback", "notifId" to NOTIF_ID.toString()),
            )
            true
        } catch (t: Throwable) {
            val blocked = Build.VERSION.SDK_INT >= 31 &&
                t.javaClass.name == FGS_BLOCKED_EXCEPTION
            if (blocked) {
                AlarmDebugLog.log(
                    "FOREGROUND", AlarmDebugLog.STATUS_FAIL,
                    "BLOCKED by system: ForegroundServiceStartNotAllowedException",
                    mapOf(
                        "exception" to t.javaClass.simpleName,
                        "message" to (t.message ?: ""),
                        "cause" to (t.cause?.toString() ?: "n/a"),
                        "diagnosis" to "OEM blocked the background FGS start — this is the Vivo failure",
                    ),
                )
            } else {
                AlarmDebugLog.log(
                    "FOREGROUND", AlarmDebugLog.STATUS_FAIL, "startForeground threw",
                    mapOf("exception" to t.javaClass.simpleName, "message" to (t.message ?: "")),
                )
            }
            false
        }
    }

    private fun beginRinging() {
        if (player != null) {
            AlarmDebugLog.log("MEDIA", AlarmDebugLog.STATUS_INFO, "Already ringing")
            return
        }
        AlarmDebugLog.log("MEDIA", AlarmDebugLog.STATUS_INFO, "Preparing alarm playback")
        acquireFocus()
        player = createPlayer()
        val p = player
        if (p == null) {
            AlarmDebugLog.log("MEDIA", AlarmDebugLog.STATUS_FAIL, "MediaPlayer unavailable — no sound")
            return
        }
        try {
            p.start()
            AlarmDebugLog.log("MEDIA", AlarmDebugLog.STATUS_OK, "Alarm sound playing")
        } catch (t: Throwable) {
            AlarmDebugLog.log(
                "MEDIA", AlarmDebugLog.STATUS_FAIL, "MediaPlayer.start() threw",
                mapOf("exception" to t.javaClass.simpleName, "message" to (t.message ?: "")),
            )
        }
    }

    override fun onDestroy() {
        AlarmDebugLog.log("STOP", AlarmDebugLog.STATUS_INFO, "RingerService.onDestroy — releasing")
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
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireFocus() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        try {
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
            AlarmDebugLog.log("AUDIOFOCUS", AlarmDebugLog.STATUS_OK, "Audio focus requested")
        } catch (t: Throwable) {
            AlarmDebugLog.log(
                "AUDIOFOCUS", AlarmDebugLog.STATUS_FAIL, "Audio focus request threw",
                mapOf("exception" to t.javaClass.simpleName, "message" to (t.message ?: "")),
            )
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
        AlarmDebugLog.log(
            "MEDIA", AlarmDebugLog.STATUS_INFO, "Resolved ringtone uri",
            mapOf("uri" to (uri?.toString() ?: "null")),
        )
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
        } catch (t: Exception) {
            AlarmDebugLog.log(
                "MEDIA", AlarmDebugLog.STATUS_FAIL, "MediaPlayer prepare failed",
                mapOf("exception" to t.javaClass.simpleName, "message" to (t.message ?: "")),
            )
            null
        }
    }

    companion object {
        const val ACTION_STOP = "com.seqn.timewatcher.action.STOP_RINGER"
        const val EXTRA_TEST = "is_test"
        private const val NOTIF_ID = 7725
        private const val RING_MS = 8_000L
        private const val WAKE_TIMEOUT_MS = 30_000L
        private const val TEST_LABEL = "测试响铃"
        private const val FGS_BLOCKED_EXCEPTION = "android.app.ForegroundServiceStartNotAllowedException"
    }
}
