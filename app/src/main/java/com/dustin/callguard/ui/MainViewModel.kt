package com.dustin.callguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dustin.callguard.data.AllowedNumber
import com.dustin.callguard.data.AppDatabase
import com.dustin.callguard.digest.DigestScheduler
import com.dustin.callguard.data.NumberInfo
import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.data.ScreenerSettings
import com.dustin.callguard.data.ScreenerSettingsStore
import com.dustin.callguard.screening.NumberNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.get(application)
    private val settingsStore = ScreenerSettingsStore(application)

    private val _settings = MutableStateFlow(settingsStore.load())

    init {
        // WorkManager keeps queued runs across restarts; this just makes sure they match the settings
        DigestScheduler.reschedule(application)
    }

    private fun digestFields(settings: ScreenerSettings) = listOf(
        settings.notificationsEnabled,
        settings.dailyDigestEnabled,
        settings.dailyDigestMinutes,
        settings.weeklyDigestEnabled,
        settings.weeklyDigestDay,
        settings.weeklyDigestMinutes,
    )
    val settings: StateFlow<ScreenerSettings> = _settings.asStateFlow()

    val allowedNumbers: StateFlow<List<AllowedNumber>> = database.allowedNumberDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val screenedCalls: StateFlow<List<ScreenedCall>> = database.screenedCallDao()
        .observeRecent(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // the whole log (kept for 30 days), for trends and export
    val allCalls: StateFlow<List<ScreenedCall>> = database.screenedCallDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val numberInfo: StateFlow<Map<String, NumberInfo>> = database.numberInfoDao()
        .observeAll()
        .map { list -> list.associateBy { it.normalizedNumber } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun updateSettings(transform: (ScreenerSettings) -> ScreenerSettings) {
        val previous = _settings.value
        val updated = transform(previous)
        _settings.value = updated
        settingsStore.save(updated)
        if (digestFields(previous) != digestFields(updated)) {
            DigestScheduler.reschedule(getApplication<Application>())
        }
    }

    /** returns false when the number can't be normalized into something usable. */
    fun addAllowedNumber(rawNumber: String, label: String): Boolean {
        val normalizedNumber = NumberNormalizer.normalize(rawNumber) ?: return false
        viewModelScope.launch {
            database.allowedNumberDao().upsert(
                AllowedNumber(
                    normalizedNumber = normalizedNumber,
                    label = label.trim(),
                    addedAt = System.currentTimeMillis(),
                )
            )
        }
        return true
    }

    fun removeAllowedNumber(normalizedNumber: String) {
        viewModelScope.launch {
            database.allowedNumberDao().deleteByNumber(normalizedNumber)
        }
    }

    /** saves a note and tag for a number; an empty note and no tag removes the entry. */
    fun saveNumberInfo(normalizedNumber: String, note: String, tag: String) {
        viewModelScope.launch {
            val dao = database.numberInfoDao()
            if (note.isBlank() && tag.isEmpty()) {
                dao.deleteByNumber(normalizedNumber)
            } else {
                dao.upsert(NumberInfo(normalizedNumber, note.trim(), tag, System.currentTimeMillis()))
            }
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            database.screenedCallDao().clearAll()
        }
    }
}
