package com.dustin.callguard.screening

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
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
import java.util.concurrent.atomic.AtomicBoolean

class CallScreenerService : CallScreeningService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val startedAt = SystemClock.elapsedRealtime()
        // how long android took to hand us the call (includes starting our process if it was asleep)
        val receivedDelayMs = (System.currentTimeMillis() - callDetails.creationTimeMillis)
            .takeIf { it in 0..MAX_PLAUSIBLE_DELAY_MS }
        val coldStart = !firstCallHandled.getAndSet(true)
        Log.i(TAG, "onScreenCall received, reached us ${receivedDelayMs ?: "?"} ms after android created the call, coldStart=$coldStart")
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, allowResponse())
            return
        }

        serviceScope.launch {
            // fail open: if anything goes wrong or takes too long, let the phone ring
            val response = try {
                withTimeout(SCREENING_TIMEOUT_MS) { screenCall(callDetails, startedAt, receivedDelayMs, coldStart) }
            } catch (e: Exception) {
                Log.w(TAG, "screening failed, letting the call ring", e)
                allowResponse()
            }
            respondToCall(callDetails, response)
            Log.i(TAG, "respondToCall sent after ${SystemClock.elapsedRealtime() - startedAt} ms")
        }
    }

    private suspend fun screenCall(
        callDetails: Call.Details,
        startedAt: Long,
        receivedDelayMs: Long?,
        coldStart: Boolean,
    ): CallResponse {
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        // without contacts access every real contact would look unknown, so don't screen at all
        if (!hasContactsPermission) return allowResponse()

        val rawNumber = callDetails.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
        val snapshot = CallDetailsSnapshot.describe(callDetails)
        Log.i(TAG, "call details: ${snapshot.replace("\n", " | ")}")
        val normalizedNumber = NumberNormalizer.normalize(rawNumber)
        val database = AppDatabase.get(applicationContext)
        val settings = ScreenerSettingsStore(applicationContext).load()
        val nowMillis = System.currentTimeMillis()

        val isContact = rawNumber != null && ContactLookup.isContact(applicationContext, rawNumber)
        val isAllowlisted = normalizedNumber != null && (
            database.allowedNumberDao().isAllowedNumber(normalizedNumber, nowMillis) ||
                database.allowedPrefixDao().matchesAnyPrefix(normalizedNumber)
            )
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
                    receivedDelayMs = receivedDelayMs,
                    decisionMs = SystemClock.elapsedRealtime() - startedAt,
                    coldStart = coldStart,
                    detailsSnapshot = snapshot,
                )
            )
            screenedCallDao.deleteOlderThan(nowMillis - LOG_RETENTION_MS)
        }

        Log.i(TAG, "outcome=${outcome.name} allowsRing=${outcome.allowsRing} rejects=${outcome.rejectsCall}")
        return when {
            outcome.allowsRing -> allowResponse()
            outcome.rejectsCall -> rejectResponse()
            else -> silenceResponse()
        }
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

    // reject = call ends right away; the caller usually hears busy or reaches voicemail
    private fun rejectResponse(): CallResponse = CallResponse.Builder()
        .setDisallowCall(true)
        .setRejectCall(true)
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
        const val MAX_PLAUSIBLE_DELAY_MS = 10 * 60 * 1000L
        // true once this process has handled a call, so the first one can be flagged as a cold start
        val firstCallHandled = AtomicBoolean(false)
        const val LOG_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
