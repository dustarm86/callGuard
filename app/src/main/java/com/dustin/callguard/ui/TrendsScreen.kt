package com.dustin.callguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dustin.callguard.screening.NumberNormalizer
import com.dustin.callguard.trends.Trends

@Composable
fun TrendsScreen(
    trends: Trends,
    hasOwnNumber: Boolean,
    onExportCsv: () -> Unit,
    onExportReport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("last 30 days", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExportReport, enabled = trends.silencedCount > 0) { Text("export report") }
            OutlinedButton(onClick = onExportCsv, enabled = trends.silencedCount > 0) { Text("export csv") }
        }

        if (trends.silencedCount == 0) {
            Text(
                "nothing silenced yet. trends show up here once calls are screened.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(
            "${trends.silencedCount} calls silenced from ${trends.distinctNumbers} different numbers",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (trends.hiddenCount > 0) StatLine("hidden numbers", trends.hiddenCount)
        if (trends.failedVerificationCount > 0) StatLine("failed caller verification", trends.failedVerificationCount)
        if (hasOwnNumber) {
            StatLine("neighbor spoofs (first 6 digits match yours)", trends.neighborSpoofCount)
        } else {
            Text(
                "add your number in settings to count neighbor spoofs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionTitle("top area codes")
        val maxAreaCount = trends.topAreaCodes.maxOfOrNull { it.count } ?: 1
        trends.topAreaCodes.forEach { entry ->
            Column {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = entry.areaCode + (entry.region?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${entry.count}", style = MaterialTheme.typography.bodyMedium)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(entry.count / maxAreaCount.toFloat())
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                )
            }
        }

        SectionTitle("calls by hour")
        BarChart(trends.callsByHour)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("12a", "6a", "12p", "6p", "11p").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
        }

        SectionTitle("calls by weekday")
        BarChart(trends.callsByWeekday)
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun").forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (trends.topRepeatNumbers.isNotEmpty()) {
            SectionTitle("repeat callers")
            trends.topRepeatNumbers.forEach { entry ->
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(NumberNormalizer.format(entry.number), style = MaterialTheme.typography.bodyMedium)
                    Text("${entry.count} calls", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Text(
            "the report is a readable web page; the csv is for sheets or python. both include phone numbers, so pick where they go.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun StatLine(label: String, value: Int) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("$value", style = MaterialTheme.typography.bodyMedium)
    }
}

/** equal-width bars scaled to the largest value; empty slots keep a thin baseline tick. */
@Composable
private fun BarChart(values: List<Int>) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { value ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight((value / max.toFloat()).coerceIn(0.02f, 1f))
                    .background(
                        if (value == 0) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                    ),
            )
        }
    }
}
