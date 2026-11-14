package com.dustin.callguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Modifier
import com.dustin.callguard.carrierdata.CarrierProgress
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dustin.callguard.carrierdata.CarrierDataInfo
import com.dustin.callguard.carrierdata.CarrierStore
import com.dustin.callguard.carrierdata.SourceCheck
import com.dustin.callguard.screening.NxxDirectory
import kotlinx.coroutines.launch

/** shows how fresh the carrier data is and downloads a newer copy, trying several sources in turn. no phone numbers are sent. */
@Composable
fun CarrierDataPanel(
    directory: NxxDirectory?,
    updateLines: List<String>,
    updating: Boolean,
    progress: CarrierProgress?,
    onUpdate: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checkLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var checking by remember { mutableStateOf(false) }
    // read again whenever a new directory loads, which is what an update produces
    val lastUpdate = remember(directory) { CarrierStore(context).lastUpdate() }

    SectionCard(
        title = "carrier data",
        summary = CarrierDataInfo.summary(directory),
        collapsible = true,
        initiallyOpen = false,
        about = "the carrier and block lookups come from nanpa's public reports. a copy is built into the app, and this downloads a newer one. it tries nanpa's site first, then prebuilt copies on github, and skips any source that fails, is damaged or is older than what you have. the downloaded data is checked before it replaces anything. no phone numbers are sent. the check button asks each source for one byte, so it costs almost no data.",
    ) {
        CarrierLines(CarrierDataInfo.lines(directory, lastUpdate), verdictFirst = false)
        Button(enabled = !updating, onClick = onUpdate) { Text(if (updating) "updating…" else "update carrier data") }
        if (updating) {
            // a bar that fills as bytes arrive, or just shows activity while the size isn't known yet
            val fraction = progress?.fraction
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (progress != null) CarrierLines(progress.lines(), verdictFirst = false)
        }
        if (updateLines.isNotEmpty()) CarrierLines(updateLines, verdictFirst = true)
        OutlinedButton(
            enabled = !checking && !updating,
            onClick = {
                checking = true
                scope.launch {
                    checkLines = SourceCheck.run()
                    checking = false
                }
            },
        ) { Text(if (checking) "checking…" else "check data sources") }
        if (checkLines.isNotEmpty()) CarrierLines(checkLines, verdictFirst = true)
    }
}

@Composable
private fun CarrierLines(lines: List<String>, verdictFirst: Boolean) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEachIndexed { index, line ->
                val verdict = verdictFirst && index == 0
                Text(
                    line,
                    style = if (verdict) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    fontWeight = if (verdict) FontWeight.Bold else null,
                    color = if (verdict) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
