package com.seqn.timewatcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Circular gauge showing utilization %. Color steps by tier relative to [targetRatio]; "优秀" label when excellent. */
@Composable
fun UtilizationGauge(
    utilization: Double,
    isExcellent: Boolean,
    targetRatio: Double,
    modifier: Modifier = Modifier,
) {
    val pct = utilization.coerceIn(0.0, 1.0).toFloat()
    val color = when {
        utilization >= targetRatio -> MaterialTheme.colorScheme.primary
        utilization >= targetRatio * 0.6 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(150.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = Color.Gray.copy(alpha = 0.22f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (pct > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f, sweepAngle = 360f * pct, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${(pct * 100).roundToInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                color = color,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (isExcellent) "优秀" else "利用率",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
