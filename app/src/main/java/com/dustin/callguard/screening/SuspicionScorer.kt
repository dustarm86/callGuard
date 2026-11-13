package com.dustin.callguard.screening

import com.dustin.callguard.data.NumberTag

enum class SuspicionLevel(val label: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
}

data class SuspicionResult(
    val score: Int,
    val level: SuspicionLevel,
    val reasons: List<String>,
)

data class SuspicionInput(
    val normalizedNumber: String,
    // calls from this number in the log
    val callCount: Int,
    // true if any logged call from this number failed stir/shaken verification
    val verificationFailed: Boolean,
    val ownNumber: String?,
    val allowedNumbers: Collection<String>,
    // empty, or a NumberTag
    val tag: String?,
    // reasons from NanpValidator; empty when the number looks valid
    val nanpProblems: List<String> = emptyList(),
    // what the nanpa carrier data says about the number's exchange; unknown adds nothing
    val blockStatus: BlockStatus = BlockStatus.UNKNOWN,
    // how many consumer reports of this number are in the downloaded ftc data
    val complaintCount: Int = 0,
    // other numbers in this number's block (first 6 digits) that are in the downloaded ftc data
    val blockReportedNumbers: Int = 0,
    // the country when the area code can bill like an international call (one-ring scams), else null
    val callbackRiskPlace: String? = null,
    // contacts that share this number's first 6 digits
    val contactNeighbors: Int = 0,
)

/**
 * pure, transparent heuristic: every point comes from a listed reason. it is a hint for the
 * user, not a verdict, and it never changes whether a call rings.
 */
object SuspicionScorer {
    private const val BASE_SCORE = 10
    private const val FAILED_VERIFICATION_POINTS = 50
    private const val OWN_NUMBER_POINTS = 45
    private const val NEIGHBOR_POINTS = 40
    private const val INVALID_NUMBER_POINTS = 45
    private const val SAME_AREA_CODE_POINTS = 5
    private const val UNASSIGNED_BLOCK_POINTS = 45
    private const val UNAVAILABLE_BLOCK_POINTS = 30
    private const val NOT_IN_SERVICE_POINTS = 25
    private const val COMPLAINT_POINTS = 20
    private const val MANY_COMPLAINTS_POINTS = 35
    private const val MANY_COMPLAINTS_AT = 3
    private const val ALLOWED_NEIGHBOR_POINTS = 20
    private const val CONTACT_NEIGHBOR_POINTS = 10
    private const val CALLBACK_RISK_POINTS = 15
    private const val BLOCK_REPORTED_POINTS = 10
    private const val BLOCK_REPORTED_AT = 3
    private const val MANY_BLOCK_REPORTED_POINTS = 20
    private const val MANY_BLOCK_REPORTED_AT = 10
    private const val REPEAT_POINTS = 8
    private const val MANY_REPEATS_POINTS = 15
    private const val MANY_REPEATS_AT = 3
    private const val HIGH_AT = 55
    private const val MEDIUM_AT = 30

    /** true when both are 10-digit numbers with the same area code and exchange (first 6 digits). */
    fun sharesPrefix(first: String?, second: String?): Boolean =
        first != null && second != null &&
            first.length == 10 && second.length == 10 &&
            first.substring(0, 6) == second.substring(0, 6)

    /** how many of [numbers] share the first 6 digits of [number], not counting [number] itself. */
    fun neighborCount(number: String, numbers: Collection<String>): Int =
        numbers.count { it != number && sharesPrefix(number, it) }

    fun score(input: SuspicionInput): SuspicionResult {
        when (input.tag) {
            NumberTag.KNOWN -> return SuspicionResult(0, SuspicionLevel.LOW, listOf("you tagged this number as known"))
            NumberTag.SPAM -> return SuspicionResult(100, SuspicionLevel.HIGH, listOf("you tagged this number as spam"))
        }

        var score = BASE_SCORE
        val reasons = mutableListOf<String>()
        val number = input.normalizedNumber
        val ownNumber = NumberNormalizer.normalize(input.ownNumber)

        if (input.verificationFailed) {
            score += FAILED_VERIFICATION_POINTS
            reasons += "failed caller verification, so the number is probably faked"
        }

        if (input.nanpProblems.isNotEmpty()) {
            score += INVALID_NUMBER_POINTS
            reasons += "not a real number: ${input.nanpProblems.first()}"
        }

        // an impossible number is already counted above, so the carrier data only adds to numbers that look valid
        if (input.nanpProblems.isEmpty()) {
            val code = "${number.take(3)}-${number.drop(3).take(3)}"
            when (input.blockStatus) {
                BlockStatus.UNASSIGNED -> {
                    score += UNASSIGNED_BLOCK_POINTS
                    reasons += "$code is not assigned to any carrier, so a real line shouldn't have this number"
                }
                BlockStatus.UNAVAILABLE -> {
                    score += UNAVAILABLE_BLOCK_POINTS
                    reasons += "$code is reserved for special use, not ordinary lines"
                }
                BlockStatus.NOT_IN_SERVICE -> {
                    score += NOT_IN_SERVICE_POINTS
                    reasons += "$code is assigned to a carrier but not in service yet"
                }
                else -> Unit
            }
        }

        if (input.complaintCount > 0) {
            if (input.complaintCount >= MANY_COMPLAINTS_AT) {
                score += MANY_COMPLAINTS_POINTS
                reasons += "reported as an unwanted caller ${input.complaintCount} times in recent ftc data"
            } else {
                score += COMPLAINT_POINTS
                reasons += "reported as an unwanted caller ${input.complaintCount} ${if (input.complaintCount == 1) "time" else "times"} in recent ftc data"
            }
        }

        if (input.blockReportedNumbers >= BLOCK_REPORTED_AT) {
            val many = input.blockReportedNumbers >= MANY_BLOCK_REPORTED_AT
            score += if (many) MANY_BLOCK_REPORTED_POINTS else BLOCK_REPORTED_POINTS
            reasons += "${input.blockReportedNumbers} other numbers in the same block are in recent ftc data, which is how a spam campaign looks"
        }

        input.callbackRiskPlace?.let { place ->
            score += CALLBACK_RISK_POINTS
            reasons += "area code belongs to $place, where callback scams bill like an international call"
        }

        if (ownNumber != null) {
            when {
                number == ownNumber -> {
                    score += OWN_NUMBER_POINTS
                    reasons += "calling from your own number"
                }
                sharesPrefix(number, ownNumber) -> {
                    score += NEIGHBOR_POINTS
                    reasons += "first 6 digits match your own number (neighbor spoofing)"
                }
                AreaCodes.areaCodeOf(number) != null &&
                    AreaCodes.areaCodeOf(number) == AreaCodes.areaCodeOf(ownNumber) -> {
                    score += SAME_AREA_CODE_POINTS
                    reasons += "same area code as your number"
                }
            }
        }

        if (input.allowedNumbers.any { it != number && sharesPrefix(number, it) }) {
            score += ALLOWED_NEIGHBOR_POINTS
            reasons += "first 6 digits match a number you allow"
        }

        if (input.contactNeighbors > 0) {
            score += CONTACT_NEIGHBOR_POINTS
            reasons += "first 6 digits match ${input.contactNeighbors} of your contacts (common in a small town, but it is also how neighbor spoofing works)"
        }

        when {
            input.callCount >= MANY_REPEATS_AT -> {
                score += MANY_REPEATS_POINTS
                reasons += "called ${input.callCount} times"
            }
            input.callCount == 2 -> {
                score += REPEAT_POINTS
                reasons += "called twice"
            }
        }

        val clamped = score.coerceIn(0, 100)
        val level = when {
            clamped >= HIGH_AT -> SuspicionLevel.HIGH
            clamped >= MEDIUM_AT -> SuspicionLevel.MEDIUM
            else -> SuspicionLevel.LOW
        }
        return SuspicionResult(clamped, level, reasons)
    }
}
