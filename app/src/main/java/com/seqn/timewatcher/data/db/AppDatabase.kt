package com.seqn.timewatcher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.seqn.timewatcher.data.db.dao.EventDao
import com.seqn.timewatcher.data.db.dao.SettingDao
import com.seqn.timewatcher.data.db.dao.StateDefDao
import com.seqn.timewatcher.data.db.entity.SettingEntity
import com.seqn.timewatcher.data.db.entity.StateDefEntity
import com.seqn.timewatcher.data.db.entity.StateEventEntity

@Database(
    entities = [StateDefEntity::class, StateEventEntity::class, SettingEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stateDefDao(): StateDefDao
    abstract fun eventDao(): EventDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "timewatcher.db",
                ).build().also { INSTANCE = it }
            }
    }
}
