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

    @Query("SELECT * FROM state_event ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<StateEventEntity>>

    /** Events inside [start, end), ordered ascending — the bounded window used for stats. */
    @Query("SELECT * FROM state_event WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp ASC")
    fun observeBetween(start: Long, end: Long): Flow<List<StateEventEntity>>

    /** The single latest event before [start] (the state that was running across the period boundary). */
    @Query("SELECT * FROM state_event WHERE timestamp < :start ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestBefore(start: Long): Flow<StateEventEntity?>

    /** The most recent event overall — drives the Home "current state" card without loading all history. */
    @Query("SELECT * FROM state_event ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<StateEventEntity?>

    @Query("SELECT * FROM state_event ORDER BY timestamp ASC")
    suspend fun getAll(): List<StateEventEntity>

    @Query("SELECT * FROM state_event ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): StateEventEntity?

    @Query(
        """
        UPDATE state_event
        SET state_id = :stateId, state_name = :stateName, category = :category
        WHERE state_name LIKE '%' || :fragment || '%'
        """
    )
    suspend fun replaceStateByNameContaining(
        fragment: String,
        stateId: String,
        stateName: String,
        category: String,
    )

    @Query(
        """
        UPDATE state_event
        SET category = :category
        WHERE state_id = :stateId OR state_name = :stateName
        """
    )
    suspend fun updateCategoryByState(
        stateId: String,
        stateName: String,
        category: String,
    )

    @Query("DELETE FROM state_event")
    suspend fun deleteAll()
}
