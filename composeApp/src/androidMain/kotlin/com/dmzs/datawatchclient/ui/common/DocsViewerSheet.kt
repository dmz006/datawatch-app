package com.dmzs.datawatchclient.ui.common

import android.net.http.SslError
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocsViewerSheet(
    url: String,
    onDismiss: () -> Unit,
    /**
     * When true, the WebView's sub-resource fetches accept self-signed
     * certificates (trust-all SSL). Should be tied to the profile's
     * self-signed-cert toggle so this isn't a blanket disable of TLS
     * verification for users whose servers DO have valid certs. Default
     * `false` — trust-all must be opted in by the profile config.
     */
    allowSelfSigned: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    println("DocsViewer: loading url=$url allowSelfSigned=$allowSelfSigned")
                    // MATCH_PARENT on both axes so the WebView fills the
                    // sheet area instead of measuring as 0 inside the
                    // Material BottomSheet's Column layout.
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#0e1013"))
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler,
                            error: SslError,
                        ) {
                            // Accepts the main page only — sub-resource SSL errors
                            // don't reach this callback; those go through
                            // shouldInterceptRequest below where we re-fetch via
                            // our trust-all HttpURLConnection.
                            handler.proceed()
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null
                            println("DocsViewer: intercept request url=$reqUrl method=${request.method}")
                            if (!reqUrl.startsWith("https://")) return null
                            return runCatching {
                                val conn = (URL(reqUrl).openConnection() as HttpsURLConnection).apply {
                                    // Only override TLS verification when the
                                    // profile is explicitly marked as using a
                                    // self-signed cert. Trustworthy certs go
                                    // through the system's normal verification.
                                    if (allowSelfSigned) {
                                        sslSocketFactory = trustAllSslContext.socketFactory
                                        hostnameVerifier = TrustAllHostnameVerifier
                                    }
                                    connectTimeout = 10_000
                                    readTimeout = 10_000
                                    instanceFollowRedirects = true
                                    requestMethod = request.method ?: "GET"
                                    request.requestHeaders.forEach { (k, v) ->
                                        if (!k.equals("Accept-Encoding", ignoreCase = true)) {
                                            setRequestProperty(k, v)
                                        }
                                    }
                                }
                                val status = conn.responseCode
                                val ct = conn.contentType ?: "text/plain"
                                val mime = ct.substringBefore(';').trim().ifBlank { "text/plain" }
                                val charset =
                                    ct.substringAfter("charset=", "utf-8")
                                        .substringBefore(';').trim().ifBlank { "utf-8" }
                                val body =
                                    (if (status in 200..399) conn.inputStream else conn.errorStream)
                                        ?.readBytes() ?: ByteArray(0)
                                println("DocsViewer: intercept ok url=$reqUrl status=$status mime=$mime bytes=${body.size}")
                                WebResourceResponse(
                                    mime,
                                    charset,
                                    status,
                                    conn.responseMessage ?: "OK",
                                    conn.headerFields
                                        .filterKeys { it != null }
                                        .mapValues { (_, v) -> v.joinToString(",") },
                                    ByteArrayInputStream(body),
                                )
                            }.onFailure {
                                println("DocsViewer: intercept failed url=$reqUrl err=$it")
                            }.getOrNull()
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            println("DocsViewer: load error url=${request?.url} code=${error?.errorCode} desc=${error?.description}")
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            println("DocsViewer: page finished url=$url")
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            println("DocsViewer-js: ${consoleMessage.messageLevel()} ${consoleMessage.message()} (line ${consoleMessage.lineNumber()})")
                            return true
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Without trust-all here, self-signed certs fail the
                    // mixed-cert check that NetworkSecurityConfig enforces
                    // for the app's MAIN WebView; the per-profile trust
                    // anchor only applies to OkHttp. For the docs page,
                    // proceed on SSL error (we already do via WebViewClient).
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize().heightIn(min = 500.dp),
        )
    }
}

// Trust-all SSL context for the docs WebView's sub-resource fetches.
// Required because the WebView's onReceivedSslError callback ONLY fires
// for the main page navigation — XHR / fetch() calls inside the loaded
// page silently fail on self-signed certs. We intercept every HTTPS
// sub-resource and re-fetch via this trust-all stack instead.
// Scoped to the docs viewer only — does NOT affect the main app's
// TLS verification (OkHttp / Ktor use per-profile trust anchors).
private val trustAllSslContext: SSLContext by lazy {
    val tm = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    }
    SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), java.security.SecureRandom()) }
}

private object TrustAllHostnameVerifier : javax.net.ssl.HostnameVerifier {
    override fun verify(hostname: String?, session: javax.net.ssl.SSLSession?): Boolean = true
}
