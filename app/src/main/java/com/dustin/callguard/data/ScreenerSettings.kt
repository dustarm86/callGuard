package com.dustin.callguard.data

import android.content.Context
import android.content.SharedPreferences
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
    // opt-in: summaries need notification permission
    val notificationsEnabled: Boolean = false,
    val dailyDigestEnabled: Boolean = true,
    val dailyDigestMinutes: Int = 17 * 60 + 30,
    val weeklyDigestEnabled: Boolean = false,
    // DayOfWeek.value: 1 = monday ... 7 = sunday
    val weeklyDigestDay: Int = 7,
    val weeklyDigestMinutes: Int = 9 * 60,
    val skipDigestWhenZero: Boolean = true,
    // off = silence unknown callers (default); on = reject them instead
    val rejectInsteadOfSilence: Boolean = false,
    // off = callGuard's green and violet colors; on = the phone's wallpaper colors
    val useSystemColors: Boolean = false,
    // on = always use the dark look; off = follow the phone's light or dark setting
    val alwaysDark: Boolean = true,
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
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFS, defaults.notificationsEnabled),
            dailyDigestEnabled = prefs.getBoolean(KEY_DAILY, defaults.dailyDigestEnabled),
            dailyDigestMinutes = prefs.getInt(KEY_DAILY_TIME, defaults.dailyDigestMinutes),
            weeklyDigestEnabled = prefs.getBoolean(KEY_WEEKLY, defaults.weeklyDigestEnabled),
            weeklyDigestDay = prefs.getInt(KEY_WEEKLY_DAY, defaults.weeklyDigestDay),
            weeklyDigestMinutes = prefs.getInt(KEY_WEEKLY_TIME, defaults.weeklyDigestMinutes),
            skipDigestWhenZero = prefs.getBoolean(KEY_SKIP_ZERO, defaults.skipDigestWhenZero),
            rejectInsteadOfSilence = prefs.getBoolean(KEY_REJECT, defaults.rejectInsteadOfSilence),
            useSystemColors = prefs.getBoolean(KEY_SYSTEM_COLORS, defaults.useSystemColors),
            alwaysDark = prefs.getBoolean(KEY_ALWAYS_DARK, defaults.alwaysDark),
        )
    }

    /**
     * calls [onChange] whenever settings are saved anywhere in the app, including the quick settings
     * tile. the returned listener must be kept (prefs only hold it weakly) and passed to
     * [unregisterChangeListener] when done.
     */
    fun registerChangeListener(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
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
            .putBoolean(KEY_NOTIFS, settings.notificationsEnabled)
            .putBoolean(KEY_DAILY, settings.dailyDigestEnabled)
            .putInt(KEY_DAILY_TIME, settings.dailyDigestMinutes)
            .putBoolean(KEY_WEEKLY, settings.weeklyDigestEnabled)
            .putInt(KEY_WEEKLY_DAY, settings.weeklyDigestDay)
            .putInt(KEY_WEEKLY_TIME, settings.weeklyDigestMinutes)
            .putBoolean(KEY_SKIP_ZERO, settings.skipDigestWhenZero)
            .putBoolean(KEY_REJECT, settings.rejectInsteadOfSilence)
            .putBoolean(KEY_SYSTEM_COLORS, settings.useSystemColors)
            .putBoolean(KEY_ALWAYS_DARK, settings.alwaysDark)
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
        const val KEY_NOTIFS = "notificationsEnabled"
        const val KEY_DAILY = "dailyDigestEnabled"
        const val KEY_DAILY_TIME = "dailyDigestMinutes"
        const val KEY_WEEKLY = "weeklyDigestEnabled"
        const val KEY_WEEKLY_DAY = "weeklyDigestDay"
        const val KEY_WEEKLY_TIME = "weeklyDigestMinutes"
        const val KEY_SKIP_ZERO = "skipDigestWhenZero"
        const val KEY_REJECT = "rejectInsteadOfSilence"
        const val KEY_SYSTEM_COLORS = "useSystemColors"
        const val KEY_ALWAYS_DARK = "alwaysDark"
    }
}
