package com.seqn.timewatcher

import com.google.common.truth.Truth.assertThat
import com.seqn.timewatcher.data.model.Category
import com.seqn.timewatcher.data.model.DefaultConfig
import com.seqn.timewatcher.data.model.defaultStateDefs
import com.seqn.timewatcher.data.model.StateColors
import org.junit.Test

class DefaultStateDefsTest {

    @Test
    fun legacyColorAssignmentsRemainStable() {
        assertThat(StateColors.PALETTE_SIZE).isEqualTo(16)
        assertThat(StateColors.legacyIndexFor(DefaultConfig.ID_WORK)).isEqualTo(0)
        assertThat(StateColors.legacyIndexFor(DefaultConfig.ID_SLEEP)).isEqualTo(11)
        assertThat(StateColors.legacyIndexFor("custom_state")).isIn(0..12)
    }

    @Test
    fun builtInRestStatesUseCurrentDefaults() {
        val states = defaultStateDefs().associateBy { it.id }

        assertThat(states.getValue(DefaultConfig.ID_LIFE)).isEqualTo(
            states.getValue(DefaultConfig.ID_LIFE).copy(
                category = Category.REST,
                pomodoroEnabled = false,
                note = "（吃饭、洗漱、运动……）",
            ),
        )
        assertThat(states.getValue(DefaultConfig.ID_DOG)).isEqualTo(
            states.getValue(DefaultConfig.ID_DOG).copy(
                category = Category.REST,
                pomodoroEnabled = false,
                note = "（不得不干的杂活……）",
            ),
        )
        assertThat(states.getValue(DefaultConfig.ID_SNOOZE)).isEqualTo(
            states.getValue(DefaultConfig.ID_SNOOZE).copy(
                category = Category.REST,
                pomodoroEnabled = true,
                pomodoroMinutes = 3,
                note = "（走动、饮水、远眺……）",
            ),
        )
    }
}
