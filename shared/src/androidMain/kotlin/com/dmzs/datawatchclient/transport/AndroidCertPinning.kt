package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.rest.RestTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Certificate pinning (trust-on-first-use), parity D91a — the Android side of
 * iOS `IosCertPins` / `CertProbe`.
 *
 * A pin is the lowercase hex SHA-256 of the server's leaf certificate (DER),
 * stored in `ServerProfile.trustAnchorSha256`. A pinned profile accepts exactly
 * that leaf certificate in place of CA validation (the supported path for
 * self-signed servers), and — unlike the trust-all option — keeps OkHttp's
 * default hostname verification, so the certificate must still name the host.
 */
public object CertPins {
    /** Lowercase hex with separators (`:` / spaces) removed. */
    public fun normalize(hex: String): String = hex.lowercase().filter { it in '0'..'9' || it in 'a'..'f' }

    /** True when [value] is a usable SHA-256 pin (not null, not the trust-all sentinel). */
    public fun isPin(
        value: String?,
        trustAllSentinel: String,
    ): Boolean = value != null && value != trustAllSentinel && normalize(value).length == SHA256_HEX_LEN

    /** `AB:CD:…` grouping for display (same format as iOS `CertProbe.display`). */
    public fun display(hex: String): String = normalize(hex).uppercase().chunked(2).joinToString(":")

    /** Lowercase hex SHA-256 of [der]. */
    public fun sha256Hex(der: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { "%02x".format(it) }

    internal const val SHA256_HEX_LEN = 64
}

/**
 * Accepts the server chain iff its leaf certificate matches [pinHex]. No CA
 * validation (that's the point of pinning a self-signed cert); hostname
 * verification is left to OkHttp's default verifier.
 */
internal class PinnedTrustManager(pinHex: String) : X509TrustManager {
    private val pin = CertPins.normalize(pinHex)

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ): Unit = throw CertificateException("Client certificates are not supported")

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("Server presented no certificate")
        if (CertPins.sha256Hex(leaf.encoded) != pin) {
            throw CertificateException("Server certificate does not match the pinned fingerprint")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

internal fun pinnedSslContext(tm: X509TrustManager): SSLContext =
    SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), SecureRandom()) }

/**
 * Socket factory that accepts exactly the leaf certificate [pinSha256] — for
 * `HttpsURLConnection` users (the docs WebView's request interceptor). Keep the
 * connection's default hostname verifier.
 */
public fun pinnedSocketFactory(pinSha256: String): javax.net.ssl.SSLSocketFactory =
    pinnedSslContext(PinnedTrustManager(pinSha256)).socketFactory

/** REST client for a pinned profile — same timeouts/pool as [createHttpClient]. */
public fun createPinnedHttpClient(pinSha256: String): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                connectionPool(
                    okhttp3.ConnectionPool(5, 10, java.util.concurrent.TimeUnit.MINUTES),
                )
                val tm = PinnedTrustManager(pinSha256)
                sslSocketFactory(pinnedSslContext(tm).socketFactory, tm)
                // Hostname verification intentionally left at OkHttp's default.
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 30_000
        }
        install(ContentNegotiation) { json(RestTransport.DefaultJson) }
        expectSuccess = true
    }

/** Leaf certificate a server presented to [probeServerCertificate]. */
public data class CertFingerprint(
    /** Lowercase hex SHA-256 of the leaf DER. */
    val sha256Hex: String,
    val subject: String,
    val issuer: String,
    val notAfterEpochMs: Long,
) {
    val display: String get() = CertPins.display(sha256Hex)
}

/**
 * Fetches the TLS leaf certificate [baseUrl] presents — without trusting it and
 * without sending any HTTP request — so the user can review its SHA-256 and pin
 * it (iOS `CertProbe.fetch`). The handshake is aborted right after capture.
 */
public suspend fun probeServerCertificate(baseUrl: String): Result<CertFingerprint> =
    withContext(Dispatchers.IO) {
        runCatching {
            val url = Url(baseUrl.trim())
            require(url.protocol.name == "https") { "Enter an https:// URL first." }
            var captured: X509Certificate? = null
            val capture =
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<X509Certificate>,
                        authType: String,
                    ): Unit = throw CertificateException("unsupported")

                    override fun checkServerTrusted(
                        chain: Array<X509Certificate>,
                        authType: String,
                    ) {
                        captured = chain.firstOrNull()
                        // Abort: the probe never trusts what it captures.
                        throw CertificateException("probe: captured, aborting handshake")
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
            val factory = pinnedSslContext(capture).socketFactory
            val port = if (url.port > 0) url.port else url.protocol.defaultPort
            val raw = java.net.Socket()
            raw.use {
                raw.connect(InetSocketAddress(url.host, port), PROBE_TIMEOUT_MS)
                raw.soTimeout = PROBE_TIMEOUT_MS
                // Layered socket keeps the host name so SNI is sent.
                (factory.createSocket(raw, url.host, port, true) as SSLSocket).use { socket ->
                    runCatching { socket.startHandshake() }
                }
            }
            val leaf = captured ?: error("Could not reach the server or it presented no certificate.")
            CertFingerprint(
                sha256Hex = CertPins.sha256Hex(leaf.encoded),
                subject = leaf.subjectX500Principal.name,
                issuer = leaf.issuerX500Principal.name,
                notAfterEpochMs = leaf.notAfter.time,
            )
        }
    }

private const val PROBE_TIMEOUT_MS = 10_000
