package com.dmzs.datawatchclient.transport

/**
 * Typed failure modes for transport operations. Mobile never leaks generic
 * exceptions into the UI; everything lands as one of these so banners + retries
 * can be chosen deterministically (ADR-0013: visible failures, no silent retry).
 */
public sealed class TransportError(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    /** DNS / connect / TLS / read timeout — the server wasn't reachable at all. */
    public class Unreachable(cause: Throwable? = null) :
        TransportError("Server unreachable", cause)

    /** 401 / 403. Token invalid or revoked — user must re-pair. */
    public class Unauthorized(message: String = "Bearer token rejected") :
        TransportError(message)

    /** 404 — path doesn't exist on this server version. Typically missing feature. */
    public class NotFound(message: String) : TransportError(message)

    /** Rate limited by the server (429) or an upstream LLM. */
    public class RateLimited(public val retryAfterSeconds: Long? = null) :
        TransportError("Rate limited")

    /** 5xx from the server. */
    public class ServerError(public val status: Int, message: String) :
        TransportError("$status: $message")

    /** Body could not be parsed as expected. Indicates version skew. */
    public class ProtocolMismatch(message: String, cause: Throwable? = null) :
        TransportError(message, cause)

    /** TLS / cert / pinning failure. User must confirm trust anchor or cancel. */
    public class TrustFailure(message: String, cause: Throwable? = null) :
        TransportError(message, cause)
}
