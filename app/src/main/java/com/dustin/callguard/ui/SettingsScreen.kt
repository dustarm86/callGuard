package com.dustin.callguard.ui

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dustin.callguard.data.ScreenerSettings
import com.dustin.callguard.digest.DigestNotifier
import com.dustin.callguard.digest.DigestScheduler
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: ScreenerSettings,
    onChange: ((ScreenerSettings) -> ScreenerSettings) -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "screening") {
            SwitchRow(
                title = "screening on",
                subtitle = "master switch. when off, every call rings normally.",
                checked = settings.screeningEnabled,
                onCheckedChange = { checked -> onChange { it.copy(screeningEnabled = checked) } },
            )

            SwitchRow(
                title = "only during work hours",
                subtitle = "outside the schedule below, all calls ring.",
                checked = settings.workHoursOnly,
                onCheckedChange = { checked -> onChange { it.copy(workHoursOnly = checked) } },
            )

            if (settings.workHoursOnly) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        showTimePicker(context, settings.workStartMinutes) { picked ->
                            onChange { it.copy(workStartMinutes = picked) }
                        }
                    }) { Text("start ${formatMinutes(settings.workStartMinutes)}") }
                    OutlinedButton(onClick = {
                        showTimePicker(context, settings.workEndMinutes) { picked ->
                            onChange { it.copy(workEndMinutes = picked) }
                        }
                    }) { Text("end ${formatMinutes(settings.workEndMinutes)}") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.values().forEach { day ->
                        FilterChip(
                            selected = settings.isWorkDay(day),
                            onClick = { onChange { it.withWorkDayToggled(day) } },
                            label = { Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault())) },
                        )
                    }
                }
            }

            SwitchRow(
                title = "silence failed caller verification",
                subtitle = "catches spoofed numbers, even ones that match a contact. needs carrier support, so it won't catch everything.",
                checked = settings.silenceFailedVerification,
                onCheckedChange = { checked -> onChange { it.copy(silenceFailedVerification = checked) } },
            )
        }
        SectionCard(title = "your number") {
            Text(
                "used to spot spoofed numbers that copy the start of yours. it never leaves this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = settings.ownNumber,
                onValueChange = { typed -> onChange { it.copy(ownNumber = typed) } },
                label = { Text("phone number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SectionCard(title = "repeat callers and reject") {
            SwitchRow(
                title = "let repeat callers through",
                subtitle = "if a silenced number calls again within the window, let it ring. real people retry, most spammers don't. off by default.",
                checked = settings.repeatBypassEnabled,
                onCheckedChange = { checked -> onChange { it.copy(repeatBypassEnabled = checked) } },
            )

            if (settings.repeatBypassEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 5, 10).forEach { minutes ->
                        FilterChip(
                            selected = settings.repeatWindowMinutes == minutes,
                            onClick = { onChange { it.copy(repeatWindowMinutes = minutes) } },
                            label = { Text("$minutes min") },
                        )
                    }
                }
            }

            SwitchRow(
                title = "reject instead of silence",
                subtitle = "ends an unknown call right away instead of letting it ring silently. the caller usually hears busy or reaches voicemail, which can tell spammers the line is live. off by default.",
                checked = settings.rejectInsteadOfSilence,
                onCheckedChange = { checked -> onChange { it.copy(rejectInsteadOfSilence = checked) } },
            )
        }
        SectionCard(title = "summaries") {
            NotificationSettings(settings = settings, onChange = onChange)
        }
        SectionCard(title = "appearance") {
            SwitchRow(
                title = "dark mode",
                subtitle = "on keeps callGuard dark no matter what. off follows your phone's light or dark setting.",
                checked = settings.alwaysDark,
                onCheckedChange = { checked -> onChange { it.copy(alwaysDark = checked) } },
            )
            SwitchRow(
                title = "use my phone's wallpaper colors",
                subtitle = "off uses callGuard's green and violet. on follows your wallpaper theme (android 12 and up).",
                checked = settings.useSystemColors,
                onCheckedChange = { checked -> onChange { it.copy(useSystemColors = checked) } },
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun NotificationSettings(
    settings: ScreenerSettings,
    onChange: ((ScreenerSettings) -> ScreenerSettings) -> Unit,
) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(DigestNotifier.hasPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    SwitchRow(
        title = "summary notifications",
        subtitle = "master switch for the daily and weekly summaries below. off by default.",
        checked = settings.notificationsEnabled,
        onCheckedChange = { checked ->
            onChange { it.copy(notificationsEnabled = checked) }
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionGranted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )

    if (!settings.notificationsEnabled) return

    if (!permissionGranted) {
        Text(
            "android is blocking notifications for callGuard. allow them in android's app settings for callGuard, then reopen this screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    SwitchRow(
        title = "daily summary",
        subtitle = "calls silenced in the last 24 hours.",
        checked = settings.dailyDigestEnabled,
        onCheckedChange = { checked -> onChange { it.copy(dailyDigestEnabled = checked) } },
    )
    if (settings.dailyDigestEnabled) {
        OutlinedButton(onClick = {
            showTimePicker(context, settings.dailyDigestMinutes) { picked ->
                onChange { it.copy(dailyDigestMinutes = picked) }
            }
        }) { Text("every day at ${formatMinutes(settings.dailyDigestMinutes)}") }
    }

    SwitchRow(
        title = "weekly summary",
        subtitle = "calls silenced in the last 7 days.",
        checked = settings.weeklyDigestEnabled,
        onCheckedChange = { checked -> onChange { it.copy(weeklyDigestEnabled = checked) } },
    )
    if (settings.weeklyDigestEnabled) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DayOfWeek.values().forEach { day ->
                FilterChip(
                    selected = settings.weeklyDigestDay == day.value,
                    onClick = { onChange { it.copy(weeklyDigestDay = day.value) } },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                )
            }
        }
        OutlinedButton(onClick = {
            showTimePicker(context, settings.weeklyDigestMinutes) { picked ->
                onChange { it.copy(weeklyDigestMinutes = picked) }
            }
        }) {
            val dayName = DayOfWeek.of(settings.weeklyDigestDay.coerceIn(1, 7))
                .getDisplayName(TextStyle.FULL, Locale.getDefault())
            Text("every $dayName at ${formatMinutes(settings.weeklyDigestMinutes)}")
        }
    }

    SwitchRow(
        title = "skip empty summaries",
        subtitle = "don't send a notification when nothing was silenced.",
        checked = settings.skipDigestWhenZero,
        onCheckedChange = { checked -> onChange { it.copy(skipDigestWhenZero = checked) } },
    )

    val notify = LocalNotify.current
    Button(onClick = {
        DigestScheduler.sendTestNow(context)
        notify("test summary queued, it can take a minute to show up in your notifications")
    }) { Text("send a test summary now") }
    Text(
        "delivery can be a few minutes off because android batches background work to save battery.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private fun formatMinutes(minutes: Int): String =
    LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofPattern("h:mm a"))

private fun showTimePicker(
    context: android.content.Context,
    initialMinutes: Int,
    onPicked: (Int) -> Unit,
) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        initialMinutes / 60,
        initialMinutes % 60,
        false,
    ).show()
}
