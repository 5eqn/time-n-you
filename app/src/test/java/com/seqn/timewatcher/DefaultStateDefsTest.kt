package com.seqn.timewatcher

import com.google.common.truth.Truth.assertThat
import com.seqn.timewatcher.data.model.Category
import com.seqn.timewatcher.data.model.DefaultConfig
import com.seqn.timewatcher.data.model.defaultStateDefs
import org.junit.Test

class DefaultStateDefsTest {

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
