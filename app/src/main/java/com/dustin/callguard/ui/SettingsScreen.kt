package com.dustin.callguard.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dustin.callguard.data.ScreenerSettings
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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
    }
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
            .padding(vertical = 8.dp),
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
