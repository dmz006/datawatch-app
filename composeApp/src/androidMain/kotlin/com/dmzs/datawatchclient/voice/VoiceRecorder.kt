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
    private var mutedRinger: Boolean = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    public fun start() {
        val file = File.createTempFile("dw-voice-", ".m4a", context.cacheDir)
        outputFile = file

        // Suppress the start/stop system sounds by temporarily muting the ringer.
        // Best effort only: with Do Not Disturb / silent mode on, Android refuses
        // ring-volume changes ("Not allowed to change Do Not Disturb state") unless
        // the app has notification-policy access — that SecurityException used to
        // abort recording entirely. Muting is cosmetic, so never let it fail start().
        mutedRinger =
            runCatching {
                savedRingerVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                if (savedRingerVolume > 0) audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
                savedRingerVolume > 0
            }.getOrDefault(false)

        @Suppress("DEPRECATION")
        val r =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
        try {
            r.apply {
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
            recorder = r
        } catch (e: Exception) {
            // Mic busy / unavailable: release everything and put the ringer back.
            runCatching { r.release() }
            file.delete()
            outputFile = null
            restoreRingerVolume()
            throw e
        }
    }

    /**
     * Live input level 0..1 for the recording dialog's waveform — peak
     * amplitude since the previous call ([MediaRecorder.getMaxAmplitude]),
     * square-root scaled so normal speech fills most of the bar range.
     */
    public fun level(): Float {
        val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        val norm = (amp / 32767f).coerceIn(0f, 1f)
        return kotlin.math.sqrt(norm)
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
        if (!mutedRinger) return
        mutedRinger = false
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_RING, savedRingerVolume, 0)
        }
    }
}
