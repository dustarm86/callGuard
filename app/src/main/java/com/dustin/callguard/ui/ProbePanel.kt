package com.dustin.callguard.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.dustin.callguard.dns.EnumLookup
import com.dustin.callguard.lookup.LineLookupClient
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dustin.callguard.complaints.ComplaintIndex
import com.dustin.callguard.data.AllowedNumber
import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.dns.EnumDns
import com.dustin.callguard.screening.NumberNormalizer
import com.dustin.callguard.screening.NumberProbe
import com.dustin.callguard.screening.NxxDirectory
import com.dustin.callguard.screening.ProbeInput
import java.time.ZoneId

/** looks a number up in local and public data only. it never contacts the number. */
@Composable
fun ProbePanel(
    directory: NxxDirectory?,
    calls: List<ScreenedCall>,
    allowedNumbers: List<AllowedNumber>,
    ownNumber: String,
    complaints: ComplaintIndex?,
    contactNumbers: Collection<String>,
    lineLookupKey: String,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val recent = remember(calls) {
        calls.sortedByDescending { it.timestamp }.mapNotNull { it.normalizedNumber }.distinct().take(8)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enumFor by remember { mutableStateOf<String?>(null) }
    var enumLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var enumBusy by remember { mutableStateOf(false) }
    var lineFor by remember { mutableStateOf<String?>(null) }
    var lineLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var lineBusy by remember { mutableStateOf(false) }

    SectionCard(
        title = "probe a number",
        summary = "carrier, complaints and history for one number",
        collapsible = true,
        about = "checks a number against offline carrier data, the ftc's public reports and your own log. it only reads data: it never calls, texts or pings the number, so a spammer can't tell you looked.",
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("number to probe") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        if (recent.isNotEmpty()) {
            Text("or pick from your log", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                recent.forEach { number ->
                    AssistChip(onClick = { text = number }, label = { Text(NumberNormalizer.format(number)) })
                }
            }
        }

        if (text.isNotBlank()) {
            val result = NumberProbe.run(
                ProbeInput(
                    rawText = text,
                    directory = directory,
                    ownNumber = ownNumber,
                    allowedNumbers = allowedNumbers.map { it.normalizedNumber },
                    calls = calls,
                    zone = ZoneId.systemDefault(),
                    complaints = complaints,
                    contactNumbers = contactNumbers,
                )
            )
            result.suspicion?.let { suspicion ->
                SuspicionBadge(suspicion.level, suspicion.score)
                suspicion.reasons.forEach { reason ->
                    Text("· $reason", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    result.sections.forEach { section -> ResultBlock(section.title, section.lines) }
                }
            }

            val probed = NumberNormalizer.normalize(text)
            if (probed != null && EnumDns.name(probed) != null) {
                ResultBlock(
                    title = "enum (public dns)",
                    lines = listOf(
                        "asks public dns whether this number is in enum. only runs when you tap: it sends the digits to your phone's dns resolver, or to cloudflare over https if that fails. it never contacts the number.",
                    ),
                ) {
                    OutlinedButton(
                        enabled = !enumBusy,
                        onClick = {
                            enumBusy = true
                            scope.launch {
                                enumLines = EnumLookup.query(context, probed)
                                enumFor = probed
                                enumBusy = false
                            }
                        },
                    ) { Text(if (enumBusy) "asking dns…" else "look up enum") }
                    if (enumFor == probed) {
                        SelectionContainer {
                            Column {
                                enumLines.forEach { line ->
                                    val verdict = line.startsWith(EnumDns.VERDICT)
                                    Text(
                                        line,
                                        style = if (verdict) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                                        fontWeight = if (verdict) androidx.compose.ui.text.font.FontWeight.Bold else null,
                                        color = if (verdict) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (probed != null && probed.length == 10) {
                ResultBlock(
                    title = "line type (online lookup)",
                    lines = listOf(
                        if (lineLookupKey.isBlank()) {
                            "off: add your own veriphone api key in settings (line type lookup) to turn this on. it asks an online database whether the number is mobile, landline or internet phone, and who the carrier is."
                        } else {
                            "sends this number and your api key to veriphone.io, only when you tap. it never contacts the number. each tap uses one lookup from your monthly quota."
                        },
                    ),
                ) {
                    if (lineLookupKey.isNotBlank()) {
                        OutlinedButton(
                            enabled = !lineBusy,
                            onClick = {
                                lineBusy = true
                                scope.launch {
                                    lineLines = LineLookupClient.query(probed, lineLookupKey)
                                    lineFor = probed
                                    lineBusy = false
                                }
                            },
                        ) { Text(if (lineBusy) "asking…" else "look up line type") }
                        if (lineFor == probed) {
                            SelectionContainer {
                                Column {
                                    lineLines.forEach { line ->
                                        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
