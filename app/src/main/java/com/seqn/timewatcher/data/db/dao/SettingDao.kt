package com.seqn.timewatcher.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.seqn.timewatcher.data.db.entity.SettingEntity

@Dao
interface SettingDao {
    @Query("SELECT * FROM setting")
    suspend fun getAll(): List<SettingEntity>

    @Query("SELECT value FROM setting WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: SettingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setAll(settings: List<SettingEntity>)

    @Query("DELETE FROM setting")
    suspend fun deleteAll()
}
