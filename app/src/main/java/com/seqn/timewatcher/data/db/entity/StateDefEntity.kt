package com.seqn.timewatcher.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.seqn.timewatcher.data.model.Category
import com.seqn.timewatcher.data.model.StateDef
import com.seqn.timewatcher.data.model.fromString

@Entity(tableName = "state_def")
data class StateDefEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "pomodoro_enabled") val pomodoroEnabled: Boolean,
    @ColumnInfo(name = "pomodoro_minutes") val pomodoroMinutes: Int,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "note") val note: String?,
)

fun StateDefEntity.toModel(): StateDef = StateDef(
    id = id,
    name = name,
    category = Category.fromString(category),
    pomodoroEnabled = pomodoroEnabled,
    pomodoroMinutes = pomodoroMinutes,
    sortOrder = sortOrder,
    note = note,
)

fun StateDef.toEntity(): StateDefEntity = StateDefEntity(
    id = id,
    name = name,
    category = category.name,
    pomodoroEnabled = pomodoroEnabled,
    pomodoroMinutes = pomodoroMinutes,
    sortOrder = sortOrder,
    note = note,
)
