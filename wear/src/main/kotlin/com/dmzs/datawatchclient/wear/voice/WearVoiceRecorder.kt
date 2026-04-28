package com.dmzs.datawatchclient.wear.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Wear-side audio capture. Mirror of `composeApp` `VoiceRecorder.kt` but
 * lives in the wear module so Wear's `WearMainActivity` doesn't reach
 * across module boundaries. Same AAC-in-MP4 container, same 16 kHz mono
 * encoder, same single-use lifecycle.
 *
 * v0.35.8 added this when the system speech-recognizer (`RecognizerIntent`)
 * wasn't reliably available on the Galaxy Watch (no on-device model + the
 * Pixel-Watch-style Google Assistant flow doesn't return text to the
 * launching activity). The replacement flow is: record → ship raw bytes
 * to the phone via Wearable MessageClient → phone runs the daemon's
 * `/api/voice/transcribe` → phone replies with text via MessageClient
 * → watch shows the transcript inside the existing SessionDetailPopup
 * for the user to validate before sending.
 */
public class WearVoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    public fun start() {
        val file = File.createTempFile("dw-wear-voice-", ".m4a", context.cacheDir)
        outputFile = file
        @Suppress("DEPRECATION")
        recorder =
            (
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    MediaRecorder()
                }
            ).apply {
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

    /**
     * Stops recording and returns the captured audio bytes plus their
     * MIME type. Returns null if the recorder wasn't started or if
     * `MediaRecorder.stop()` threw — the latter happens when the user
     * taps stop within ~300 ms of start (encoder doesn't have a
     * complete frame yet); we silently drop in that case.
     */
    public fun stop(): Pair<ByteArray, String>? {
        val r = recorder ?: return null
        val f = outputFile ?: return null
        return try {
            r.stop()
            r.release()
            val bytes = f.readBytes()
            Pair(bytes, "audio/mp4")
        } catch (e: Throwable) {
            android.util.Log.w("WearVoiceRecorder", "stop failed: ${e.message}")
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
