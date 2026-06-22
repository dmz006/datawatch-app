package com.dmzs.datawatchclient.auto.messaging

import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.dmzs.datawatchclient.auto.AutoServiceLocator
import com.dmzs.datawatchclient.auto.AutoSummaryScreen
import com.dmzs.datawatchclient.auto.R

/** Must match [com.dmzs.datawatchclient.push.NotificationPoster.EXTRA_CAR_SESSION_ID]. */
private const val CAR_SESSION_ID_EXTRA = "dw.car.session_id"

/** Must match [com.dmzs.datawatchclient.push.NotificationPoster.EXTRA_CAR_SESSION_TITLE]. */
private const val CAR_SESSION_TITLE_EXTRA = "dw.car.session_title"

/** Must match [com.dmzs.datawatchclient.push.NotificationPoster.EXTRA_CAR_AUTO_PLAY_LONG]. */
private const val CAR_AUTO_PLAY_LONG_EXTRA = "dw.car.auto_play_long"

/** Must match [com.dmzs.datawatchclient.push.NotificationPoster.EXTRA_CAR_AUTO_VOICE_REPLY]. */
private const val CAR_AUTO_VOICE_REPLY_EXTRA = "dw.car.auto_voice_reply"

/**
 * Public Android Auto Messaging-template service per ADR-0031.
 * Play-compliant: TTS inbound, voice reply, no free-form UI, no terminal.
 *
 * Opens [AutoSummaryScreen] as the root hub — shows session counts,
 * server vitals inline, and last completed task. ActionStrip exposes
 * About (info icon) and Monitor. Sessions and Automata are in the list.
 */
public class DatawatchMessagingService : CarAppService() {
    override fun onCreate() {
        super.onCreate()
        AutoServiceLocator.init(applicationContext)
    }

    override fun createHostValidator(): HostValidator =
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            // Debug builds: permissive so the Desktop Head Unit
            // simulator binds without signing-cert fuss.
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            // Release builds: strict allowlist per ADR-0031 +
            // Google's Auto host-validation rules. The
            // hosts_allowlist array is in publicMain/res and is
            // included via res.srcDirs in auto/build.gradle.kts.
            //
            // Using R.array.hosts_allowlist directly (not getIdentifier)
            // so the reference is verified at compile time and can never
            // silently return 0 if the resource is missing.
            HostValidator.Builder(this)
                .addAllowedHosts(R.array.hosts_allowlist)
                .build()
        }

    override fun onCreateSession(): Session =
        object : Session() {
            override fun onCreateScreen(intent: android.content.Intent) = AutoSummaryScreen(carContext)

            override fun onNewIntent(intent: android.content.Intent) {
                val screenManager = carContext.getCarService(ScreenManager::class.java)
                // §8: always pop to root before pushing to stay within the 5-screen limit.

                // Notification tap from CarAppExtender — navigate directly to the session.
                val sessionId = intent.getStringExtra(CAR_SESSION_ID_EXTRA)
                val sessionTitle = intent.getStringExtra(CAR_SESSION_TITLE_EXTRA)
                val autoPlayLong = intent.getBooleanExtra(CAR_AUTO_PLAY_LONG_EXTRA, false)
                val autoVoiceReply = intent.getBooleanExtra(CAR_AUTO_VOICE_REPLY_EXTRA, false)
                if (sessionId != null) {
                    screenManager.popToRoot()
                    screenManager.push(
                        com.dmzs.datawatchclient.auto.AutoSessionDetailScreen(
                            carContext,
                            sessionId,
                            sessionTitle ?: sessionId,
                            autoPlayLong = autoPlayLong,
                        ),
                    )
                    // "Reply" notification button: go straight to voice input on top of session.
                    if (autoVoiceReply) {
                        screenManager.push(
                            com.dmzs.datawatchclient.auto.VoiceRecordingScreen(
                                carContext,
                                sessionId,
                                sessionTitle ?: sessionId,
                            ),
                        )
                    }
                    return
                }

                // Voice actions from Google Assistant — spoken text arrives via
                // android.speech.RecognizerIntent.EXTRA_RESULTS.
                val voiceResults = intent.getStringArrayListExtra("android.speech.extra.RESULTS")
                val spokenText = voiceResults?.firstOrNull() ?: return
                val cmd = com.dmzs.datawatchclient.auto.voice.parseVoiceCommand(spokenText)
                screenManager.popToRoot()
                screenManager.push(
                    com.dmzs.datawatchclient.auto.voice.VoiceStatusScreen(carContext, cmd),
                )
            }
        }
}
