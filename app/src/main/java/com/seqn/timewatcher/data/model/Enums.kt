package com.seqn.timewatcher.data.model

/** Statistics bucket for a state. Drives utilization math. */
enum class Category { REST, EFFECTIVE, WASTE;

    companion object
}

object DefaultConfig {
    const val SCHEMA_VERSION = 1

    // Stable ids for the 6 default states — never change, so events/refs stay valid after rename.
    const val ID_SLEEP = "sleep"
    const val ID_EAT = "eat"
    const val ID_WORK = "work"
    const val ID_HOBBY = "hobby"
    const val ID_EXERCISE = "exercise"
    const val ID_SLACK = "slack"

    /** Default state the pomodoro auto-switches into when it ends. */
    const val DEFAULT_EXIT_STATE_ID = ID_SLACK

    const val SETTING_EXIT_STATE = "pomodoro_exit_state_id"
    const val DEFAULT_POMODORO_MINUTES = 25
}

data class StateDef(
    val id: String,
    val name: String,
    val category: Category,
    val pomodoroEnabled: Boolean,
    val pomodoroMinutes: Int,
    val sortOrder: Int,
)

/** An immutable state-switch record. Carries name/category snapshots so editing a state
 *  later never rewrites history. */
data class StateEvent(
    val id: Long,
    val stateId: String,
    val stateName: String,
    val category: Category,
    val timestamp: Long,
    val pomodoroEndTs: Long?,
    val pomodoroApplied: Boolean,
)

fun Category.Companion.fromString(s: String?): Category =
    Category.entries.firstOrNull { it.name == s } ?: Category.WASTE

fun defaultStateDefs(): List<StateDef> = listOf(
    StateDef(DefaultConfig.ID_SLEEP, "睡觉", Category.REST, pomodoroEnabled = false, pomodoroMinutes = 25, sortOrder = 0),
    StateDef(DefaultConfig.ID_EAT, "吃饭", Category.REST, pomodoroEnabled = false, pomodoroMinutes = 25, sortOrder = 1),
    StateDef(DefaultConfig.ID_WORK, "主业", Category.EFFECTIVE, pomodoroEnabled = true, pomodoroMinutes = 25, sortOrder = 2),
    StateDef(DefaultConfig.ID_HOBBY, "爱好", Category.EFFECTIVE, pomodoroEnabled = true, pomodoroMinutes = 25, sortOrder = 3),
    StateDef(DefaultConfig.ID_EXERCISE, "运动", Category.WASTE, pomodoroEnabled = false, pomodoroMinutes = 25, sortOrder = 4),
    StateDef(DefaultConfig.ID_SLACK, "摆烂", Category.WASTE, pomodoroEnabled = false, pomodoroMinutes = 25, sortOrder = 5),
)
