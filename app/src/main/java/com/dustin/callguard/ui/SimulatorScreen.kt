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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dustin.callguard.data.AllowedNumber
import com.dustin.callguard.data.AllowedPrefix
import com.dustin.callguard.data.ScreenerSettings
import com.dustin.callguard.screening.AllowlistMatcher
import com.dustin.callguard.screening.AreaCodes
import com.dustin.callguard.screening.NanpValidator
import com.dustin.callguard.screening.NumberNormalizer
import com.dustin.callguard.screening.ScreeningEngine
import com.dustin.callguard.screening.ScreeningInput
import com.dustin.callguard.screening.SuspicionInput
import com.dustin.callguard.screening.SuspicionScorer
import java.time.LocalDateTime

/**
 * runs a made-up call through the same decision code the real screener uses, with your current
 * settings, allowlist, and prefixes. it can't tell you whether android honors a silence request.
 */
@Composable
fun SimulatorScreen(
    settings: ScreenerSettings,
    allowedNumbers: List<AllowedNumber>,
    allowedPrefixes: List<AllowedPrefix>,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var numberText by rememberSaveable { mutableStateOf("") }
    var isContact by rememberSaveable { mutableStateOf(false) }
    var verificationFailed by rememberSaveable { mutableStateOf(false) }
    var calledAgain by rememberSaveable { mutableStateOf(false) }

    val normalized = NumberNormalizer.normalize(numberText)
    val nowMillis = System.currentTimeMillis()
    val allowlisted = AllowlistMatcher.matches(normalized, allowedNumbers, allowedPrefixes, nowMillis)
    val outcome = ScreeningEngine.decide(
        ScreeningInput(
            normalizedNumber = normalized,
            isContact = isContact,
            isAllowlisted = allowlisted,
            hasRecentSilencedCall = calledAgain,
            verificationFailed = verificationFailed,
            settings = settings,
            now = LocalDateTime.now(),
        )
    )
    val suspicion = normalized?.let { number ->
        SuspicionScorer.score(
            SuspicionInput(
                normalizedNumber = number,
                callCount = if (calledAgain) 2 else 1,
                verificationFailed = verificationFailed,
                ownNumber = settings.ownNumber,
                allowedNumbers = allowedNumbers.map { it.normalizedNumber },
                tag = null,
                nanpProblems = NanpValidator.problems(numberText, number),
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("back to settings") }
        Text("simulate a call", style = MaterialTheme.typography.titleLarge)
        Text(
            "runs a made-up call through callGuard's real decision code using your current settings, allowlist and prefixes. leave the number empty to simulate a hidden number. it can't show whether android actually silences the ring.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = numberText,
            onValueChange = { numberText = it },
            label = { Text("caller number") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )

        SimulatorSwitch("number is in my contacts", isContact) { isContact = it }
        SimulatorSwitch("failed caller verification", verificationFailed) { verificationFailed = it }
        SimulatorSwitch("same number called again just now", calledAgain) { calledAgain = it }

        val rings = outcome.allowsRing
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (rings) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = when {
                        rings -> "this call would ring"
                        outcome.rejectsCall -> "this call would be rejected"
                        else -> "this call would be silenced"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(outcome.label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = normalized?.let { NumberNormalizer.format(it) } ?: "hidden number",
                    style = MaterialTheme.typography.bodySmall,
                )
                AreaCodes.lookup(normalized)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (allowlisted) {
                    Text("matched your allowlist or a number block", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (suspicion != null) {
            Column {
                Text(
                    text = "suspicion: ${suspicion.level.label} (${suspicion.score}/100)",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (suspicion.reasons.isEmpty()) {
                    Text(
                        "no warning signs from the number itself",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                suspicion.reasons.forEach { reason ->
                    Text(
                        "· $reason",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SimulatorSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
