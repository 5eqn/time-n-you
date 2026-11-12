package com.seqn.timewatcher.data.model

/** Statistics bucket for a state. Drives utilization math. */
enum class Category { REST, EFFECTIVE, WASTE;

    companion object
}

/** The three deliberate focus standards exposed to users. */
enum class LambdaLevel(
    val value: Int,
    val targetRatio: Double,
    val displayPercent: Int,
) {
    STEADY(5, 1.0 / 5.0, 20),
    AMBITIOUS(3, 1.0 / 3.0, 33),
    SPRINT(2, 1.0 / 2.0, 50);

    companion object {
        fun fromValue(value: Int?): LambdaLevel = entries.firstOrNull { it.value == value } ?: STEADY

        /** Preserve an old custom target by choosing the closest supported standard. */
        fun nearestToRatio(ratio: Double?): LambdaLevel {
            if (ratio == null || !ratio.isFinite()) return STEADY
            return entries.minBy { kotlin.math.abs(it.targetRatio - ratio) }
        }
    }
}

object DefaultConfig {
    const val SCHEMA_VERSION = 4

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

    /** Default pomodoro length for newly-created states and 主业/爱好. 小憩 uses 3 min. */
    const val DEFAULT_POMODORO_MINUTES = 30
    const val SNOOZE_POMODORO_MINUTES = 3

    const val DEFAULT_LIFE_NOTE = "（吃饭、洗漱、运动……）"
    const val DEFAULT_DOG_NOTE = "（不得不干的杂活……）"
    const val DEFAULT_SNOOZE_NOTE = "（走动、饮水、远眺……）"

    // --- configurable focus standard ---
    val DEFAULT_LAMBDA = LambdaLevel.STEADY

    // --- setting keys ---
    const val SETTING_EXIT_STATE = "pomodoro_exit_state_id"
    const val SETTING_LAMBDA = "lambda"
    /** Legacy v1.3.1 setting, read once by the v1.4 migration. */
    const val SETTING_TARGET_RATIO = "target_ratio"
    const val SETTING_LAST_CHANGELOG_VERSION = "last_changelog_version"
    const val SETTING_MIN_FOCUS_BREAK_MINUTES = "minimum_focus_break_minutes"
    const val SETTING_LAST_FOCUS_POMODORO_END_TS = "last_focus_pomodoro_end_ts"
    const val SETTING_DATA_VERSION = "data_version"
    const val SETTING_TIP_PREFIX = "tip_enabled_" // + TipId.name

    const val DEFAULT_MIN_FOCUS_BREAK_MINUTES = 3
    const val CURRENT_DATA_VERSION = 6
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
    StateDef(DefaultConfig.ID_DOG, "喂狗", Category.REST, pomodoroEnabled = false, pomodoroMinutes = DefaultConfig.DEFAULT_POMODORO_MINUTES, sortOrder = 2, note = DefaultConfig.DEFAULT_DOG_NOTE),
    StateDef(DefaultConfig.ID_WORK, "主业", Category.EFFECTIVE, pomodoroEnabled = true, pomodoroMinutes = DefaultConfig.DEFAULT_POMODORO_MINUTES, sortOrder = 3),
    StateDef(DefaultConfig.ID_HOBBY, "爱好", Category.EFFECTIVE, pomodoroEnabled = true, pomodoroMinutes = DefaultConfig.DEFAULT_POMODORO_MINUTES, sortOrder = 4),
    StateDef(DefaultConfig.ID_SNOOZE, "小憩", Category.REST, pomodoroEnabled = true, pomodoroMinutes = DefaultConfig.SNOOZE_POMODORO_MINUTES, sortOrder = 5, note = DefaultConfig.DEFAULT_SNOOZE_NOTE),
    StateDef(DefaultConfig.ID_SLACK, "摆烂", Category.WASTE, pomodoroEnabled = false, pomodoroMinutes = DefaultConfig.DEFAULT_POMODORO_MINUTES, sortOrder = 6),
)
