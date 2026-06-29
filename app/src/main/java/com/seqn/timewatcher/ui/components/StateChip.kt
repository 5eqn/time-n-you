package com.seqn.timewatcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

/** A wide, tappable state pill. Lean: flat Box + background + clickable (no Surface/shadow),
 *  since these are the items recycled during Home scrolling. */
@Composable
fun StateChip(
    name: String,
    colorSeed: String,
    selected: Boolean,
    showPomodoro: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = ColorUtil.colorFor(colorSeed)
    val onColor = ColorUtil.OnFill
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .then(if (selected) Modifier.border(2.5.dp, onColor, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = onColor)
            if (showPomodoro) {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = onColor)
            }
        }
    }
}
