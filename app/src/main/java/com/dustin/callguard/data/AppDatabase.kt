package com.dustin.callguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AllowedNumber::class, AllowedPrefix::class, ScreenedCall::class, NumberInfo::class],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun allowedNumberDao(): AllowedNumberDao
    abstract fun screenedCallDao(): ScreenedCallDao
    abstract fun numberInfoDao(): NumberInfoDao
    abstract fun allowedPrefixDao(): AllowedPrefixDao

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

        // v5 adds allowlisted number blocks (prefixes)
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `allowed_prefixes` (" +
                        "`prefix` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`prefix`))"
                )
            }
        }

        // v6 stores screener timing for each call, so slow or cold-start answers can be spotted later
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `screened_calls` ADD COLUMN `receivedDelayMs` INTEGER")
                db.execSQL("ALTER TABLE `screened_calls` ADD COLUMN `decisionMs` INTEGER")
                db.execSQL("ALTER TABLE `screened_calls` ADD COLUMN `coldStart` INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v7 keeps android's raw call details for each call
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `screened_calls` ADD COLUMN `detailsSnapshot` TEXT")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "callguard.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { instance = it }
            }
    }
}
