package com.seqn.timewatcher.data.model

/** Stable state-color indices. The first 13 entries exactly match the v1.4 palette. */
object StateColors {
    const val PALETTE_SIZE = 16
    private const val LEGACY_PALETTE_SIZE = 13

    private val builtIn = mapOf(
        DefaultConfig.ID_WORK to 0,
        DefaultConfig.ID_LIFE to 1,
        DefaultConfig.ID_DOG to 2,
        DefaultConfig.ID_HOBBY to 6,
        DefaultConfig.ID_SNOOZE to 3,
        DefaultConfig.ID_SLACK to 7,
        DefaultConfig.ID_SLEEP to 11,
    )

    /** Reproduces v1.4's hand-picked/default + unsigned-ID-hash assignment exactly. */
    fun legacyIndexFor(stateId: String): Int = builtIn[stateId]
        ?: (((stateId.hashCode().toLong() and 0xFFFFFFFFL) % LEGACY_PALETTE_SIZE).toInt())

    fun isValid(index: Int): Boolean = index in 0 until PALETTE_SIZE
}
