package com.dustin.callguard.trends

import com.dustin.callguard.data.NumberInfo
import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.data.VerificationStatus
import com.dustin.callguard.screening.AreaCodes
import com.dustin.callguard.screening.NumberNormalizer
import com.dustin.callguard.screening.ScreeningOutcome
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** builds a readable, self-contained html page of the call log, newest first. */
object ReportBuilder {
    private val rowTime = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a", Locale.US)
    private val headerTime = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US)

    fun build(
        calls: List<ScreenedCall>,
        numberInfo: Map<String, NumberInfo>,
        zone: ZoneId,
        now: ZonedDateTime,
    ): String {
        val rows = calls.sortedByDescending { it.timestamp }.joinToString("\n") { call ->
            val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(call.timestamp), zone)
            val number = call.normalizedNumber
            val info = number?.let { numberInfo[it] }
            val outcome = runCatching { ScreeningOutcome.valueOf(call.outcome) }.getOrNull()?.label ?: call.outcome
            val verification = when (call.verificationStatus) {
                VerificationStatus.PASSED -> "passed"
                VerificationStatus.FAILED -> "failed"
                else -> "—"
            }
            val tag = when (info?.tag) {
                "SPAM" -> "spam"
                "KNOWN" -> "known"
                else -> ""
            }
            val notes = listOf(tag, info?.note.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
            val timing = listOfNotNull(
                call.decisionMs?.let { "decided in $it ms" },
                call.receivedDelayMs?.let { "reached us $it ms after android" },
                if (call.coldStart) "cold start" else null,
            ).joinToString(" · ")
            "<tr><td>${esc(time.format(rowTime))}</td>" +
                "<td class=\"num\">${esc(number?.let(NumberNormalizer::format) ?: "hidden number")}</td>" +
                "<td>${esc(AreaCodes.lookup(number) ?: "—")}</td>" +
                "<td>${esc(outcome)}</td>" +
                "<td>${esc(verification)}</td>" +
                "<td>${esc(timing)}</td>" +
                "<td>${esc(notes)}</td></tr>"
        }

        return """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>callGuard log</title>
<style>
  :root { color-scheme: light dark; }
  body { font-family: system-ui, sans-serif; margin: 16px; line-height: 1.4; }
  h1 { font-size: 1.3rem; margin: 0 0 4px; }
  p { margin: 0 0 16px; opacity: .7; }
  .wrap { overflow-x: auto; }
  table { border-collapse: collapse; width: 100%; min-width: 640px; }
  th, td { text-align: left; padding: 8px 12px; border-bottom: 1px solid rgba(128,128,128,.3); vertical-align: top; }
  th { position: sticky; top: 0; background: Canvas; font-size: .8rem; text-transform: lowercase; opacity: .8; }
  tbody tr:nth-child(even) { background: rgba(128,128,128,.08); }
  .num { white-space: nowrap; font-variant-numeric: tabular-nums; }
</style>
</head>
<body>
<h1>callGuard log</h1>
<p>${calls.size} calls · generated ${esc(now.format(headerTime))}</p>
<div class="wrap">
<table>
<thead><tr><th>when</th><th>number</th><th>where</th><th>result</th><th>verification</th><th>timing</th><th>notes</th></tr></thead>
<tbody>
$rows
</tbody>
</table>
</div>
</body>
</html>
"""
    }

    private fun esc(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
