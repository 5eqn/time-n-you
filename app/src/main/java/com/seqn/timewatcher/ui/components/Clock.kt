package com.seqn.timewatcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/** A Compose state holding the current epoch-millis, refreshed every [intervalMs]. Drives live
 *  elapsed time and pomodoro countdowns. */
@Composable
fun rememberNowTick(intervalMs: Long = 1_000L): State<Long> {
    val state = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(intervalMs)
            state.longValue = System.currentTimeMillis()
        }
    }
    return state
}
