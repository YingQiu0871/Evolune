package io.github.yingqiu0871.evolune.data.lab

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Separate Room database for PK 2.0 lab results.
 *
 * Kept apart from [io.github.yingqiu0871.evolune.data.AppDatabase] so the sealed v3 schema,
 * its migration chain and downgrade safety are untouched: an older app version never opens this
 * file. Excluded from Android cloud backup and device transfer by the existing
 * `domain="database" path="."` rules.
 */
@Database(
    entities = [LabResultEntity::class],
    version = 1,
    exportSchema = true
)
abstract class LabDatabase : RoomDatabase() {
    abstract fun labResultDao(): LabResultDao

    companion object {
        const val DATABASE_NAME = "evolune_labs"

        @Volatile
        private var INSTANCE: LabDatabase? = null

        fun getDatabase(context: Context): LabDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LabDatabase::class.java,
                    DATABASE_NAME
                ).build().also { INSTANCE = it }
            }
    }
}
