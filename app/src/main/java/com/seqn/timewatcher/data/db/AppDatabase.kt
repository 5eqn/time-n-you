package com.seqn.timewatcher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.seqn.timewatcher.data.db.dao.EventDao
import com.seqn.timewatcher.data.db.dao.SettingDao
import com.seqn.timewatcher.data.db.dao.StateDefDao
import com.seqn.timewatcher.data.db.dao.StateColorDao
import com.seqn.timewatcher.data.db.entity.SettingEntity
import com.seqn.timewatcher.data.db.entity.StateDefEntity
import com.seqn.timewatcher.data.db.entity.StateEventEntity
import com.seqn.timewatcher.data.db.entity.StateColorEntity

@Database(
    entities = [StateDefEntity::class, StateEventEntity::class, SettingEntity::class, StateColorEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stateDefDao(): StateDefDao
    abstract fun eventDao(): EventDao
    abstract fun settingDao(): SettingDao
    abstract fun stateColorDao(): StateColorDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 → v2: add the `note` column to state_def. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE state_def ADD COLUMN note TEXT")
            }
        }

        /** v2 → v3: persist user-selectable colors independently from state definitions. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS state_color " +
                        "(state_id TEXT NOT NULL PRIMARY KEY, color_index INTEGER NOT NULL)",
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "timewatcher.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
    }
}
