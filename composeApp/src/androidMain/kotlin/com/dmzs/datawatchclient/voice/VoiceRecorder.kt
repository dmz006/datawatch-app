package com.dmzs.datawatchclient.voice

import android.content.Context
import android.media.AudioManager
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
 *
 * Suppresses system sounds during recording by temporarily muting the
 * ringer volume, matching the behavior of voice input on PWA.
 */
public class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var savedRingerVolume: Int = 0
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    public fun start() {
        val file = File.createTempFile("dw-voice-", ".m4a", context.cacheDir)
        outputFile = file

        // Suppress system sounds by temporarily muting the ringer.
        // Save the current volume to restore it on stop().
        savedRingerVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)

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
            restoreRingerVolume()
            runCatching { f.delete() }
        }
    }

    public fun cancel() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile?.delete()
        outputFile = null
        restoreRingerVolume()
    }

    private fun restoreRingerVolume() {
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_RING, savedRingerVolume, 0)
        }
    }
}
