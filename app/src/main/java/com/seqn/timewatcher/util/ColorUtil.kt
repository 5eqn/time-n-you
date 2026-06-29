package com.seqn.timewatcher.util

import androidx.compose.ui.graphics.Color
import com.seqn.timewatcher.data.model.DefaultConfig
import java.util.concurrent.ConcurrentHashMap

/** Per-state color resolution.
 *
 *  The old hash-derived colors (fixed S/V, only hue varying) all came out the same muted tone.
 *  Now every color is drawn from one curated palette of pleasant, saturated colors that read
 *  well under white text. The default states have hand-picked entries; custom states pick a
 *  palette entry by a stable hash of their id — so a random color and a default color are
 *  always from the same consistent set. Results are memoized (called in hot scroll paths). */
object ColorUtil {

    private val palette = listOf(
        Color(0xFF1565C0), // 0  blue 800       — 主业
        Color(0xFF00897B), // 1  teal 600       — 生活
        Color(0xFF6A1B9A), // 2  purple 800     — 爱好
        Color(0xFFEF6C00), // 3  orange 800     — 小憩
        Color(0xFF283593), // 4  indigo 800
        Color(0xFF2E7D32), // 5  green 800
        Color(0xFFAD1457), // 6  pink 800
        Color(0xFF546E7A), // 7  blue-grey 600  — 摆烂
        Color(0xFF0277BD), // 8  light-blue 800
        Color(0xFFBF360C), // 9  deep-orange 900
        Color(0xFF455A64), // 10 blue-grey 700
        Color(0xFF1A237E), // 11 indigo 900     — 睡觉
    )

    /** Hand-picked palette entries for the six default states. */
    private val defaultColors: Map<String, Color> = mapOf(
        DefaultConfig.ID_WORK to palette[0],
        DefaultConfig.ID_LIFE to palette[1],
        DefaultConfig.ID_HOBBY to palette[2],
        DefaultConfig.ID_SNOOZE to palette[3],
        DefaultConfig.ID_SLACK to palette[7],
        DefaultConfig.ID_SLEEP to palette[11],
    )

    private val cache = ConcurrentHashMap<String, Color>()

    /** On-color for fills produced by [colorFor] — white for contrast. */
    val OnFill: Color = Color(0xFFFFFFFF)

    fun colorFor(seed: String): Color = cache.computeIfAbsent(seed) { key ->
        val index = ((key.hashCode().toLong() and 0xFFFFFFFFL) % palette.size).toInt()
        defaultColors[key] ?: palette[index]
    }
}
