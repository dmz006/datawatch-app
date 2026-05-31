package com.dmzs.datawatchclient.auto.messaging

import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.dmzs.datawatchclient.auto.AutoServiceLocator
import com.dmzs.datawatchclient.auto.AutoSummaryScreen
import com.dmzs.datawatchclient.auto.R

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
                // Handle notification voice-reply tap — fires when the user taps
                // "Voice Reply" in the Auto notification shade (pushed from
                // NotificationPoster.buildCarAppExtender). Constants kept as
                // inline strings because the auto module does not depend on
                // composeApp (see auto/build.gradle.kts).
                if (intent.action == "com.dmzs.datawatchclient.auto.ACTION_VOICE_REPLY") {
                    val sessionId = intent.getStringExtra("session_id") ?: return
                    val sessionName = intent.getStringExtra("session_name") ?: sessionId
                    val screenManager = carContext.getCarService(ScreenManager::class.java)
                    screenManager.popToRoot()
                    screenManager.push(
                        com.dmzs.datawatchclient.auto.VoiceRecordingScreen(
                            carContext,
                            sessionId,
                            sessionName,
                        )
                    )
                    return
                }

                // Handle voice actions — Google Assistant sends the spoken text via
                // android.speech.RecognizerIntent.EXTRA_RESULTS
                val voiceResults = intent.getStringArrayListExtra("android.speech.extra.RESULTS")
                val spokenText = voiceResults?.firstOrNull() ?: return
                val cmd = com.dmzs.datawatchclient.auto.voice.parseVoiceCommand(spokenText)
                val screenManager = carContext.getCarService(ScreenManager::class.java)
                // §8: Car App Library enforces a max screen stack depth of 5. Voice commands
                // arrive independently of the user's navigation state, so pop to root first to
                // guarantee we are at depth 1 before pushing VoiceStatusScreen (depth 2).
                // Without this, a deep path (Summary→Monitor→SessionList→SessionDetail)
                // would push to depth 6 and trigger an IllegalStateException from the host.
                screenManager.popToRoot()
                screenManager.push(
                    com.dmzs.datawatchclient.auto.voice.VoiceStatusScreen(carContext, cmd),
                )
            }
        }
}
