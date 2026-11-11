package com.dustin.callguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AllowedNumber::class, ScreenedCall::class, NumberInfo::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun allowedNumberDao(): AllowedNumberDao
    abstract fun screenedCallDao(): ScreenedCallDao
    abstract fun numberInfoDao(): NumberInfoDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // v2 adds per-number notes and tags; existing tables are untouched
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `number_info` (" +
                        "`normalizedNumber` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`tag` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`normalizedNumber`))"
                )
            }
        }

        // v3 stores the stir/shaken verification result for each screened call
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `screened_calls` ADD COLUMN `verificationStatus` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // v4 lets allowlist entries expire (temporary allow)
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `allowed_numbers` ADD COLUMN `expiresAt` INTEGER")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "callguard.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
