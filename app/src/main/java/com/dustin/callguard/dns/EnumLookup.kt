package com.dustin.callguard.dns

import android.content.Context
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.Network
import android.os.CancellationSignal
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * asks the phone's own dns resolver for the enum records of a number. this sends the number's digits
 * to that resolver (or, as a fallback, to cloudflare over https), so it only runs when the person taps
 * the button. it never contacts the number.
 */
object EnumLookup {
    private const val DOH_URL = "https://cloudflare-dns.com/dns-query"

    suspend fun query(context: Context, number: String): List<String> {
        val name = EnumDns.name(number) ?: return listOf(EnumDns.VERDICT + "can't check. enum needs a full 10 digit north american number")
        val network = context.getSystemService(ConnectivityManager::class.java)?.activeNetwork
            ?: return EnumDns.couldntCheck("there is no network connection")
        val systemFailure = try {
            val packet = withTimeout(5_000) { rawQuery(network, name) }
            return EnumDns.describe(name, EnumDns.parse(packet)) + "answered by your phone's own dns resolver"
        } catch (e: TimeoutCancellationException) {
            "no answer within 5 seconds"
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
        // many routers drop or mangle naptr queries, so ask a public resolver over https as a second opinion
        return try {
            val packet = withContext(Dispatchers.IO) { httpsQuery(name) }
            val lines = EnumDns.describe(name, EnumDns.parse(packet))
            // verdict first, then the routine detail about which resolver answered
            listOf(lines.first()) + lines.drop(1) +
                "answered by cloudflare over https. your phone's own resolver didn't reply ($systemFailure), which is common on home networks"
        } catch (e: Exception) {
            EnumDns.couldntCheck(
                "asked dns for naptr records at $name",
                "your phone's dns resolver failed: $systemFailure",
                "the https fallback failed too: ${e.message ?: e.javaClass.simpleName}",
            )
        }
    }

    private fun httpsQuery(name: String): ByteArray {
        val connection = URL(DOH_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/dns-message")
            connection.setRequestProperty("Accept", "application/dns-message")
            connection.outputStream.use { it.write(EnumDns.buildQuery(name)) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("http ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun rawQuery(network: Network, name: String): ByteArray = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        DnsResolver.getInstance().rawQuery(
            network,
            name,
            DnsResolver.CLASS_IN,
            EnumDns.NAPTR_TYPE,
            DnsResolver.FLAG_EMPTY,
            Executor { it.run() },
            signal,
            object : DnsResolver.Callback<ByteArray> {
                override fun onAnswer(answer: ByteArray, rcode: Int) = continuation.resume(answer)
                override fun onError(error: DnsResolver.DnsException) = continuation.resumeWithException(error)
            },
        )
    }
}
