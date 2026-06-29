package com.seqn.timewatcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/** A Compose state holding the current epoch-millis, refreshed every [intervalMs] (always 1s in
 *  practice). Drives live elapsed time and pomodoro countdowns. Keyed on [intervalMs] and returns
 *  a mutable state so callers can bump it immediately on a state switch (avoids a briefly-stale
 *  "now" right after switching). */
@Composable
fun rememberNowTick(intervalMs: Long = 1_000L): MutableLongState {
    val state = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            delay(intervalMs)
            state.longValue = System.currentTimeMillis()
        }
    }
    return state
}
