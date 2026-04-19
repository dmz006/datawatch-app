package com.dmzs.datawatchclient.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Thin wrapper around [MediaRecorder] producing mp4/aac audio. We use AAC
 * rather than Opus because AAC encoder availability on Android 7+ is
 * universal while Opus requires API 29+ and still bundled codec quirks on
 * some vendors. The server accepts any whisper-supported format.
 *
 * Single-use: construct, [start], [stop] (→ File), discard.
 */
public class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    public fun start() {
        val file = File.createTempFile("dw-voice-", ".m4a", context.cacheDir)
        outputFile = file
        @Suppress("DEPRECATION")
        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioChannels(1)
            setAudioEncodingBitRate(48_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }

    /** Stops recording and returns the captured bytes + MIME type. */
    public fun stop(): Pair<ByteArray, String>? {
        val r = recorder ?: return null
        val f = outputFile ?: return null
        return try {
            r.stop()
            r.release()
            val bytes = f.readBytes()
            Pair(bytes, "audio/mp4")
        } catch (e: Throwable) {
            android.util.Log.w("VoiceRecorder", "stop failed: ${e.message}")
            null
        } finally {
            recorder = null
            outputFile = null
            runCatching { f.delete() }
        }
    }

    public fun cancel() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
