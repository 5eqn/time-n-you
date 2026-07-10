package com.seqn.timewatcher.pomodoro

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
        val isTest = intent?.getBooleanExtra(EXTRA_IS_TEST, false) ?: false

        setContent {
            TimeWatcherTheme {
                AlarmScreen(exitName = exitName, isTest = isTest, onStop = { stopRingerAndFinish() })
            }
        }
    }

    private fun stopRingerAndFinish() {
        startService(Intent(this, RingerService::class.java).setAction(RingerService.ACTION_STOP))
        finish()
    }

    companion object {
        const val EXTRA_EXIT_STATE_NAME = "exit_state_name"
        const val EXTRA_IS_TEST = "is_test"
    }
}

@Composable
private fun AlarmScreen(exitName: String, isTest: Boolean, onStop: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Surface(
                modifier = Modifier.size(88.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(if (isTest) R.string.alarm_test_title else R.string.alarm_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(if (isTest) R.string.alarm_test_message else R.string.alarm_switched_to),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!isTest) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "「$exitName」",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.StopCircle, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(text = stringResource(R.string.stop_alarm), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
