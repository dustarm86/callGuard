package com.dustin.callguard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dustin.callguard.data.NumberInfo
import com.dustin.callguard.data.NumberTag
import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.screening.AreaCodes
import com.dustin.callguard.screening.NumberNormalizer
import com.dustin.callguard.screening.ScreeningOutcome
import java.text.DateFormat
import java.util.Date

/** short lowercase label for a tag, or null when there isn't one. */
internal fun tagLabel(tag: String?): String? = when (tag) {
    NumberTag.SPAM -> "spam"
    NumberTag.KNOWN -> "known"
    else -> null
}

@Composable
fun CallDetailScreen(
    normalizedNumber: String,
    calls: List<ScreenedCall>,
    info: NumberInfo?,
    isAllowed: Boolean,
    onSave: (note: String, tag: String) -> Unit,
    onToggleAllow: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var note by rememberSaveable(normalizedNumber, info?.updatedAt) { mutableStateOf(info?.note.orEmpty()) }
    var tag by rememberSaveable(normalizedNumber, info?.updatedAt) { mutableStateOf(info?.tag.orEmpty()) }

    val callsFromNumber = calls
        .filter { it.normalizedNumber == normalizedNumber }
        .sortedByDescending { it.timestamp }
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("back to log") }

        Column {
            Text(
                text = NumberNormalizer.format(normalizedNumber),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = AreaCodes.lookup(normalizedNumber)
                    ?: "area code ${AreaCodes.areaCodeOf(normalizedNumber) ?: "?"} isn't in the offline table yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (callsFromNumber.isNotEmpty()) {
            val first = callsFromNumber.last().timestamp
            val last = callsFromNumber.first().timestamp
            Text(
                text = "${callsFromNumber.size} call${if (callsFromNumber.size == 1) "" else "s"} in the log · " +
                    "first ${dateFormat.format(Date(first))} · last ${dateFormat.format(Date(last))}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tag == NumberTag.SPAM,
                onClick = { tag = if (tag == NumberTag.SPAM) "" else NumberTag.SPAM },
                label = { Text("spam") },
            )
            FilterChip(
                selected = tag == NumberTag.KNOWN,
                onClick = { tag = if (tag == NumberTag.KNOWN) "" else NumberTag.KNOWN },
                label = { Text("known") },
            )
        }
        Text(
            text = "tags are just labels for your own reference. they don't change whether a call rings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("note") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(note, tag) }) { Text("save") }
            OutlinedButton(onClick = onToggleAllow) {
                Text(if (isAllowed) "remove from allowlist" else "always allow this number")
            }
        }

        HorizontalDivider()
        Text("calls", style = MaterialTheme.typography.titleMedium)
        callsFromNumber.take(50).forEach { call ->
            val outcome = runCatching { ScreeningOutcome.valueOf(call.outcome) }.getOrNull()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(dateFormat.format(Date(call.timestamp)), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = outcome?.label ?: call.outcome,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
