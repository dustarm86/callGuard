package com.dustin.callguard.screening

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactLookup {
    /**
     * PhoneLookup does its own minimal-match comparison, so formats like
     * +1 / dashes / parentheses mostly sort themselves out. we still retry
     * with the bare digits in case the raw handle is oddly formatted.
     */
    fun isContact(context: Context, rawNumber: String): Boolean {
        val candidates = listOfNotNull(rawNumber, NumberNormalizer.normalize(rawNumber)).distinct()
        return candidates.any { lookup(context, it) }
    }

    private fun lookup(context: Context, number: String): Boolean {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number),
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null,
            )?.use { cursor -> cursor.count > 0 } ?: false
        } catch (e: SecurityException) {
            false
        }
    }
}
