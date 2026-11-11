package com.dustin.callguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AllowedNumber::class, ScreenedCall::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun allowedNumberDao(): AllowedNumberDao
    abstract fun screenedCallDao(): ScreenedCallDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "callguard.db",
                ).build().also { instance = it }
            }
    }
}
