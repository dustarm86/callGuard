package com.dustin.callguard.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dustin.callguard.data.VerificationStatus
import com.dustin.callguard.screening.SuspicionInput
import com.dustin.callguard.screening.SuspicionResult
import com.dustin.callguard.screening.SuspicionScorer
import com.dustin.callguard.trends.ReportBuilder
import com.dustin.callguard.trends.TrendAnalyzer
import java.time.ZoneId
import java.time.ZonedDateTime
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CallGuardTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallGuardApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current

    var isRoleHeld by remember { mutableStateOf(false) }
    var hasContactsPermission by remember { mutableStateOf(false) }

    fun refreshStatus() {
        isRoleHeld = context.getSystemService(RoleManager::class.java)
            ?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    LifecycleResumeEffect(Unit) {
        refreshStatus()
        onPauseOrDispose { }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshStatus() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshStatus() }

    val settings by viewModel.settings.collectAsState()
    val allowedNumbers by viewModel.allowedNumbers.collectAsState()
    val screenedCalls by viewModel.screenedCalls.collectAsState()
    val numberInfo by viewModel.numberInfo.collectAsState()
    val allCalls by viewModel.allCalls.collectAsState()
    val trends = remember(allCalls, settings.ownNumber) {
        TrendAnalyzer.analyze(allCalls, ZoneId.systemDefault(), settings.ownNumber)
    }
    var openedNumber by rememberSaveable { mutableStateOf<String?>(null) }
    val callCounts = remember(screenedCalls) { screenedCalls.groupingBy { it.normalizedNumber }.eachCount() }
    val failedVerificationNumbers = remember(screenedCalls) {
        screenedCalls
            .filter { it.verificationStatus == VerificationStatus.FAILED }
            .mapNotNull { it.normalizedNumber }
            .toSet()
    }
    val suspicionFor: (String) -> SuspicionResult = { number ->
        SuspicionScorer.score(
            SuspicionInput(
                normalizedNumber = number,
                callCount = callCounts[number] ?: 0,
                verificationFailed = number in failedVerificationNumbers,
                ownNumber = settings.ownNumber,
                allowedNumbers = allowedNumbers.map { it.normalizedNumber },
                tag = numberInfo[number]?.tag,
            )
        )
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("callGuard") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    label = { Text("log") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("allowlist") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text("trends") },
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("settings") },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (openedNumber == null) StatusCard(
                isRoleHeld = isRoleHeld,
                hasContactsPermission = hasContactsPermission,
                onRequestRole = {
                    val roleManager = context.getSystemService(RoleManager::class.java)
                    if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                        roleLauncher.launch(
                            roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                        )
                    }
                },
                onRequestContacts = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
            )

            when (selectedTab) {
                0 -> {
                    val allowedSet = allowedNumbers.map { it.normalizedNumber }.toSet()
                    val opened = openedNumber
                    if (opened != null) {
                        CallDetailScreen(
                            normalizedNumber = opened,
                            calls = screenedCalls,
                            info = numberInfo[opened],
                            isAllowed = opened in allowedSet,
                            suspicion = suspicionFor(opened),
                            onSave = { note, tag -> viewModel.saveNumberInfo(opened, note, tag) },
                            onToggleAllow = {
                                if (opened in allowedSet) {
                                    viewModel.removeAllowedNumber(opened)
                                } else {
                                    viewModel.addAllowedNumber(opened, "")
                                }
                            },
                            onBack = { openedNumber = null },
                        )
                    } else {
                        LogScreen(
                            calls = screenedCalls,
                            allowedNormalizedNumbers = allowedSet,
                            numberInfo = numberInfo,
                            suspicionFor = suspicionFor,
                            onOpen = { call -> openedNumber = call.normalizedNumber },
                            onAllow = { call -> viewModel.addAllowedNumber(call.rawNumber.orEmpty(), "") },
                            onClear = viewModel::clearLog,
                        )
                    }
                }
                1 -> AllowlistScreen(
                    allowedNumbers = allowedNumbers,
                    onAdd = viewModel::addAllowedNumber,
                    onRemove = viewModel::removeAllowedNumber,
                )
                2 -> TrendsScreen(
                    trends = trends,
                    hasOwnNumber = settings.ownNumber.isNotBlank(),
                    onExportCsv = { ExportShare.shareCsv(context, allCalls) },
                    onExportReport = {
                        val html = ReportBuilder.build(allCalls, numberInfo, ZoneId.systemDefault(), ZonedDateTime.now())
                        ExportShare.shareReport(context, html)
                    },
                )
                else -> SettingsScreen(
                    settings = settings,
                    onChange = viewModel::updateSettings,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    isRoleHeld: Boolean,
    hasContactsPermission: Boolean,
    onRequestRole: () -> Unit,
    onRequestContacts: () -> Unit,
) {
    val isReady = isRoleHeld && hasContactsPermission
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isReady) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isReady) {
                Text("active: unknown callers are silenced", style = MaterialTheme.typography.titleSmall)
            } else {
                Text("not screening yet", style = MaterialTheme.typography.titleSmall)
                if (!hasContactsPermission) {
                    Text("contacts access is needed so your contacts still ring. until it is granted, every call rings.")
                    Button(onClick = onRequestContacts) { Text("grant contacts access") }
                }
                if (!isRoleHeld) {
                    Text("callGuard has to be your default caller id & spam app so android sends it every incoming call.")
                    Button(onClick = onRequestRole) { Text("make default screener") }
                }
            }
        }
    }
}
