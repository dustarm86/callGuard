package com.dustin.callguard.data

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDateTime

data class ScreenerSettings(
    val screeningEnabled: Boolean = true,
    val workHoursOnly: Boolean = false,
    val workStartMinutes: Int = 8 * 60,
    val workEndMinutes: Int = 17 * 60,
    val workDaysMask: Int = DEFAULT_WORK_DAYS_MASK,
    val repeatBypassEnabled: Boolean = false,
    val repeatWindowMinutes: Int = 3,
    val silenceFailedVerification: Boolean = true,
    // the user's own number, used to spot neighbor spoofing. stays on the device.
    val ownNumber: String = "",
) {
    fun isWorkDay(day: DayOfWeek): Boolean =
        (workDaysMask and (1 shl (day.value - 1))) != 0

    fun withWorkDayToggled(day: DayOfWeek): ScreenerSettings =
        copy(workDaysMask = workDaysMask xor (1 shl (day.value - 1)))

    fun isWithinWorkHours(now: LocalDateTime): Boolean {
        if (!isWorkDay(now.dayOfWeek)) return false
        val minutes = now.hour * 60 + now.minute
        return minutes in workStartMinutes until workEndMinutes
    }

    companion object {
        // monday through friday
        const val DEFAULT_WORK_DAYS_MASK = 0b0011111
    }
}

class ScreenerSettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("callguard_settings", Context.MODE_PRIVATE)

    fun load(): ScreenerSettings {
        val defaults = ScreenerSettings()
        return ScreenerSettings(
            screeningEnabled = prefs.getBoolean(KEY_ENABLED, defaults.screeningEnabled),
            workHoursOnly = prefs.getBoolean(KEY_WORK_HOURS_ONLY, defaults.workHoursOnly),
            workStartMinutes = prefs.getInt(KEY_WORK_START, defaults.workStartMinutes),
            workEndMinutes = prefs.getInt(KEY_WORK_END, defaults.workEndMinutes),
            workDaysMask = prefs.getInt(KEY_WORK_DAYS, defaults.workDaysMask),
            repeatBypassEnabled = prefs.getBoolean(KEY_REPEAT_ENABLED, defaults.repeatBypassEnabled),
            repeatWindowMinutes = prefs.getInt(KEY_REPEAT_WINDOW, defaults.repeatWindowMinutes),
            silenceFailedVerification = prefs.getBoolean(KEY_VERIFICATION, defaults.silenceFailedVerification),
            ownNumber = prefs.getString(KEY_OWN_NUMBER, defaults.ownNumber).orEmpty(),
        )
    }

    fun save(settings: ScreenerSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.screeningEnabled)
            .putBoolean(KEY_WORK_HOURS_ONLY, settings.workHoursOnly)
            .putInt(KEY_WORK_START, settings.workStartMinutes)
            .putInt(KEY_WORK_END, settings.workEndMinutes)
            .putInt(KEY_WORK_DAYS, settings.workDaysMask)
            .putBoolean(KEY_REPEAT_ENABLED, settings.repeatBypassEnabled)
            .putInt(KEY_REPEAT_WINDOW, settings.repeatWindowMinutes)
            .putBoolean(KEY_VERIFICATION, settings.silenceFailedVerification)
            .putString(KEY_OWN_NUMBER, settings.ownNumber)
            .apply()
    }

    private companion object {
        const val KEY_ENABLED = "screeningEnabled"
        const val KEY_WORK_HOURS_ONLY = "workHoursOnly"
        const val KEY_WORK_START = "workStartMinutes"
        const val KEY_WORK_END = "workEndMinutes"
        const val KEY_WORK_DAYS = "workDaysMask"
        const val KEY_REPEAT_ENABLED = "repeatBypassEnabled"
        const val KEY_REPEAT_WINDOW = "repeatWindowMinutes"
        const val KEY_VERIFICATION = "silenceFailedVerification"
        const val KEY_OWN_NUMBER = "ownNumber"
    }
}
