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
    private const val ALLOWED_NEIGHBOR_POINTS = 20
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
