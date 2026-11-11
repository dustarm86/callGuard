package com.dustin.callguard.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "allowed_numbers")
data class AllowedNumber(
    @PrimaryKey val normalizedNumber: String,
    val label: String,
    val addedAt: Long,
)

@Entity(
    tableName = "screened_calls",
    indices = [Index("normalizedNumber")],
)
data class ScreenedCall(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawNumber: String?,
    val normalizedNumber: String?,
    val timestamp: Long,
    // name of a ScreeningOutcome
    val outcome: String,
    // a VerificationStatus value
    @ColumnInfo(defaultValue = "0") val verificationStatus: Int = 0,
)

/** mirrors android's Connection.VERIFICATION_STATUS_* values (stir/shaken result from the carrier). */
object VerificationStatus {
    const val NOT_VERIFIED = 0
    const val PASSED = 1
    const val FAILED = 2
}

/** a user's note and tag for a number. kept apart from the log so it survives log cleanup. */
@Entity(tableName = "number_info")
data class NumberInfo(
    @PrimaryKey val normalizedNumber: String,
    val note: String,
    // empty, or one of NumberTag
    val tag: String,
    val updatedAt: Long,
)

object NumberTag {
    const val SPAM = "SPAM"
    const val KNOWN = "KNOWN"
}
