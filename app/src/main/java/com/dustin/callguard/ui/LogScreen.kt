package com.dustin.callguard.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dustin.callguard.data.NumberInfo
import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.screening.AreaCodes
import com.dustin.callguard.screening.NumberNormalizer
import com.dustin.callguard.screening.ScreeningOutcome
import com.dustin.callguard.screening.SuspicionLevel
import com.dustin.callguard.screening.SuspicionResult

@Composable
fun LogScreen(
    calls: List<ScreenedCall>,
    allowedNormalizedNumbers: Set<String>,
    numberInfo: Map<String, NumberInfo>,
    suspicionFor: (String) -> SuspicionResult,
    onOpen: (ScreenedCall) -> Unit,
    onAllow: (ScreenedCall) -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${calls.size} screened",
                style = MaterialTheme.typography.titleMedium,
            )
            if (calls.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("clear log") }
            }
        }

        if (calls.isEmpty()) {
            Text(
                text = "nothing screened yet. silenced calls will show up here so you can allow real callers with one tap.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(calls, key = { it.id }) { call ->
                val outcome = runCatching { ScreeningOutcome.valueOf(call.outcome) }.getOrNull()
                val isSilenced = outcome?.allowsRing == false
                val canAllow = call.normalizedNumber != null &&
                    call.normalizedNumber !in allowedNormalizedNumbers

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = call.normalizedNumber != null) { onOpen(call) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = call.normalizedNumber?.let(NumberNormalizer::format) ?: "hidden number",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val whenText = DateUtils.getRelativeTimeSpanString(
                            call.timestamp,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                        )
                        Text(
                            text = "$whenText · ${outcome?.label ?: call.outcome}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSilenced) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                        val extraLine = listOfNotNull(
                            AreaCodes.lookup(call.normalizedNumber),
                            tagLabel(call.normalizedNumber?.let { numberInfo[it]?.tag }),
                            call.normalizedNumber?.let { suspicionFor(it) }
                                ?.takeIf { it.level != SuspicionLevel.LOW }
                                ?.let { "suspicion ${it.level.label}" },
                        ).joinToString(" · ")
                        if (extraLine.isNotEmpty()) {
                            Text(
                                text = extraLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (canAllow) {
                        TextButton(onClick = { onAllow(call) }) { Text("allow") }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
