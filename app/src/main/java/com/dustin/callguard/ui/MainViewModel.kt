package com.dustin.callguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dustin.callguard.data.AllowedNumber
import com.dustin.callguard.data.AllowedPrefix
import com.dustin.callguard.data.AppDatabase
import com.dustin.callguard.digest.DigestScheduler
import com.dustin.callguard.data.NumberInfo
import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.data.ScreenerSettings
import com.dustin.callguard.data.ScreenerSettingsStore
import com.dustin.callguard.complaints.ComplaintDownloader
import com.dustin.callguard.complaints.ComplaintIndex
import com.dustin.callguard.complaints.ComplaintStore
import com.dustin.callguard.screening.NumberNormalizer
import com.dustin.callguard.screening.NxxDirectory
import com.dustin.callguard.screening.NxxDirectoryLoader
import com.dustin.callguard.screening.PrefixNormalizer
import kotlinx.coroutines.Dispatchers
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

    // keeps the screens in step with changes made elsewhere, like the quick settings tile
    private val settingsListener = settingsStore.registerChangeListener {
        _settings.value = settingsStore.load()
    }

    init {
        // WorkManager keeps queued runs across restarts; this just makes sure they match the settings
        DigestScheduler.reschedule(application)
        viewModelScope.launch { database.allowedNumberDao().deleteExpired(System.currentTimeMillis()) }
    }

    // offline carrier data, null until it has loaded (or when this install doesn't include it)
    private val _directory = MutableStateFlow<NxxDirectory?>(null)
    val directory: StateFlow<NxxDirectory?> = _directory.asStateFlow()

    private val complaintStore = ComplaintStore(application)
    private val _complaints = MutableStateFlow<ComplaintIndex?>(null)
    val complaints: StateFlow<ComplaintIndex?> = _complaints.asStateFlow()
    private val _complaintStatus = MutableStateFlow("")
    val complaintStatus: StateFlow<String> = _complaintStatus.asStateFlow()
    private val _complaintsRefreshing = MutableStateFlow(false)
    val complaintsRefreshing: StateFlow<Boolean> = _complaintsRefreshing.asStateFlow()

    // declared after the flows above so they exist before the loaders can finish
    init {
        viewModelScope.launch(Dispatchers.IO) { _directory.value = NxxDirectoryLoader.load(application) }
        viewModelScope.launch(Dispatchers.IO) { _complaints.value = complaintStore.loadIndex() }
    }

    /** downloads the ftc's recent daily files. only runs when the user taps the button. */
    fun refreshComplaints() {
        if (_complaintsRefreshing.value) return
        _complaintsRefreshing.value = true
        _complaintStatus.value = ""
        viewModelScope.launch(Dispatchers.IO) {
            val result = ComplaintDownloader(complaintStore).refresh(java.time.LocalDate.now())
            _complaints.value = complaintStore.loadIndex()
            _complaintStatus.value = result.summary()
            _complaintsRefreshing.value = false
        }
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

    val allowedPrefixes: StateFlow<List<AllowedPrefix>> = database.allowedPrefixDao()
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

    override fun onCleared() {
        settingsStore.unregisterChangeListener(settingsListener)
        super.onCleared()
    }

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

    /** allows a number for a limited time, like a delivery driver or an appointment callback. */
    fun allowTemporarily(normalizedNumber: String, durationMillis: Long) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            database.allowedNumberDao().upsert(
                AllowedNumber(
                    normalizedNumber = normalizedNumber,
                    label = "temporary",
                    addedAt = now,
                    expiresAt = now + durationMillis,
                )
            )
        }
    }

    /** returns false when the prefix isn't 8 or 9 digits. */
    fun addAllowedPrefix(rawPrefix: String, label: String): Boolean {
        val prefix = PrefixNormalizer.normalize(rawPrefix) ?: return false
        viewModelScope.launch {
            database.allowedPrefixDao().upsert(
                AllowedPrefix(prefix = prefix, label = label.trim(), addedAt = System.currentTimeMillis())
            )
        }
        return true
    }

    fun removeAllowedPrefix(prefix: String) {
        viewModelScope.launch { database.allowedPrefixDao().deleteByPrefix(prefix) }
    }

    /** picks up changes made outside the app, like the quick settings tile. */
    fun reloadSettings() {
        _settings.value = settingsStore.load()
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
