package com.seqn.timewatcher.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.seqn.timewatcher.data.db.entity.StateDefEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StateDefDao {
    @Query("SELECT * FROM state_def ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<StateDefEntity>>

    @Query("SELECT * FROM state_def ORDER BY sort_order ASC")
    suspend fun getAll(): List<StateDefEntity>

    @Query("SELECT * FROM state_def WHERE id = :id")
    suspend fun getById(id: String): StateDefEntity?

    @Query("SELECT COUNT(*) FROM state_def")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(def: StateDefEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(defs: List<StateDefEntity>)

    @Query("DELETE FROM state_def WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM state_def")
    suspend fun deleteAll()
}
