package com.dustin.callguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dustin.callguard.diagnostics.DiagnosticsCapture
import kotlinx.coroutines.launch

/** captures the system's call records into a text file and opens the share sheet. */
@Composable
fun DiagnosticsPanel(header: List<String>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var capturing by remember { mutableStateOf(false) }
    // bumped after each capture so the permission status below is checked again
    var refresh by remember { mutableStateOf(0) }

    val hasDump = refresh >= 0 && DiagnosticsCapture.hasPermission(context, DiagnosticsCapture.PERMISSION_DUMP)
    val hasLogs = refresh >= 0 && DiagnosticsCapture.hasPermission(context, DiagnosticsCapture.PERMISSION_READ_LOGS)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("capture diagnostics", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        Text(
            "saves android's own record of recent calls (the telecom dump and call-related log lines) plus whether android is limiting callGuard in the background, then opens the share sheet. do it soon after a call comes in, because the system keeps these only for a while.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "dump permission: ${if (hasDump) "granted" else "not granted"} · log permission: ${if (hasLogs) "granted" else "not granted"}",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!hasDump || !hasLogs) {
            Text(
                "android only lets you grant these over adb, one time. with the phone connected to your mac, run these (the grant survives app updates but not an uninstall):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                Text(
                    DiagnosticsCapture.grantCommands(context.packageName).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Button(
            enabled = !capturing,
            onClick = {
                capturing = true
                scope.launch {
                    val text = DiagnosticsCapture.capture(context, header)
                    ExportShare.shareDiagnostics(context, text)
                    capturing = false
                    refresh++
                }
            },
        ) { Text(if (capturing) "capturing…" else "capture and share") }
    }
}
