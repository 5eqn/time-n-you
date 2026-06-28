package com.seqn.timewatcher.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seqn.timewatcher.util.ColorUtil

@OptIn(ExperimentalMaterial3Api::class)
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
    Surface(
        onClick = onClick,
        color = bg,
        contentColor = onColor,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(2.5.dp, onColor, RoundedCornerShape(18.dp)) else Modifier),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (showPomodoro) {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = onColor)
            }
        }
    }
}
