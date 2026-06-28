package com.seqn.timewatcher.util

import androidx.compose.ui.graphics.Color

/** Deterministic, saturated color per state id (hash-derived, not editable).
 *  Hue from a stable hash of the id; S/V tuned for rich fills with readable white text. */
object ColorUtil {
    private const val SATURATION = 0.55f
    private const val VALUE = 0.50f

    fun colorFor(seed: String): Color {
        val unsigned = seed.hashCode().toLong() and 0xFFFFFFFFL
        val hue = (unsigned % 360L).toFloat()
        val hsv = floatArrayOf(hue, SATURATION, VALUE)
        val argb = android.graphics.Color.HSVToColor(hsv)
        return Color(argb)
    }

    /** On-color for fills produced by [colorFor] — white for contrast and a cleaner look. */
    val OnFill: Color = Color(0xFFFFFFFF)
}
