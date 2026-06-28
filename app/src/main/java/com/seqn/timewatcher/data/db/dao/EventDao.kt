package com.seqn.timewatcher.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.seqn.timewatcher.data.db.entity.StateEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: StateEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<StateEventEntity>)

    @Query("UPDATE state_event SET pomodoro_end_ts = :endTs, pomodoro_applied = :applied WHERE id = :id")
    suspend fun updatePomodoro(id: Long, endTs: Long?, applied: Boolean)

    @Query("SELECT * FROM state_event ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<StateEventEntity>>

    @Query("SELECT * FROM state_event ORDER BY timestamp ASC")
    suspend fun getAll(): List<StateEventEntity>

    @Query("SELECT * FROM state_event ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): StateEventEntity?

    @Query("DELETE FROM state_event")
    suspend fun deleteAll()
}
