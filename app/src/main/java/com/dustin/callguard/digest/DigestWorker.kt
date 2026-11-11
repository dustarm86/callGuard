package com.dustin.callguard.digest

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dustin.callguard.data.AppDatabase
import com.dustin.callguard.data.ScreenerSettingsStore
import java.time.ZoneId

class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val kindName = inputData.getString(KEY_KIND) ?: return Result.success()
        val isTest = kindName == KIND_TEST
        val settings = ScreenerSettingsStore(applicationContext).load()

        if (!isTest) {
            // the schedule may have been switched off since this run was queued
            val stillEnabled = settings.notificationsEnabled && when (kindName) {
                KIND_DAILY -> settings.dailyDigestEnabled
                KIND_WEEKLY -> settings.weeklyDigestEnabled
                else -> false
            }
            if (!stillEnabled) return Result.success()
        }

        val kind = if (kindName == KIND_WEEKLY) DigestKind.WEEKLY else DigestKind.DAILY
        try {
            val windowMillis = if (kind == DigestKind.WEEKLY) WEEK_MILLIS else DAY_MILLIS
            val calls = AppDatabase.get(applicationContext)
                .screenedCallDao()
                .getSince(System.currentTimeMillis() - windowMillis)
            val content = DigestBuilder.build(kind, calls, ZoneId.systemDefault())
            val skip = !isTest && content.silencedCount == 0 && settings.skipDigestWhenZero
            if (!skip) {
                val posted = DigestNotifier.post(applicationContext, content, notificationId = kind.ordinal + 100)
                if (!posted) Log.w(TAG, "digest not shown: notification permission missing")
            }
        } catch (e: Exception) {
            Log.w(TAG, "digest failed", e)
        } finally {
            // queue tomorrow's (or next week's) run, whatever happened today
            if (!isTest) DigestScheduler.reschedule(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KIND_DAILY = "daily"
        const val KIND_WEEKLY = "weekly"
        const val KIND_TEST = "test"
        private const val TAG = "DigestWorker"
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val WEEK_MILLIS = 7 * DAY_MILLIS
    }
}
