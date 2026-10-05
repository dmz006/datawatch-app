package com.dmzs.datawatchclient.transport

import io.ktor.client.engine.darwin.ChallengeHandler
import io.ktor.http.Url
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust
import platform.Security.SecCertificateCopyData
import platform.Security.SecTrustGetCertificateAtIndex
import platform.Security.SecTrustRef

/**
 * Per-host certificate pins: lowercase hex SHA-256 of the server's leaf certificate
 * (DER). Populated from `ServerProfile.trustAnchorSha256` by IosServiceLocator each
 * time a transport is built, consulted by [IosTls.challengeHandler]. A pinned host
 * bypasses system CA validation and accepts exactly that certificate — the
 * supported path for self-signed servers (trust-on-first-use from the server form).
 */
public object IosCertPins {
    private val pins = HashMap<String, String>()

    // Hosts whose profile explicitly opted in to "Trust all certificates (insecure)".
    private val trustAllHosts = HashSet<String>()

    public fun sync(baseUrl: String, trustAnchorSha256: String?, trustAllSentinel: String) {
        val host = hostOf(baseUrl) ?: return
        val pin = trustAnchorSha256?.takeIf { it.isNotBlank() && it != trustAllSentinel }
        if (pin != null) pins[host] = normalize(pin) else pins.remove(host)
        if (trustAnchorSha256 == trustAllSentinel) trustAllHosts.add(host) else trustAllHosts.remove(host)
    }

    public fun pinFor(host: String): String? = pins[host.lowercase()]

    /** True only for a host whose profile opted in to trust-all. */
    public fun isTrustAll(host: String): Boolean = host.lowercase() in trustAllHosts

    public fun normalize(hex: String): String =
        hex.lowercase().filter { it in '0'..'9' || it in 'a'..'f' }

    private fun hostOf(baseUrl: String): String? =
        runCatching { Url(baseUrl).host.lowercase() }.getOrNull()
}

public object IosTls {
    /**
     * Darwin URLSession challenge handler shared by every Ktor client.
     * - trustAll → accept any server trust, but only for a host whose profile
     *   opted in (IosCertPins.isTrustAll); other hosts fall through to the
     *   pin / system-validation paths below.
     * - pinned host → accept iff the leaf SHA-256 matches, else cancel.
     * - otherwise → system default validation (CAs + user-installed anchors).
     */
    @OptIn(ExperimentalForeignApi::class)
    public fun challengeHandler(trustAll: Boolean): ChallengeHandler =
        { _, _, challenge, completion ->
            val space = challenge.protectionSpace
            val trust = space.serverTrust
            if (space.authenticationMethod != NSURLAuthenticationMethodServerTrust || trust == null) {
                completion(NSURLSessionAuthChallengePerformDefaultHandling, null)
            } else if (trustAll && IosCertPins.isTrustAll(space.host)) {
                completion(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(trust))
            } else {
                val pin = IosCertPins.pinFor(space.host)
                when {
                    pin == null ->
                        completion(NSURLSessionAuthChallengePerformDefaultHandling, null)
                    leafSha256Hex(trust) == pin ->
                        completion(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(trust))
                    else ->
                        completion(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                }
            }
        }

    /** Lowercase hex SHA-256 of the leaf certificate's DER bytes, or null if unavailable. */
    @OptIn(ExperimentalForeignApi::class)
    public fun leafSha256Hex(trust: SecTrustRef): String? {
        val cert = SecTrustGetCertificateAtIndex(trust, 0) ?: return null
        val data = SecCertificateCopyData(cert) ?: return null
        try {
            val length = CFDataGetLength(data)
            val bytes = CFDataGetBytePtr(data) ?: return null
            val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
            digest.usePinned { pinned ->
                CC_SHA256(bytes, length.convert(), pinned.addressOf(0).reinterpret<UByteVar>())
            }
            return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        } finally {
            CFRelease(data)
        }
    }
}
