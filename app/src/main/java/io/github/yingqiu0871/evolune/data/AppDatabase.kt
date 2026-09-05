package io.github.yingqiu0871.evolune.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.yingqiu0871.evolune.data.migration.MIGRATION_2_3

/**
 * 应用数据库
 */
@Database(
    entities = [
        DoseEventEntity::class,
        MedicationPlanEntity::class,
        ScheduledDoseSlotEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun doseEventDao(): DoseEventDao
    abstract fun medicationPlanDao(): MedicationPlanDao
    abstract fun scheduledDoseSlotDao(): ScheduledDoseSlotDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 数据库迁移：从版本1到版本2
         * 添加medication_plans表，保留dose_events表的所有数据
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建medication_plans表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `medication_plans` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `route` TEXT NOT NULL,
                        `ester` TEXT NOT NULL,
                        `doseMG` REAL NOT NULL,
                        `scheduleType` TEXT NOT NULL,
                        `timeOfDay` TEXT NOT NULL,
                        `daysOfWeek` TEXT NOT NULL,
                        `intervalDays` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `extras` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "evolune_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
