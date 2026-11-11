package com.dustin.callguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dustin.callguard.data.AllowedNumber
import com.dustin.callguard.data.AppDatabase
import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.data.ScreenerSettings
import com.dustin.callguard.data.ScreenerSettingsStore
import com.dustin.callguard.screening.NumberNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.get(application)
    private val settingsStore = ScreenerSettingsStore(application)

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<ScreenerSettings> = _settings.asStateFlow()

    val allowedNumbers: StateFlow<List<AllowedNumber>> = database.allowedNumberDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val screenedCalls: StateFlow<List<ScreenedCall>> = database.screenedCallDao()
        .observeRecent(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateSettings(transform: (ScreenerSettings) -> ScreenerSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        settingsStore.save(updated)
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

    fun clearLog() {
        viewModelScope.launch {
            database.screenedCallDao().clearAll()
        }
    }
}
