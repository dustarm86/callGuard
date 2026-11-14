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
import com.dustin.callguard.complaints.ComplaintCsv
import com.dustin.callguard.complaints.ComplaintDownloader
import com.dustin.callguard.complaints.ComplaintFileName
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    // the carrier data update: progress lines for the screen, and whether one is running
    private val _carrierDataLines = MutableStateFlow<List<String>>(emptyList())
    val carrierDataLines: StateFlow<List<String>> = _carrierDataLines.asStateFlow()
    private val _carrierDataBusy = MutableStateFlow(false)
    val carrierDataBusy: StateFlow<Boolean> = _carrierDataBusy.asStateFlow()

    /** downloads newer carrier data, trying each source in turn. only runs when the user taps the button. */
    fun updateCarrierData() {
        if (_carrierDataBusy.value) return
        _carrierDataBusy.value = true
        _carrierDataLines.value = listOf("result: updating…")
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val store = com.dustin.callguard.carrierdata.CarrierStore(app)
            val today = java.time.LocalDate.now().let { it.year * 10000 + it.monthValue * 100 + it.dayOfMonth }
            val current = _directory.value
            val lines = try {
                com.dustin.callguard.carrierdata.CarrierUpdater(
                    fetch = { source -> com.dustin.callguard.carrierdata.CarrierFetcher.fetch(source, today) },
                    install = { built, source -> store.install(built, source.label, today) },
                ).run(
                    sources = com.dustin.callguard.carrierdata.CarrierSources.all,
                    currentNanpaAsOf = current?.asOf ?: 0,
                    currentBlocksAsOf = current?.blocks?.asOf ?: 0,
                    progress = { _carrierDataLines.value = it },
                )
            } catch (e: Exception) {
                listOf("result: couldn't update: ${e.message ?: e.javaClass.simpleName}")
            }
            val updated = lines.first().startsWith("result: updated")
            if (updated) _directory.value = NxxDirectoryLoader.load(app)
            _carrierDataLines.value = lines
            _carrierDataBusy.value = false
            _messages.tryEmit(if (updated) "carrier data updated" else "couldn't update carrier data")
        }
    }

    // contact numbers for the neighbor check; held in memory only, never stored or logged
    private val _contactNumbers = MutableStateFlow<Set<String>>(emptySet())
    val contactNumbers: StateFlow<Set<String>> = _contactNumbers.asStateFlow()

    fun reloadContacts() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication<Application>(),
            android.Manifest.permission.READ_CONTACTS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            _contactNumbers.value = emptySet()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _contactNumbers.value = com.dustin.callguard.screening.ContactLookup.allNumbers(getApplication<Application>())
        }
    }

    // short confirmations for the snackbar; dropped when nothing is showing yet
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // declared after the flows above so they exist before the loaders can finish
    init {
        viewModelScope.launch(Dispatchers.IO) { _directory.value = NxxDirectoryLoader.load(application) }
        viewModelScope.launch(Dispatchers.IO) { _complaints.value = complaintStore.loadIndex() }
    }

    /** downloads the ftc's recent daily files. only runs when the user taps the button. */
    /** imports ftc csv files the person downloaded in a browser; the day comes from each file's name. */
    fun importComplaintFiles(uris: List<android.net.Uri>) {
        if (uris.isEmpty() || _complaintsRefreshing.value) return
        _complaintsRefreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            var imported = 0
            var reports = 0
            val problems = mutableListOf<String>()
            for (uri in uris) {
                val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
                try {
                    val parsed = ComplaintCsv.parse(resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty())
                    val day = ComplaintFileName.dayFrom(name) ?: parsed.maxOfOrNull { it.day }?.let(java.time.LocalDate::parse)
                    if (parsed.isEmpty() || day == null) {
                        problems += "${name ?: "a file"} isn't an ftc report file"
                    } else {
                        complaintStore.saveDay(day, parsed)
                        imported++
                        reports += parsed.size
                    }
                } catch (e: Exception) {
                    problems += "${name ?: "a file"}: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            complaintStore.prune(java.time.LocalDate.now().minusDays(ComplaintDownloader.RETENTION_DAYS.toLong()))
            _complaints.value = complaintStore.loadIndex()
            val summary = buildString {
                append("imported $imported ${if (imported == 1) "file" else "files"} ($reports reports)")
                if (problems.isNotEmpty()) append(", ${problems.size} skipped: ${problems.first()}")
            }
            _complaintStatus.value = summary
            _messages.tryEmit(summary)
            _complaintsRefreshing.value = false
        }
    }

    fun refreshComplaints() {
        if (_complaintsRefreshing.value) return
        _complaintsRefreshing.value = true
        _complaintStatus.value = ""
        viewModelScope.launch(Dispatchers.IO) {
            val result = ComplaintDownloader(complaintStore).refresh(java.time.LocalDate.now())
            _complaints.value = complaintStore.loadIndex()
            _complaintStatus.value = result.summary()
            _messages.tryEmit(
                if (result.downloaded > 0) "downloaded ${result.downloaded} new ${if (result.downloaded == 1) "day" else "days"} (${result.reports} reports)"
                else "no new complaint data downloaded, see the status under the button"
            )
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
            _messages.tryEmit("allowed ${NumberNormalizer.format(normalizedNumber)}")
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
            _messages.tryEmit("allowed ${NumberNormalizer.format(normalizedNumber)} for ${durationMillis / 3_600_000} hours")
        }
    }

    /** returns false when the prefix isn't 8 or 9 digits. */
    fun addAllowedPrefix(rawPrefix: String, label: String): Boolean {
        val prefix = PrefixNormalizer.normalize(rawPrefix) ?: return false
        viewModelScope.launch {
            database.allowedPrefixDao().upsert(
                AllowedPrefix(prefix = prefix, label = label.trim(), addedAt = System.currentTimeMillis())
            )
            _messages.tryEmit("allowed every number starting $prefix")
        }
        return true
    }

    fun removeAllowedPrefix(prefix: String) {
        viewModelScope.launch {
            database.allowedPrefixDao().deleteByPrefix(prefix)
            _messages.tryEmit("removed prefix $prefix")
        }
    }

    /** picks up changes made outside the app, like the quick settings tile. */
    fun reloadSettings() {
        _settings.value = settingsStore.load()
    }

    fun removeAllowedNumber(normalizedNumber: String) {
        viewModelScope.launch {
            database.allowedNumberDao().deleteByNumber(normalizedNumber)
            _messages.tryEmit("removed ${NumberNormalizer.format(normalizedNumber)} from the allowlist")
        }
    }

    /** saves a note and tag for a number; an empty note and no tag removes the entry. */
    fun saveNumberInfo(normalizedNumber: String, note: String, tag: String) {
        viewModelScope.launch {
            val dao = database.numberInfoDao()
            if (note.isBlank() && tag.isEmpty()) {
                dao.deleteByNumber(normalizedNumber)
                _messages.tryEmit("note and tag cleared")
            } else {
                dao.upsert(NumberInfo(normalizedNumber, note.trim(), tag, System.currentTimeMillis()))
                _messages.tryEmit("saved")
            }
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            database.screenedCallDao().clearAll()
            _messages.tryEmit("log cleared")
        }
    }
}
