package com.dustin.callguard.digest

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dustin.callguard.data.ScreenerSettingsStore
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * keeps one queued work request per enabled digest, set for the next delivery time.
 * android may shift delivery by a few minutes to save battery.
 */
object DigestScheduler {
    private const val DAILY_WORK = "callguard_digest_daily"
    private const val WEEKLY_WORK = "callguard_digest_weekly"

    fun reschedule(context: Context) {
        val appContext = context.applicationContext
        val settings = ScreenerSettingsStore(appContext).load()
        val workManager = WorkManager.getInstance(appContext)
        val now = ZonedDateTime.now()

        if (settings.notificationsEnabled && settings.dailyDigestEnabled) {
            enqueue(
                workManager, DAILY_WORK, DigestWorker.KIND_DAILY,
                DigestSchedule.delayUntilNext(now, settings.dailyDigestMinutes),
            )
        } else {
            workManager.cancelUniqueWork(DAILY_WORK)
        }

        if (settings.notificationsEnabled && settings.weeklyDigestEnabled) {
            enqueue(
                workManager, WEEKLY_WORK, DigestWorker.KIND_WEEKLY,
                DigestSchedule.delayUntilNext(
                    now,
                    settings.weeklyDigestMinutes,
                    DayOfWeek.of(settings.weeklyDigestDay.coerceIn(1, 7)),
                ),
            )
        } else {
            workManager.cancelUniqueWork(WEEKLY_WORK)
        }
    }

    /** posts a digest right now so you can see what it looks like. */
    fun sendTestNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<DigestWorker>()
            .setInputData(Data.Builder().putString(DigestWorker.KEY_KIND, DigestWorker.KIND_TEST).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }

    private fun enqueue(workManager: WorkManager, name: String, kind: String, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<DigestWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(DigestWorker.KEY_KIND, kind).build())
            .build()
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }
}
