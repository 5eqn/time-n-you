package com.seqn.timewatcher

import com.google.common.truth.Truth.assertThat
import com.seqn.timewatcher.data.model.LambdaLevel
import org.junit.Test

class LambdaLevelTest {

    @Test
    fun `supported levels expose exact reciprocal targets`() {
        assertThat(LambdaLevel.STEADY.targetRatio).isWithin(1e-9).of(0.2)
        assertThat(LambdaLevel.AMBITIOUS.targetRatio).isWithin(1e-9).of(1.0 / 3.0)
        assertThat(LambdaLevel.SPRINT.targetRatio).isWithin(1e-9).of(0.5)
    }

    @Test
    fun `legacy ratios map to nearest supported level`() {
        assertThat(LambdaLevel.nearestToRatio(0.10)).isEqualTo(LambdaLevel.STEADY)
        assertThat(LambdaLevel.nearestToRatio(0.35)).isEqualTo(LambdaLevel.AMBITIOUS)
        assertThat(LambdaLevel.nearestToRatio(0.90)).isEqualTo(LambdaLevel.SPRINT)
        assertThat(LambdaLevel.nearestToRatio(null)).isEqualTo(LambdaLevel.STEADY)
    }

    @Test
    fun `unsupported stored value falls back to recommended level`() {
        assertThat(LambdaLevel.fromValue(4)).isEqualTo(LambdaLevel.STEADY)
        assertThat(LambdaLevel.fromValue(null)).isEqualTo(LambdaLevel.STEADY)
    }
}
