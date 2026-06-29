package com.seqn.timewatcher.pomodoro

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seqn.timewatcher.R
import com.seqn.timewatcher.ui.theme.TimeWatcherTheme

/** Full-screen alarm overlay shown over the lock screen when a pomodoro ends. Turns the screen on
 *  and shows above the keyguard so the user can dismiss the ringing immediately. Launched via the
 *  notification's full-screen intent (system-gated: the system only starts it when the screen is
 *  off / locked; otherwise the notification shows as a heads-up). */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show over the lock screen + turn the screen on (API-version-guarded: minSdk is 26).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }

        val exitName = intent?.getStringExtra(EXTRA_EXIT_STATE_NAME) ?: ""

        setContent {
            TimeWatcherTheme {
                AlarmScreen(exitName = exitName, onStop = { stopRingerAndFinish() })
            }
        }
    }

    private fun stopRingerAndFinish() {
        startService(Intent(this, RingerService::class.java).setAction(RingerService.ACTION_STOP))
        finish()
    }

    companion object {
        const val EXTRA_EXIT_STATE_NAME = "exit_state_name"
    }
}

@Composable
private fun AlarmScreen(exitName: String, onStop: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.alarm_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.notif_pomodoro_text, exitName),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text(text = stringResource(R.string.stop_alarm), fontSize = 20.sp)
            }
        }
    }
}
