package com.seqn.timewatcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seqn.timewatcher.util.ColorUtil
import com.seqn.timewatcher.util.TimeUtil

/** A wide, tappable state pill. Lean: flat Box + background + clickable (no Surface/shadow),
 *  since these are the items recycled during Home scrolling.
 *
 *  Carries the state's [note] (e.g. 「生活」's "吃饭、洗漱、运动……") as a secondary line, and shows
 *  the configured pomodoro *total* time (e.g. "30分") for pomodoro-enabled states. The active
 *  state uses a check badge and outline while every chip keeps full-color readability. */
@Composable
fun StateChip(
    name: String,
    colorSeed: String,
    selected: Boolean,
    showPomodoro: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    pomodoroMinutes: Int = 0,
) {
    val bg = ColorUtil.colorFor(colorSeed)
    val onColor = ColorUtil.OnFill
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .then(
                if (selected) {
                    Modifier.border(2.dp, onColor.copy(alpha = 0.92f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = onColor)
                if (!note.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = onColor.copy(alpha = 0.85f),
                    )
                }
            }
            if (showPomodoro || selected) {
                Spacer(Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showPomodoro) {
                        Icon(Icons.Filled.Timer, contentDescription = null, tint = onColor)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            TimeUtil.formatHm(pomodoroMinutes.toLong() * 60_000L),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onColor,
                        )
                    }
                    if (selected) {
                        if (showPomodoro) Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(onColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = bg,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
