package com.dmzs.datawatchclient.transport

import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Parity D91a — Android certificate pinning (TOFU). */
class CertPinningTest {
    // Throwaway self-signed test certificate (CN=pin-test.invalid); key discarded.
    private val pem =
        """
        -----BEGIN CERTIFICATE-----
        MIIBizCCATGgAwIBAgIUDFA4jY902DSU4e5IHJDiNu/S9nowCgYIKoZIzj0EAwIw
        GzEZMBcGA1UEAwwQcGluLXRlc3QuaW52YWxpZDAeFw0yNjEwMDQxODUwNThaFw0z
        NjEwMDExODUwNThaMBsxGTAXBgNVBAMMEHBpbi10ZXN0LmludmFsaWQwWTATBgcq
        hkjOPQIBBggqhkjOPQMBBwNCAAQ9HfzWhzvVkfEsSKqP4j20YtXL+iSJzRFLkUKI
        v+cIvcv5G7vFRRU+6GMA/DzDsDHo8GGLJQYXMaaOlgtrvhNJo1MwUTAdBgNVHQ4E
        FgQUPJH014CHw1qmFJgf/vS3vNBGmH8wHwYDVR0jBBgwFoAUPJH014CHw1qmFJgf
        /vS3vNBGmH8wDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiALU2Vv
        CNv+9R2nh3JA6Jd5DIsB8uNXx4Jn3BJ6DwTdUwIhAPZ0y2zA4wOtUXSat3bO+5t7
        Fs2Z3WeYcBvRZrz+2VH8
        -----END CERTIFICATE-----
        """.trimIndent()

    // `openssl x509 -outform DER | sha256sum`
    private val expectedSha = "ec60bdd2d1fa61de1c5ec159d77d0742ad18d988d40120dc61e41972bf98a2ae"

    private val cert: X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(pem.byteInputStream()) as X509Certificate

    @Test
    fun `sha256Hex is the leaf DER digest`() = assertEquals(expectedSha, CertPins.sha256Hex(cert.encoded))

    @Test
    fun `normalize strips separators and lowercases`() =
        assertEquals("abcd01", CertPins.normalize("AB:CD 01"))

    @Test
    fun `display groups bytes like iOS`() = assertEquals("AB:CD:01", CertPins.display("abcd01"))

    @Test
    fun `isPin rejects the trust-all sentinel, null and short values`() {
        assertTrue(CertPins.isPin(expectedSha, "ALLOW_ALL_INSECURE"))
        assertTrue(CertPins.isPin(CertPins.display(expectedSha), "ALLOW_ALL_INSECURE"))
        assertFalse(CertPins.isPin("ALLOW_ALL_INSECURE", "ALLOW_ALL_INSECURE"))
        assertFalse(CertPins.isPin(null, "ALLOW_ALL_INSECURE"))
        assertFalse(CertPins.isPin("abcd", "ALLOW_ALL_INSECURE"))
    }

    @Test
    fun `pinned trust manager accepts the matching leaf`() {
        PinnedTrustManager(CertPins.display(expectedSha)).checkServerTrusted(arrayOf(cert), "ECDHE_ECDSA")
    }

    @Test
    fun `pinned trust manager rejects a different pin`() {
        val other = "0".repeat(64)
        assertFailsWith<CertificateException> {
            PinnedTrustManager(other).checkServerTrusted(arrayOf(cert), "ECDHE_ECDSA")
        }
    }

    @Test
    fun `pinned trust manager rejects an empty chain`() {
        assertFailsWith<CertificateException> {
            PinnedTrustManager(expectedSha).checkServerTrusted(emptyArray(), "ECDHE_ECDSA")
        }
    }

    @Test
    fun `probe refuses non-https URLs`() {
        val r = kotlinx.coroutines.runBlocking { probeServerCertificate("http://example.invalid") }
        assertTrue(r.isFailure)
    }
}
