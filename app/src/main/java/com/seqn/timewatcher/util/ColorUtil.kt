package com.seqn.timewatcher.util

import androidx.compose.ui.graphics.Color

/** Deterministic, low-saturation color per state id (hash-derived, not editable).
 *  Hue from a stable hash of the id; fixed S/V tuned for muted fills with readable dark text. */
object ColorUtil {
    private const val SATURATION = 0.48f
    private const val VALUE = 0.74f

    fun colorFor(seed: String): Color {
        val unsigned = seed.hashCode().toLong() and 0xFFFFFFFFL
        val hue = (unsigned % 360L).toFloat()
        val hsv = floatArrayOf(hue, SATURATION, VALUE)
        val argb = android.graphics.Color.HSVToColor(hsv)
        return Color(argb)
    }

    /** On-color for fills produced by [colorFor] — always dark for contrast. */
    val OnFill: Color = Color(0xFF1C1B1F)
}
