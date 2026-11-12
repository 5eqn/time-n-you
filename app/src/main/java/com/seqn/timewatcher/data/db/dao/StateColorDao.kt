package com.seqn.timewatcher.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.seqn.timewatcher.data.db.entity.StateColorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StateColorDao {
    @Query("SELECT * FROM state_color")
    fun observeAll(): Flow<List<StateColorEntity>>

    @Query("SELECT * FROM state_color")
    suspend fun getAll(): List<StateColorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(color: StateColorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(colors: List<StateColorEntity>)

    @Query("DELETE FROM state_color")
    suspend fun deleteAll()
}
