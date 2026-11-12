package com.seqn.timewatcher.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Kept independently from state_def so deleted states retain their historical color. */
@Entity(tableName = "state_color")
data class StateColorEntity(
    @PrimaryKey @ColumnInfo(name = "state_id") val stateId: String,
    @ColumnInfo(name = "color_index") val colorIndex: Int,
)
