package com.dustin.callguard.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dustin.callguard.data.AllowedNumber
import com.dustin.callguard.data.AllowedPrefix
import com.dustin.callguard.screening.NumberNormalizer
import com.dustin.callguard.screening.PrefixNormalizer

@Composable
fun AllowlistScreen(
    allowedNumbers: List<AllowedNumber>,
    allowedPrefixes: List<AllowedPrefix>,
    onAdd: (rawNumber: String, label: String) -> Boolean,
    onAddPrefix: (rawPrefix: String, label: String) -> Boolean,
    onRemove: (normalizedNumber: String) -> Unit,
    onRemovePrefix: (prefix: String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddPrefixDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${allowedNumbers.size + allowedPrefixes.size} allowed",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("number", modifier = Modifier.padding(start = 4.dp))
                }
                OutlinedButton(onClick = { showAddPrefixDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("prefix", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        Text(
            text = "numbers here always ring, even if they are not in your contacts. good for the pharmacy, a delivery line, or a doctor's office.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(allowedNumbers, key = { it.normalizedNumber }) { allowedNumber ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = NumberNormalizer.format(allowedNumber.normalizedNumber),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        allowedNumber.expiresAt?.let { expiresAt ->
                            Text(
                                text = "allowed until " + DateUtils.getRelativeTimeSpanString(
                                    expiresAt,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (allowedNumber.label.isNotBlank()) {
                            Text(
                                text = allowedNumber.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onRemove(allowedNumber.normalizedNumber) }) {
                        Icon(Icons.Default.Delete, contentDescription = "remove")
                    }
                }
                HorizontalDivider()
            }
            items(allowedPrefixes, key = { "prefix-" + it.prefix }) { allowedPrefix ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = PrefixNormalizer.format(allowedPrefix.prefix),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (allowedPrefix.label.isNotBlank()) "number block · ${allowedPrefix.label}" else "number block",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onRemovePrefix(allowedPrefix.prefix) }) {
                        Icon(Icons.Default.Delete, contentDescription = "remove")
                    }
                }
                HorizontalDivider()
            }
        }
    }

    if (showAddPrefixDialog) {
        AddPrefixDialog(
            onDismiss = { showAddPrefixDialog = false },
            onConfirm = { rawPrefix, label ->
                val wasAdded = onAddPrefix(rawPrefix, label)
                if (wasAdded) showAddPrefixDialog = false
                wasAdded
            },
        )
    }

    if (showAddDialog) {
        AddNumberDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { rawNumber, label ->
                val wasAdded = onAdd(rawNumber, label)
                if (wasAdded) showAddDialog = false
                wasAdded
            },
        )
    }
}

@Composable
private fun AddNumberDialog(
    onDismiss: () -> Unit,
    onConfirm: (rawNumber: String, label: String) -> Boolean,
) {
    var rawNumber by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("add to allowlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rawNumber,
                    onValueChange = {
                        rawNumber = it
                        hasError = false
                    },
                    label = { Text("phone number") },
                    isError = hasError,
                    supportingText = if (hasError) {
                        { Text("enter a valid number") }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("label (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!onConfirm(rawNumber, label)) hasError = true
            }) { Text("add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("cancel") }
        },
    )
}

@Composable
private fun AddPrefixDialog(
    onDismiss: () -> Unit,
    onConfirm: (rawPrefix: String, label: String) -> Boolean,
) {
    var rawPrefix by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("allow a number block") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "every number that starts with these digits will ring, including spoofed calls that copy the block. use it only for a business you trust.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedTextField(
                    value = rawPrefix,
                    onValueChange = {
                        rawPrefix = it
                        hasError = false
                    },
                    label = { Text("first 8 or 9 digits") },
                    isError = hasError,
                    supportingText = {
                        Text(if (hasError) "enter 8 or 9 digits, like 315 555 01" else "for example 315 555 01 allows (315) 555-0100 to 0199")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("label (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!onConfirm(rawPrefix, label)) hasError = true
            }) { Text("add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("cancel") }
        },
    )
}
