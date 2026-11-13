package com.dustin.callguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dustin.callguard.complaints.ComplaintDownloader
import com.dustin.callguard.complaints.ComplaintIndex

/** downloads the ftc's public unwanted-call reports, used by the probe and suspicion scores. */
@Composable
fun ComplaintPanel(
    index: ComplaintIndex?,
    status: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("public complaint data", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        Text(
            "the ftc publishes the numbers consumers report as unwanted callers each weekday. this button downloads the last ${ComplaintDownloader.RETENTION_DAYS} days of those public files from ftc.gov. only a file named by date is requested: no phone numbers or personal data are sent. reports are unverified, and spoofed numbers are often innocent people's.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = when {
                index == null -> "loading stored data…"
                index.reportCount == 0 -> "no data yet"
                else -> "${index.reportCount} reports across ${index.dayCount} days, newest ${index.newestDay}"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(enabled = !refreshing, onClick = onRefresh) {
            Text(if (refreshing) "downloading…" else "refresh complaint data")
        }
    }
}
