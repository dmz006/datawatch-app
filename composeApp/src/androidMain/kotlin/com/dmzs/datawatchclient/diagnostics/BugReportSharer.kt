package com.dmzs.datawatchclient.diagnostics

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.dmzs.datawatchclient.R
import java.io.File

/**
 * Hands a report to the user's choice of app via `ACTION_SEND`. The only way a
 * report leaves the device, and only when the user taps Send.
 */
public object BugReportSharer {
    /** Binder transactions cap at ~1 MB; keep the inline text well below that. */
    private const val MAX_EXTRA_TEXT_CHARS = 200_000

    public fun share(
        context: Context,
        text: String,
    ) {
        val subject = SupportContact.subject()
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_EMAIL, arrayOf(SupportContact.EMAIL))
                putExtra(Intent.EXTRA_TEXT, text.take(MAX_EXTRA_TEXT_CHARS))
                attachment(context, text)?.let { uri ->
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(subject, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        val chooser = Intent.createChooser(send, context.getString(R.string.crash_report_share_title))
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
            .onFailure { dwLog("BugReportSharer", "no app to share the report: ${it::class.simpleName}") }
    }

    /** `cacheDir/bug-reports/datawatch-report.txt`, served by the app's existing FileProvider. */
    private fun attachment(
        context: Context,
        text: String,
    ): Uri? =
        runCatching {
            val dir = File(context.cacheDir, "bug-reports").apply { mkdirs() }
            val file = File(dir, "datawatch-report.txt").apply { writeText(text) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
}
