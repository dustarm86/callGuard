package com.dustin.callguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dustin.callguard.screening.SuspicionLevel

// shared building blocks, so every screen breaks its text up the same way.

/**
 * a shaded, rounded card with a green title. long explanations go in [about] and stay hidden until
 * the person taps "what is this?". a [collapsible] card folds down to its title and optional [summary].
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    about: String? = null,
    collapsible: Boolean = false,
    initiallyOpen: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(key = "section-open-$title") { mutableStateOf(initiallyOpen) }
    var aboutOpen by rememberSaveable(key = "section-about-$title") { mutableStateOf(false) }
    val showBody = !collapsible || open

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (collapsible) Modifier.clickable { open = !open } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    if (!showBody && summary != null) {
                        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (collapsible) {
                    Text(
                        if (open) "[-]" else "[+]",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            if (showBody) {
                if (about != null) {
                    Text(
                        if (aboutOpen) "hide details" else "what is this?",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .clickable { aboutOpen = !aboutOpen }
                            .padding(vertical = 4.dp),
                    )
                    if (aboutOpen) {
                        Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                content()
            }
        }
    }
}

/** one labelled group of result lines, marked by a blue bar down its left edge. */
@Composable
fun ResultBlock(
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(2.dp)),
        )
        Column(modifier = Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

// the palette has no amber, so the middle level gets its own
private val AmberContainer = Color(0xFF4A3A12)
private val AmberContent = Color(0xFFFFD27A)

/** the suspicion level as a colored pill: green for low, amber for medium, red for high. */
@Composable
fun SuspicionBadge(level: SuspicionLevel, score: Int, modifier: Modifier = Modifier) {
    val (container, content) = when (level) {
        SuspicionLevel.LOW -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        SuspicionLevel.MEDIUM -> AmberContainer to AmberContent
        SuspicionLevel.HIGH -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = container) {
        Text(
            "suspicion: ${level.label} ($score/100)",
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** a bottom bar label that stays on one line, so a long word like "allowlist" never wraps. */
@Composable
fun NavLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}
