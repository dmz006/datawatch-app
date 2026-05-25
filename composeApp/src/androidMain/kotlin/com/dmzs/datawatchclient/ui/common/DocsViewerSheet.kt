package com.dmzs.datawatchclient.ui.common

import android.net.http.SslError
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocsViewerSheet(url: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    println("DocsViewer: loading url=$url")
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler,
                            error: SslError,
                        ) {
                            println("DocsViewer: SSL error for $url — accepting anyway: $error")
                            handler.proceed()
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 500.dp),
        )
    }
}
