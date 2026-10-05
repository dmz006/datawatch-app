package com.dmzs.datawatchclient.transport

import io.ktor.http.Url
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/*
 * "Trust all certificates (insecure)" — the explicit, per-profile opt-in.
 *
 * A server profile whose `trustAnchorSha256` equals the trust-all sentinel
 * (set only by the user ticking the warned "Trust all certificates (insecure)"
 * box on the server form) talks to its server without certificate validation.
 * That bypass is scoped to exactly that profile's host:
 *
 *  - [HostScopedTrustAllManager] skips validation only when the TLS peer host
 *    equals the opted-in host; any other host (e.g. a cross-host redirect, or a
 *    third-party resource in the docs WebView) is validated by the platform's
 *    default trust manager.
 *  - [hostScopedHostnameVerifier] relaxes hostname checks only for the same host
 *    and defers to the default verifier everywhere else.
 *
 * Profiles that did not opt in never reach this code: they use the system-trust
 * client or, when they carry a SHA-256 pin, [PinnedTrustManager] with default
 * hostname verification (see AndroidCertPinning.kt).
 *
 * CodeQL `java/insecure-trustmanager` flags this class by design; it is the
 * documented opt-in and is suppressed with a justification in
 * .github/codeql/codeql-config.yml.
 */

/** Lowercase host of a profile base URL, or null when it cannot be parsed. */
public fun trustAllHostOf(baseUrl: String): String? {
    val trimmed = baseUrl.trim()
    // Ktor's Url defaults a missing host to "localhost" — require an absolute URL.
    if (!trimmed.contains("://")) return null
    return runCatching { Url(trimmed).host.lowercase() }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun systemDefaultTrustManager(): X509TrustManager {
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    tmf.init(null as KeyStore?)
    return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
}

/**
 * Accepts any server certificate for [allowedHost] only (the user's opt-in);
 * validates every other host with the platform default trust manager.
 */
internal class HostScopedTrustAllManager(
    allowedHost: String,
    private val system: X509TrustManager = systemDefaultTrustManager(),
) : X509ExtendedTrustManager() {
    private val host = allowedHost.lowercase()

    /** True when the opt-in covers [peerHost]. Unknown host → not covered (fail closed). */
    internal fun covers(peerHost: String?): Boolean = peerHost != null && peerHost.equals(host, ignoreCase = true)

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?,
    ) {
        if (covers((socket as? SSLSocket)?.handshakeSession?.peerHost)) return
        val sys = system
        if (sys is X509ExtendedTrustManager) sys.checkServerTrusted(chain, authType, socket) else sys.checkServerTrusted(chain, authType)
    }

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        engine: SSLEngine?,
    ) {
        if (covers(engine?.handshakeSession?.peerHost ?: engine?.peerHost)) return
        val sys = system
        if (sys is X509ExtendedTrustManager) sys.checkServerTrusted(chain, authType, engine) else sys.checkServerTrusted(chain, authType)
    }

    // No peer-host context → cannot prove the opt-in applies → full validation.
    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ): Unit = system.checkServerTrusted(chain, authType)

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?,
    ): Unit = throw CertificateException("Client certificates are not supported")

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        engine: SSLEngine?,
    ): Unit = throw CertificateException("Client certificates are not supported")

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ): Unit = throw CertificateException("Client certificates are not supported")

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
}

/**
 * Hostname verifier for an opted-in profile: any name is accepted for
 * [allowedHost] (self-signed certs often lack a matching SAN); every other host
 * goes through [fallback] (the platform default verifier).
 */
public fun hostScopedHostnameVerifier(
    allowedHost: String,
    fallback: HostnameVerifier,
): HostnameVerifier =
    HostnameVerifier { hostname: String?, session: SSLSession? ->
        hostname.equals(allowedHost, ignoreCase = true) || fallback.verify(hostname, session)
    }

internal fun hostScopedTrustAllContext(tm: HostScopedTrustAllManager): SSLContext =
    SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), SecureRandom()) }

/**
 * Socket factory for `HttpsURLConnection` users (the docs WebView's request
 * interceptor) — trust-all for [allowedHost] only, system validation elsewhere.
 * Pair with [hostScopedHostnameVerifier].
 */
public fun hostScopedTrustAllSocketFactory(allowedHost: String): javax.net.ssl.SSLSocketFactory =
    hostScopedTrustAllContext(HostScopedTrustAllManager(allowedHost)).socketFactory
