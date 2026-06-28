package com.seqn.timewatcher.tips

import com.google.common.truth.Truth.assertThat
import com.seqn.timewatcher.data.model.Category
import com.seqn.timewatcher.data.model.DefaultConfig
import org.junit.Test

class TipsTest {

    @Test
    fun philosophy_triggers_only_for_effective_state_without_pomodoro() {
        // The disallowed case: effective but unmanaged.
        assertThat(Tips.triggersPhilosophy(Category.EFFECTIVE.name, pomodoroEnabled = false)).isTrue()
        // All other combos are fine.
        assertThat(Tips.triggersPhilosophy(Category.EFFECTIVE.name, pomodoroEnabled = true)).isFalse()
        assertThat(Tips.triggersPhilosophy(Category.REST.name, pomodoroEnabled = false)).isFalse()
        assertThat(Tips.triggersPhilosophy(Category.WASTE.name, pomodoroEnabled = false)).isFalse()
    }

    @Test
    fun exactly_five_default_states_carry_an_entry_tip() {
        assertThat(Tips.stateTip).hasSize(5)
        listOf(DefaultConfig.ID_SLEEP, DefaultConfig.ID_LIFE, DefaultConfig.ID_SNOOZE, DefaultConfig.ID_WORK, DefaultConfig.ID_HOBBY).forEach {
            assertThat(Tips.stateTip.containsKey(it)).isTrue()
        }
        // 摆烂 has no tip (it's the default sink state).
        assertThat(Tips.stateTip.containsKey(DefaultConfig.ID_SLACK)).isFalse()
    }

    @Test
    fun all_tip_ids_have_metadata_and_are_manageable() {
        TipId.entries.forEach { assertThat(Tips.get(it).message).isNotEmpty() }
        // Every tip is toggleable in settings.
        assertThat(Tips.manageable.map { it.id }.toSet()).isEqualTo(TipId.entries.toSet())
    }
}
