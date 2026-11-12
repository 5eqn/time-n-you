package com.seqn.timewatcher.util

import androidx.compose.ui.graphics.Color
import com.seqn.timewatcher.data.model.StateColors
import java.util.concurrent.ConcurrentHashMap

/** Per-state color resolution.
 *
 *  The old hash-derived colors (fixed S/V, only hue varying) all came out the same muted tone.
 *  Now every color is drawn from one curated palette of pleasant, saturated colors that read
 *  well under white text. v1.5 persists a selected palette index for each state; missing legacy
 *  entries still resolve through the exact v1.4 ID algorithm. Fallbacks are memoized because
 *  this is called in hot scroll paths. */
object ColorUtil {

    val palette = listOf(
        Color(0xFF1565C0), // 0  blue 800       — 主业
        Color(0xFF00897B), // 1  teal 600       — 生活
        Color(0xFFC62828), // 2  red 800        — 喂狗
        Color(0xFFEF6C00), // 3  orange 800     — 小憩
        Color(0xFF283593), // 4  indigo 800
        Color(0xFF2E7D32), // 5  green 800
        Color(0xFFAD1457), // 6  pink 800       — 爱好
        Color(0xFF546E7A), // 7  blue-grey 600  — 摆烂
        Color(0xFF0277BD), // 8  light-blue 800
        Color(0xFFBF360C), // 9  deep-orange 900
        Color(0xFF455A64), // 10 blue-grey 700
        Color(0xFF1A237E), // 11 indigo 900     — 睡觉
        Color(0xFF6A1B9A), // 12 purple 800
        Color(0xFF5D4037), // 13 brown 700
        Color(0xFF006064), // 14 cyan 900
        Color(0xFF4527A0), // 15 deep-purple 800
    )

    private val cache = ConcurrentHashMap<String, Color>()

    /** On-color for fills produced by [colorFor] — white for contrast. */
    val OnFill: Color = Color(0xFFFFFFFF)

    fun colorFor(seed: String, colorIndex: Int? = null): Color {
        if (colorIndex != null && StateColors.isValid(colorIndex)) return palette[colorIndex]
        return cache.computeIfAbsent(seed) { key -> palette[StateColors.legacyIndexFor(key)] }
    }

    fun colorForIndex(colorIndex: Int): Color = palette[colorIndex.coerceIn(palette.indices)]
}
