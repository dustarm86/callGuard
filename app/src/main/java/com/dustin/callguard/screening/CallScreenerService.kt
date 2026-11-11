package com.dustin.callguard.screening

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.util.Log
import androidx.core.content.ContextCompat
import com.dustin.callguard.data.AppDatabase
import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.data.ScreenerSettingsStore
import com.dustin.callguard.data.VerificationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDateTime

class CallScreenerService : CallScreeningService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        Log.i(TAG, "onScreenCall received")
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, allowResponse())
            return
        }

        serviceScope.launch {
            // fail open: if anything goes wrong or takes too long, let the phone ring
            val response = try {
                withTimeout(SCREENING_TIMEOUT_MS) { screenCall(callDetails) }
            } catch (e: Exception) {
                Log.w(TAG, "screening failed, letting the call ring", e)
                allowResponse()
            }
            respondToCall(callDetails, response)
            Log.i(TAG, "respondToCall sent")
        }
    }

    private suspend fun screenCall(callDetails: Call.Details): CallResponse {
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        // without contacts access every real contact would look unknown, so don't screen at all
        if (!hasContactsPermission) return allowResponse()

        val rawNumber = callDetails.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
        val normalizedNumber = NumberNormalizer.normalize(rawNumber)
        val database = AppDatabase.get(applicationContext)
        val settings = ScreenerSettingsStore(applicationContext).load()
        val nowMillis = System.currentTimeMillis()

        val isContact = rawNumber != null && ContactLookup.isContact(applicationContext, rawNumber)
        val isAllowlisted = normalizedNumber != null &&
            database.allowedNumberDao().isAllowedNumber(normalizedNumber, nowMillis)
        val hasRecentSilencedCall = normalizedNumber != null && settings.repeatBypassEnabled &&
            database.screenedCallDao().countSilencedSince(
                normalizedNumber,
                nowMillis - settings.repeatWindowMinutes * 60_000L,
            ) > 0
        val verificationStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            callDetails.callerNumberVerificationStatus
        } else {
            VerificationStatus.NOT_VERIFIED
        }
        val verificationFailed = verificationStatus == Connection.VERIFICATION_STATUS_FAILED

        val outcome = ScreeningEngine.decide(
            ScreeningInput(
                normalizedNumber = normalizedNumber,
                isContact = isContact,
                isAllowlisted = isAllowlisted,
                hasRecentSilencedCall = hasRecentSilencedCall,
                verificationFailed = verificationFailed,
                settings = settings,
                now = LocalDateTime.now(),
            )
        )

        if (outcome.shouldLog) {
            val screenedCallDao = database.screenedCallDao()
            screenedCallDao.insert(
                ScreenedCall(
                    rawNumber = rawNumber,
                    normalizedNumber = normalizedNumber,
                    timestamp = nowMillis,
                    outcome = outcome.name,
                    verificationStatus = verificationStatus,
                )
            )
            screenedCallDao.deleteOlderThan(nowMillis - LOG_RETENTION_MS)
        }

        Log.i(TAG, "outcome=${outcome.name} allowsRing=${outcome.allowsRing}")
        return if (outcome.allowsRing) allowResponse() else silenceResponse()
    }

    private fun allowResponse(): CallResponse = CallResponse.Builder().build()

    // silence = no ring, no missed-call notification, but still in the call log
    private fun silenceResponse(): CallResponse = CallResponse.Builder()
        .setDisallowCall(false)
        .setRejectCall(false)
        .setSilenceCall(true)
        .setSkipCallLog(false)
        .setSkipNotification(true)
        .build()

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "CallScreenerService"
        const val SCREENING_TIMEOUT_MS = 3_000L
        const val LOG_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
