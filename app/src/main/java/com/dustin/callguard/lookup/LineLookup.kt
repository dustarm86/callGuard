package com.dustin.callguard.lookup

import java.net.URLEncoder
import org.json.JSONException
import org.json.JSONObject

/** what an online line lookup says about a number. any field can be missing. */
data class LineInfo(
    val valid: Boolean?,
    val type: String?,
    val carrier: String?,
    val region: String?,
    // set when the service answered with an error, such as a bad key or a used-up quota
    val error: String?,
)

/**
 * the pure part of the veriphone lookup: building the address and reading the answer. the network
 * part is in [LineLookupClient]. veriphone returns a line type and carrier for each number.
 */
object LineLookup {
    private const val BASE = "https://api.veriphone.io/v2/verify"

    /** the request address for a 10-digit north american number. the key is part of the address, so never log it. */
    fun url(number: String, key: String): String =
        "$BASE?phone=%2B1$number&key=${URLEncoder.encode(key.trim(), "UTF-8")}"

    /** reads the service's json answer; a body that isn't json comes back as an error, never a crash. */
    fun parse(body: String): LineInfo {
        val json = try {
            JSONObject(body)
        } catch (e: JSONException) {
            return LineInfo(null, null, null, null, "the service sent something that isn't json")
        }
        val status = json.optString("status")
        if (status.isNotEmpty() && status != "success") {
            val message = json.optString("message").ifBlank { status }
            return LineInfo(null, null, null, null, message)
        }
        fun text(name: String) = json.optString(name).takeIf { it.isNotBlank() && it != "null" }
        return LineInfo(
            valid = if (json.has("phone_valid")) json.optBoolean("phone_valid") else null,
            type = text("phone_type"),
            carrier = text("carrier"),
            region = text("phone_region"),
            error = null,
        )
    }

    /** plain-language lines for the probe screen. */
    fun describe(info: LineInfo): List<String> {
        if (info.error != null) return listOf("lookup failed: ${info.error}")
        val lines = mutableListOf<String>()
        info.valid?.let { lines += if (it) "the service says this is a valid number" else "the service says this is not a valid number" }
        info.type?.let { lines += "line type: ${typeLabel(it)}" }
        info.carrier?.let { lines += "carrier: $it" }
        info.region?.let { lines += "region: $it" }
        if (lines.isEmpty()) lines += "the service answered but gave no details for this number"
        lines += "carrier and type come from a third party's database, so a number that was ported or just reassigned can be out of date"
        return lines
    }

    private fun typeLabel(type: String) = when (type) {
        "mobile" -> "mobile"
        "fixed_line" -> "landline"
        "fixed_line_or_mobile" -> "landline or mobile (the service can't tell)"
        "voip" -> "internet phone (voip), which is the cheapest way for a spammer to get numbers"
        "toll_free" -> "toll-free"
        "premium_rate" -> "premium rate (calling back can cost money)"
        "shared_cost" -> "shared cost"
        "personal_number" -> "personal number"
        "pager" -> "pager"
        "voicemail" -> "voicemail"
        "uan" -> "universal access number"
        else -> type.replace('_', ' ')
    }
}
