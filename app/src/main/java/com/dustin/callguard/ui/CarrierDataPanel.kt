package com.dustin.callguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dustin.callguard.carrierdata.SourceCheck
import kotlinx.coroutines.launch

/** checks where this phone can download the nanpa carrier data from. nothing about any number is sent. */
@Composable
fun CarrierDataPanel() {
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    SectionCard(
        title = "carrier data sources",
        summary = "can this phone download the nanpa data itself?",
        collapsible = true,
        initiallyOpen = false,
        about = "the carrier and block data is built into the app. this checks which download sources this phone can actually reach, so the app can refresh the data on its own. it asks each address for one byte, so it uses almost no data, and it sends no phone numbers.",
    ) {
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    lines = SourceCheck.run()
                    busy = false
                }
            },
        ) { Text(if (busy) "checking…" else "check data sources") }
        if (lines.isNotEmpty()) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lines.forEachIndexed { index, line ->
                        Text(
                            line,
                            style = if (index == 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                            fontWeight = if (index == 0) FontWeight.Bold else null,
                            color = if (index == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
