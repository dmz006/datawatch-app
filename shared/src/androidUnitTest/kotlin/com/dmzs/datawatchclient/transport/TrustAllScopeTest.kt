package com.dmzs.datawatchclient.transport

import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The "Trust all certificates (insecure)" opt-in is scoped to the profile's own host. */
class TrustAllScopeTest {
    // Same throwaway self-signed cert as CertPinningTest (CN=pin-test.invalid).
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

    private val cert: X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(pem.byteInputStream()) as X509Certificate

    @Test
    fun `covers only the opted-in host, case-insensitively`() {
        val tm = HostScopedTrustAllManager("datawatch.example")
        assertTrue(tm.covers("datawatch.example"))
        assertTrue(tm.covers("DataWatch.EXAMPLE"))
        assertFalse(tm.covers("evil.example.com"))
        assertFalse(tm.covers(null))
    }

    @Test
    fun `self-signed cert without host context is validated by the system trust store`() {
        // The 2-arg check has no peer host, so the opt-in can't apply → system rejects.
        assertFailsWith<CertificateException> {
            HostScopedTrustAllManager("datawatch.example").checkServerTrusted(arrayOf(cert), "ECDHE_ECDSA")
        }
    }

    @Test
    fun `other hosts are delegated to the system trust manager`() {
        var delegated = 0
        val system =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) = Unit

                override fun checkServerTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) {
                    delegated++
                    throw CertificateException("system rejects")
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        val tm = HostScopedTrustAllManager("datawatch.example", system)
        // No socket → no peer host → never the opt-in path.
        assertFailsWith<CertificateException> {
            tm.checkServerTrusted(arrayOf(cert), "ECDHE_ECDSA", null as java.net.Socket?)
        }
        assertEquals(1, delegated)
    }

    @Test
    fun `client certificates are refused`() {
        assertFailsWith<CertificateException> {
            HostScopedTrustAllManager("h").checkClientTrusted(arrayOf(cert), "RSA")
        }
    }

    @Test
    fun `hostname verifier relaxes only the opted-in host`() {
        val fallback = HostnameVerifier { _, _ -> false }
        val v = hostScopedHostnameVerifier("datawatch.example", fallback)
        assertTrue(v.verify("datawatch.example", null))
        assertFalse(v.verify("evil.example.com", null))
    }

    @Test
    fun `trustAllHostOf extracts the lowercase host`() {
        assertEquals("datawatch.example", trustAllHostOf("https://DataWatch.Example:8443/"))
        assertNull(trustAllHostOf(""))
    }
}
