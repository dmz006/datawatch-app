package com.dmzs.datawatchclient.transport

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** One configured ACME domain from `GET /api/acme/status` (server `acme.DomainStatus`). */
public data class AcmeDomain(
    val domain: String,
    val issued: Boolean,
    val inFlight: Boolean,
    /** Null when the server sent Go's zero time (never issued). */
    val notAfter: Instant?,
    val lastError: String,
)

/** One rendered line; [tone] = success / warning / error / muted. */
public data class AcmeLine(
    val text: String,
    val tone: String,
    val error: String = "",
)

/**
 * BL413 — web v8.62 (BL397) Let's Encrypt status, shared by the Settings ›
 * Web Server certificate-source block (PWA `loadAcmeStatus`) and the Observer
 * Certificates card (PWA `loadAcmeHealthCard`) on Android and iOS.
 */
public object AcmeStatusFormat {
    /** PWA text when ACME is off or nothing is configured yet. */
    public const val NOT_ISSUED_YET: String = "Not yet issued — save settings, then restart to start the ACME subsystem."

    /** Domains from the status response; empty when ACME is disabled (the Observer card hides). */
    public fun domains(status: JsonObject): List<AcmeDomain> {
        if ((status["enabled"] as? JsonPrimitive)?.booleanOrNull != true) return emptyList()
        return (status["domains"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            fun s(k: String): String = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
            fun b(k: String): Boolean = (o[k] as? JsonPrimitive)?.booleanOrNull == true
            val name = s("domain").ifBlank { return@mapNotNull null }
            val notAfter =
                s("not_after").takeIf { it.isNotBlank() }
                    ?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    ?.takeIf { it.epochSeconds > 0 }
            AcmeDomain(name, b("issued"), b("in_flight"), notAfter, s("last_error"))
        }
    }

    /** Settings block lines: `domain: issuing… | expires <date> | not issued yet` (+ error). */
    public fun settingsLines(status: JsonObject): List<AcmeLine> {
        val ds = domains(status)
        if (ds.isEmpty()) return listOf(AcmeLine(NOT_ISSUED_YET, "muted"))
        return ds.map { d ->
            val label =
                when {
                    d.inFlight -> "issuing…"
                    d.issued -> "expires " + (d.notAfter?.let { dateOf(it) } ?: "—")
                    else -> "not issued yet"
                }
            AcmeLine("${d.domain}: $label", "muted", d.lastError)
        }
    }

    /**
     * Observer Certificates card lines: tone is the status dot — success when
     * issued with more than 14 days left, warning at 14 or fewer, else error.
     */
    public fun healthLines(
        status: JsonObject,
        nowEpochMs: Long,
    ): List<AcmeLine> =
        domains(status).map { d ->
            val daysLeft: Long? = d.notAfter?.let { (it.toEpochMilliseconds() - nowEpochMs).floorDiv(86_400_000L) }
            val tone =
                when {
                    d.issued && daysLeft != null -> if (daysLeft > 14) "success" else "warning"
                    else -> "error"
                }
            val label =
                when {
                    d.inFlight -> "issuing…"
                    d.issued -> "expires in ${daysLeft ?: "?"}d"
                    else -> "not issued"
                }
            AcmeLine("${d.domain} — $label", tone, d.lastError)
        }

    /** PWA Verify toast text from `GET /api/acme/verify`. */
    public fun verifyMessage(result: JsonObject): Pair<String, Boolean> =
        if ((result["ok"] as? JsonPrimitive)?.booleanOrNull == true) {
            "Verify OK — DNS resolves, ACME directory reachable" to true
        } else {
            "Verify found issues — check the domains list for details" to false
        }

    /**
     * The server's `{"error":"…"}` text out of a transport error message (e.g.
     * "acme not enabled" before the daemon restarts), else the message as is.
     */
    public fun errorText(message: String?): String {
        val m = message.orEmpty()
        return Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(m)?.groupValues?.get(1) ?: m
    }

    private fun dateOf(i: Instant): String = i.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
}
