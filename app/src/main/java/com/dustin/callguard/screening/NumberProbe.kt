package com.dustin.callguard.screening

import com.dustin.callguard.complaints.ComplaintIndex
import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.data.VerificationStatus
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ProbeSection(val title: String, val lines: List<String>)

data class ProbeResult(val sections: List<ProbeSection>, val suspicion: SuspicionResult?)

data class ProbeInput(
    val rawText: String,
    val directory: NxxDirectory?,
    val ownNumber: String?,
    val allowedNumbers: List<String>,
    val calls: List<ScreenedCall>,
    val zone: ZoneId,
    val complaints: ComplaintIndex? = null,
    // normalized numbers of the person's contacts, for the neighbor check
    val contactNumbers: Collection<String> = emptyList(),
)

/**
 * looks a number up in public and local data only. it never calls, texts or contacts the number,
 * so probing cannot tell a caller that the line is live.
 */
object NumberProbe {
    private val seenFormat = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)

    fun run(input: ProbeInput): ProbeResult {
        val number = NumberNormalizer.normalize(input.rawText)
        if (number == null) {
            return ProbeResult(listOf(ProbeSection("number", listOf("nothing to probe: type a number or pick one from your log"))), null)
        }

        val sections = mutableListOf<ProbeSection>()

        val numberLines = mutableListOf(NumberNormalizer.format(number))
        numberLines += AreaCodes.lookup(number) ?: "area code is not in the offline table"
        if (AreaCodes.isTollFree(number)) numberLines += "toll-free number"
        val callbackPlace = AreaCodes.callbackRisk(number)
        if (callbackPlace != null) {
            numberLines += "area code belongs to $callbackPlace: calling back bills like an international call, which is how one-ring scams make money"
        }
        sections += ProbeSection("number", numberLines)

        val problems = NanpValidator.problems(input.rawText, number)
        sections += ProbeSection(
            "structure",
            if (problems.isEmpty()) listOf("no structural problems found") else problems,
        )

        val block = input.directory?.lookup(number)
        sections += ProbeSection("carrier block", blockLines(number, input.directory, block))

        val reported = input.complaints?.lookup(number)
        val blockReported = input.complaints?.blockLookup(number)
        sections += ProbeSection("public complaints", complaintLines(input.complaints, reported) + blockComplaintLines(number, blockReported))

        val matching = input.calls.filter { it.normalizedNumber == number }
        sections += ProbeSection("your history", historyLines(number, matching, input.calls, input.zone))

        val contactNeighbors = SuspicionScorer.neighborCount(number, input.contactNumbers)
        val relation = relationLines(number, input.ownNumber, input.allowedNumbers, contactNeighbors)
        if (number.length == 10) sections += ProbeSection("phone number library", PhoneLibCheck.lines(number))
        if (relation.isNotEmpty()) sections += ProbeSection("relationship to you", relation)

        val suspicion = SuspicionScorer.score(
            SuspicionInput(
                normalizedNumber = number,
                callCount = matching.size,
                verificationFailed = matching.any { it.verificationStatus == VerificationStatus.FAILED },
                ownNumber = input.ownNumber,
                allowedNumbers = input.allowedNumbers,
                tag = null,
                nanpProblems = problems,
                blockStatus = block?.status ?: BlockStatus.UNKNOWN,
                complaintCount = reported?.count ?: 0,
                blockReportedNumbers = blockReported?.otherNumbers ?: 0,
                callbackRiskPlace = callbackPlace,
                contactNeighbors = contactNeighbors,
            )
        )
        return ProbeResult(sections, suspicion)
    }

    private fun blockLines(number: String, directory: NxxDirectory?, block: BlockInfo?): List<String> {
        if (directory == null || block == null) {
            return listOf("carrier data isn't in this install (run tools/buildNanpaData.py, then rebuild the app)")
        }
        // short input (someone still typing) has no exchange yet, so there is no code to show
        val code = if (number.length >= 6) "${number.take(3)}-${number.substring(3, 6)}" else number
        val lines = mutableListOf<String>()
        when (block.status) {
            BlockStatus.UNKNOWN -> lines += "no data for this area code: the nanpa file covers the us only, so canadian, caribbean and toll-free numbers aren't in it"
            BlockStatus.UNASSIGNED -> lines += "$code is not assigned to any carrier in the nanpa file, so a real phone line shouldn't have this number"
            BlockStatus.UNAVAILABLE -> lines += "$code is reserved for special use (directory assistance, test or service codes), not ordinary lines"
            BlockStatus.NOT_IN_SERVICE, BlockStatus.ASSIGNED -> {
                lines += "carrier: ${block.company ?: "unknown"}${block.ocn?.let { " (OCN $it)" }.orEmpty()}"
                CarrierKinds.classify(block.company)?.let { lines += "looks like: ${it.label} (a guess from the company name)" }
                val place = listOfNotNull(block.rateCenter, block.state).joinToString(", ")
                if (place.isNotEmpty()) lines += "rate center: $place"
                block.assigned?.let { lines += "code assigned: $it" }
                if (block.status == BlockStatus.NOT_IN_SERVICE) lines += "assigned but not in service yet"
                if (block.pooled) {
                    lines += "pooled code: this carrier originally held the whole $code, but individual 1,000-number blocks may belong to other carriers"
                }
            }
        }
        val d = directory.asOf
        lines += "nanpa data as of %04d-%02d-%02d".format(d / 10000, d / 100 % 100, d % 100)
        return lines
    }

    private fun complaintLines(index: ComplaintIndex?, stats: com.dustin.callguard.complaints.ComplaintStats?): List<String> {
        if (index == null || index.reportCount == 0) {
            return listOf("no complaint data yet: tap \"refresh complaint data\" in the tools tab to download the ftc's recent reports")
        }
        if (stats == null) {
            return listOf("no reports of this number in the last ${index.dayCount} days of ftc data (newest ${index.newestDay})")
        }
        val lines = mutableListOf(
            "${stats.count} ${if (stats.count == 1) "report" else "reports"} in the ftc's recent data, latest ${stats.lastDay}",
        )
        if (stats.robocalls > 0) lines += "${stats.robocalls} marked as recorded messages or robocalls"
        lines += "reported as: " + stats.subjects.joinToString("; ") { "${it.first} (${it.second})" }
        lines += "these are unverified consumer reports, and a spoofed number can belong to an innocent person"
        return lines
    }

    private fun blockComplaintLines(number: String, stats: com.dustin.callguard.complaints.BlockComplaintStats?): List<String> {
        if (stats == null || number.length < 6) return emptyList()
        val code = "${number.take(3)}-${number.substring(3, 6)}"
        val robo = if (stats.robocalls > 0) ", ${stats.robocalls} marked as robocalls" else ""
        return listOf(
            "${stats.otherNumbers} other ${if (stats.otherNumbers == 1) "number" else "numbers"} in the same block ($code) " +
                "${if (stats.otherNumbers == 1) "was" else "were"} reported (${stats.reports} reports$robo)",
            "spam campaigns often rotate through one block, so reports of neighbors can matter even when this number has none",
        )
    }

    private fun historyLines(
        number: String,
        matching: List<ScreenedCall>,
        allCalls: List<ScreenedCall>,
        zone: ZoneId,
    ): List<String> {
        val lines = mutableListOf<String>()
        if (matching.isEmpty()) {
            lines += "never seen in your log"
        } else {
            val sorted = matching.sortedBy { it.timestamp }
            fun at(call: ScreenedCall) = ZonedDateTime.ofInstant(Instant.ofEpochMilli(call.timestamp), zone).format(seenFormat)
            lines += "${matching.size} ${if (matching.size == 1) "call" else "calls"}, first ${at(sorted.first())}, last ${at(sorted.last())}"
            val outcomes = matching.groupingBy { call ->
                runCatching { ScreeningOutcome.valueOf(call.outcome) }.getOrNull()?.label ?: call.outcome
            }.eachCount()
            lines += outcomes.entries.joinToString(", ") { "${it.value} x ${it.key}" }
            val failed = matching.count { it.verificationStatus == VerificationStatus.FAILED }
            if (failed > 0) lines += "$failed failed caller verification"
        }
        val prefix = number.take(6)
        val others = allCalls.mapNotNull { it.normalizedNumber }
            .filter { it != number && it.length == 10 && it.startsWith(prefix) }
            .distinct()
        if (others.isNotEmpty()) {
            lines += "${others.size} other ${if (others.size == 1) "number" else "numbers"} from the same block in your log: ${others.joinToString(", ") { NumberNormalizer.format(it) }}"
        }
        return lines
    }

    private fun relationLines(number: String, ownNumber: String?, allowedNumbers: List<String>, contactNeighbors: Int): List<String> {
        val lines = mutableListOf<String>()
        val own = NumberNormalizer.normalize(ownNumber)
        if (own != null) {
            when {
                number == own -> lines += "this is your own number"
                SuspicionScorer.sharesPrefix(number, own) -> lines += "first 6 digits match your own number (neighbor spoofing pattern)"
                AreaCodes.areaCodeOf(number) != null && AreaCodes.areaCodeOf(number) == AreaCodes.areaCodeOf(own) ->
                    lines += "same area code as your number"
            }
        }
        val neighbors = allowedNumbers.filter { it != number && SuspicionScorer.sharesPrefix(number, it) }
        if (neighbors.isNotEmpty()) lines += "first 6 digits match ${neighbors.size} number(s) you allow"
        if (contactNeighbors > 0) {
            lines += "first 6 digits match $contactNeighbors of your contacts: neighbor spoofing copies the start of numbers you know, but it is also normal in a small town"
        }
        return lines
    }
}
