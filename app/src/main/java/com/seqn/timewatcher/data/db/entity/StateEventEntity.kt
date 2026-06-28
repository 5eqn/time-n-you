package com.seqn.timewatcher.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.seqn.timewatcher.data.model.Category
import com.seqn.timewatcher.data.model.StateEvent
import com.seqn.timewatcher.data.model.fromString

@Entity(tableName = "state_event", indices = [Index("timestamp")])
data class StateEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "state_id") val stateId: String,
    @ColumnInfo(name = "state_name") val stateName: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "pomodoro_end_ts") val pomodoroEndTs: Long?,
    @ColumnInfo(name = "pomodoro_applied") val pomodoroApplied: Boolean,
)

fun StateEventEntity.toModel(): StateEvent = StateEvent(
    id = id,
    stateId = stateId,
    stateName = stateName,
    category = Category.fromString(category),
    timestamp = timestamp,
    pomodoroEndTs = pomodoroEndTs,
    pomodoroApplied = pomodoroApplied,
)

fun StateEvent.toEntity(): StateEventEntity = StateEventEntity(
    id = id,
    stateId = stateId,
    stateName = stateName,
    category = category.name,
    timestamp = timestamp,
    pomodoroEndTs = pomodoroEndTs,
    pomodoroApplied = pomodoroApplied,
)
