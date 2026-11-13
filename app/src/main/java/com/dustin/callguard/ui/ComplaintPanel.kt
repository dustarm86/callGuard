package com.dustin.callguard.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.dustin.callguard.complaints.ComplaintDownloader
import com.dustin.callguard.complaints.ComplaintIndex

/** downloads the ftc's public unwanted-call reports, used by the probe and suspicion scores. */
@Composable
fun ComplaintPanel(
    index: ComplaintIndex?,
    status: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onImport: (List<Uri>) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { onImport(it) }
    val countLine = when {
        index == null -> "loading stored data…"
        index.reportCount == 0 -> "no data yet"
        else -> "${index.reportCount} reports across ${index.dayCount} days, newest ${index.newestDay}"
    }
    SectionCard(
        title = "public complaint data",
        summary = countLine,
        about = "the ftc publishes the numbers consumers report as unwanted callers each weekday. the most reliable way to load them is to download the daily files from the ftc's do not call data page in chrome and import them here. the refresh button tries to download the last ${ComplaintDownloader.RETENTION_DAYS} days directly, but ftc.gov often refuses apps. no phone numbers or personal data are ever sent. reports are unverified, and spoofed numbers are often innocent people's.",
        collapsible = true,
        initiallyOpen = false,
    ) {
        Text(countLine, style = MaterialTheme.typography.bodyMedium)
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(enabled = !refreshing, onClick = onRefresh) {
            Text(if (refreshing) "working…" else "refresh complaint data")
        }
        OutlinedButton(enabled = !refreshing, onClick = { picker.launch(arrayOf("*/*")) }) {
            Text("import downloaded files")
        }
    }
}
