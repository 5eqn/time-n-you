package com.seqn.timewatcher.data.model

/** Statistics bucket for a state. Drives utilization math. */
enum class Category { REST, EFFECTIVE, WASTE;

    companion object
}

object DefaultConfig {
    const val SCHEMA_VERSION = 2

    // Stable ids for the default states — never change, so events/refs stay valid after rename.
    const val ID_SLEEP = "sleep"
    const val ID_LIFE = "life"       // 洗漱/做饭/吃饭/如厕/洗澡 等不可避免的事
    const val ID_DOG = "dog"
    const val ID_WORK = "work"
    const val ID_HOBBY = "hobby"
    const val ID_SNOOZE = "snooze"   // 小憩（起身活动、远眺、喝水）
    const val ID_SLACK = "slack"

    /** Default state the pomodoro auto-switches into when it ends. */
    const val DEFAULT_EXIT_STATE_ID = ID_SLACK

    /** Default pomodoro length for newly-created states and 主业/爱好. 小憩 uses 5 min. */
    const val DEFAULT_POMODORO_MINUTES = 30
    const val SNOOZE_POMODORO_MINUTES = 5

    /** Default note auto-added to 「生活」 on first open of v1.2 (and for fresh installs). */
    const val DEFAULT_LIFE_NOTE = "吃饭、洗漱、运动……"
    const val DEFAULT_DOG_NOTE = "不得不干的杂活！"

    // --- configurable "优秀" target ---
    const val DEFAULT_TARGET_RATIO = 0.5

    // --- setting keys ---
    const val SETTING_EXIT_STATE = "pomodoro_exit_state_id"
    const val SETTING_TARGET_RATIO = "target_ratio"
    const val SETTING_LAST_CHANGELOG_VERSION = "last_changelog_version"
    const val SETTING_DATA_VERSION = "data_version"
    const val SETTING_TIP_PREFIX = "tip_enabled_" // + TipId.name

    const val CURRENT_DATA_VERSION = 3
}

data class StateDef(
    val id: String,
    val name: String,
    val category: Category,
    val pomodoroEnabled: Boolean,
    val pomodoroMinutes: Int,
    val sortOrder: Int,
    val note: String? = null,
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
    StateDef(DefaultConfig.ID_SLEEP, "睡觉", Category.REST, pomodoroEnabled = false, pomodoroMinutes = DefaultConfig.DEFAULT_POMODORO_MINUTES, sortOrder = 0),
    StateDef(DefaultConfig.ID_LIFE, "生活", Category.REST, pomodoroEnabled = false, pomodoroMinutes = DefaultConfig.DEFAULT_POMODORO_MINUTES, sortOrder = 1, note = DefaultConfig.DEFAULT_LIFE_NOTE),
    StateDef(DefaultConfig.ID_DOG, "喂狗", Category.REST, pomodoroEnabled = true, pomodoroMinutes = DefaultConfig.DEFAULT_POMODORO_MINUTES, sortOrder = 2, note = DefaultConfig.DEFAULT_DOG_NOTE),
    StateDef(DefaultConfig.ID_WORK, "主业", Category.EFFECTIVE, pomodoroEnabled = true, pomodoroMinutes = DefaultConfig.DEFAULT_POMODORO_MINUTES, sortOrder = 3),
    StateDef(DefaultConfig.ID_HOBBY, "爱好", Category.EFFECTIVE, pomodoroEnabled = true, pomodoroMinutes = DefaultConfig.DEFAULT_POMODORO_MINUTES, sortOrder = 4),
    StateDef(DefaultConfig.ID_SNOOZE, "小憩", Category.REST, pomodoroEnabled = true, pomodoroMinutes = DefaultConfig.SNOOZE_POMODORO_MINUTES, sortOrder = 5),
    StateDef(DefaultConfig.ID_SLACK, "摆烂", Category.WASTE, pomodoroEnabled = false, pomodoroMinutes = DefaultConfig.DEFAULT_POMODORO_MINUTES, sortOrder = 6),
)
