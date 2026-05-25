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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.voice.VoiceRecorder
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    // WebView ref + tracked back-stack state so the title bar back arrow
    // and the device back button both navigate within the docs first and
    // only dismiss the dialog once we're back at the original page.
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }

    // Device back button: pop the WebView's back stack first, then dismiss.
    BackHandler(enabled = true) {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // BackHandler above takes priority over Dialog's own back
            // handling, so we set dismissOnBackPress = false here.
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            // navigationBarsPadding keeps the bottom of the docs WebView
            // above the Android system nav bar (gesture pill / 3-button
            // bar) so the last lines of long doc pages aren't hidden
            // under the system chrome.
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            color = Color(0xFF0e1013),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Back-within-docs arrow — only enabled when the
                        // WebView has back history (i.e., user has
                        // followed a cross-doc link). Tapping it returns
                        // to the previously-viewed doc page.
                        IconButton(
                            onClick = { webViewRef?.goBack() },
                            enabled = canGoBack,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to previous doc",
                                tint = if (canGoBack) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                },
                            )
                        }
                        Text(
                            "Documentation",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Magnifying-glass opens a search dialog. Cheaper
                        // than asking the user to scroll back to the top
                        // and find the page's filter input — esp. after
                        // a cross-doc link lands them mid-page on a long
                        // manual entry. Submit drives the page's existing
                        // /api/docs/search box via evaluateJavascript.
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = "Search documentation",
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close documentation",
                            )
                        }
                    }
                }
                AndroidView(
                    factory = { ctx ->
                WebView(ctx).apply {
                    // MATCH_PARENT on both axes so the WebView fills the
                    // sheet area instead of measuring as 0 inside the
                    // Material BottomSheet's Column layout.
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#0e1013"))
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val target = request?.url?.toString() ?: return false
                            // Cross-doc links in the diagrams viewer render as
                            // relative <a href="howto/foo.md">. Clicking them
                            // navigates the WebView to the raw .md URL —
                            // which then displays as plain text instead of the
                            // rendered docs page. Rewrite any .md link to use
                            // the viewer's #docs/<path> hash format so
                            // openFromHash() picks it up and renders properly.
                            if (target.endsWith(".md") && target.contains("/docs/")) {
                                val docPath = target.substringAfter("/docs/", "")
                                if (docPath.isNotBlank()) {
                                    val origin = target.substringBefore("/docs/")
                                    val rewritten = "$origin/diagrams.html#docs/$docPath"
                                    view?.loadUrl(rewritten)
                                    return true
                                }
                            }
                            return false
                        }

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
                                val rawBody =
                                    (if (status in 200..399) conn.inputStream else conn.errorStream)
                                        ?.readBytes() ?: ByteArray(0)
                                // Inject the docs-viewer style override BEFORE the
                                // page paints, so the user never sees the
                                // unstyled "← PWA" link or the wrapped
                                // "API spec / MCP tools" header line. Doing it
                                // in onPageFinished left a ~200–500ms flash
                                // of the original layout. We splice a <style>
                                // tag into <head> on the fly for any HTML
                                // response served from the docs WebView.
                                val body = if (mime.equals("text/html", ignoreCase = true)) {
                                    injectDocsStyleOverride(rawBody, charset)
                                } else {
                                    rawBody
                                }
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
                            }.getOrNull()
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            // Update the back-stack enabled state after each
                            // navigation so the title-bar arrow lights up
                            // when we've followed a cross-doc link.
                            canGoBack = view?.canGoBack() == true
                            // Note: mobile-only CSS overrides are spliced
                            // into the HTML <head> in shouldInterceptRequest
                            // so the page paints already-styled — no JS
                            // injection here (it caused a visible flash of
                            // the original layout before the rules applied).
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            return true
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    loadUrl(url)
                    webViewRef = this
                }
            },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }

    if (searchOpen) {
        DocsSearchDialog(
            onDismiss = { searchOpen = false },
            onSearch = { query ->
                val esc = query
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                // Drive the page's existing /api/docs/search box:
                //   1. un-collapse the sidebar (mobile defaults to hidden
                //      via `aside-collapsed`; results render in the sidebar)
                //   2. set #filter value
                //   3. dispatch 'input' event so the debounced runSearch()
                //      handler in diagrams.html fires the API call
                webViewRef?.evaluateJavascript(
                    """
                    (function(){
                      try {
                        var body = document.getElementById('bodyEl');
                        if (body) body.classList.remove('aside-collapsed');
                        var el = document.getElementById('filter');
                        if (!el) return 'no-filter';
                        el.value = '$esc';
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.focus();
                        return 'ok';
                      } catch (e) { return 'err:' + e.message; }
                    })();
                    """.trimIndent(),
                    null,
                )
                searchOpen = false
            },
        )
    }
}

@Composable
private fun DocsSearchDialog(
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var voiceRecorder by remember { mutableStateOf<VoiceRecorder?>(null) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var transcribingVoice by remember { mutableStateOf(false) }
    val profiles by ServiceLocator.profileRepository.observeAll().collectAsState(initial = emptyList())
    val activeId by ServiceLocator.activeServerStore.observe().collectAsState(initial = null)

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val r = VoiceRecorder(context)
            runCatching { r.start() }
                .onSuccess { voiceRecorder = r; showVoiceDialog = true }
                .onFailure { e ->
                    android.widget.Toast.makeText(
                        context,
                        "Recording failed: ${e.message ?: e::class.simpleName}",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
        } else {
            android.widget.Toast.makeText(
                context,
                "Microphone permission denied — enable it in Settings.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    if (showVoiceDialog) {
        VoiceRecordingDialog(
            onCancel = {
                voiceRecorder?.cancel()
                voiceRecorder = null
                showVoiceDialog = false
            },
            onSend = {
                val r = voiceRecorder ?: run { showVoiceDialog = false; return@VoiceRecordingDialog }
                voiceRecorder = null
                showVoiceDialog = false
                val captured = r.stop() ?: return@VoiceRecordingDialog
                transcribingVoice = true
                scope.launch {
                    try {
                        val enabled = profiles.filter { it.enabled }
                        val profile = (enabled.firstOrNull { it.id == activeId }
                            ?: enabled.firstOrNull())
                        if (profile != null) {
                            ServiceLocator.transportFor(profile)
                                .transcribeAudio(
                                    audio = captured.first,
                                    audioMime = captured.second,
                                    sessionId = null,
                                    autoExec = false,
                                )
                                .onSuccess { result ->
                                    val t = result.transcript.trim()
                                    if (t.isNotEmpty()) query = TextFieldValue(t)
                                }
                                .onFailure { err ->
                                    android.widget.Toast.makeText(
                                        context,
                                        "Transcribe failed: ${err.message}",
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                }
                        }
                    } finally {
                        transcribingVoice = false
                    }
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search docs") },
        text = {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = {
                    Text(if (transcribingVoice) "Transcribing…" else "Type a query (≥3 chars)")
                },
                enabled = !transcribingVoice,
                trailingIcon = {
                    if (transcribingVoice) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(
                            onClick = {
                                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.RECORD_AUDIO,
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    val r = VoiceRecorder(context)
                                    runCatching { r.start() }
                                        .onSuccess { voiceRecorder = r; showVoiceDialog = true }
                                        .onFailure { e ->
                                            android.widget.Toast.makeText(
                                                context,
                                                "Recording failed: ${e.message ?: e::class.simpleName}",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                } else {
                                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = "Voice search",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = {
                        val q = query.text.trim()
                        if (q.isNotEmpty()) onSearch(q)
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val q = query.text.trim()
                if (q.isNotEmpty()) onSearch(q)
            }) { Text("Search") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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

// Splice mobile docs-viewer CSS overrides into the HTML <head> before the
// page paints. Hides the "← PWA" link (it's a re-entry to the PWA inside
// the WebView, not a back action — confusing on mobile) and stacks the
// header links vertically so "MCP tools" doesn't wrap.
private const val DOCS_STYLE_TAG = "<style id=\"dw-mobile-docs-style\">" +
    "header a[href=\"/\"] { display: none !important; }" +
    "header .links { display: flex !important; flex-direction: column !important; gap: 2px !important; align-items: flex-end !important; }" +
    "header .links a { white-space: nowrap !important; }" +
    "</style>"

private fun injectDocsStyleOverride(rawBody: ByteArray, charset: String): ByteArray {
    val charsetObj = runCatching { java.nio.charset.Charset.forName(charset) }
        .getOrDefault(Charsets.UTF_8)
    val html = rawBody.toString(charsetObj)
    val headCloseIdx = html.indexOf("</head>", ignoreCase = true)
    val patched = if (headCloseIdx >= 0) {
        html.substring(0, headCloseIdx) + DOCS_STYLE_TAG + html.substring(headCloseIdx)
    } else {
        // No <head> tag (unusual) — prepend so the rules still apply.
        DOCS_STYLE_TAG + html
    }
    return patched.toByteArray(charsetObj)
}
