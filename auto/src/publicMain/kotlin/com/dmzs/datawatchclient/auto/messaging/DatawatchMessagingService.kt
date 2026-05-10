package com.dmzs.datawatchclient.auto.messaging

import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.dmzs.datawatchclient.auto.AutoMonitorScreen
import com.dmzs.datawatchclient.auto.AutoServiceLocator

/**
 * Public Android Auto Messaging-template service per ADR-0031.
 * Play-compliant: TTS inbound, voice reply, no free-form UI, no terminal.
 *
 * Opens [AutoMonitorScreen] — default Monitor tab per user request
 * 2026-04-22, showing live server vitals (CPU, memory, disk, VRAM,
 * sessions, uptime). ActionStrip on that screen exposes Sessions,
 * Server picker and About as secondary screens.
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
            // hosts_allowlist array ships in publicMain/res.
            HostValidator.Builder(this)
                .addAllowedHosts(
                    resources.getIdentifier(
                        "hosts_allowlist",
                        "array",
                        packageName,
                    ),
                )
                .build()
        }

    override fun onCreateSession(): Session =
        object : Session() {
            override fun onCreateScreen(intent: android.content.Intent) = AutoMonitorScreen(carContext)

            override fun onNewIntent(intent: android.content.Intent) {
                // Handle voice actions — Google Assistant sends the spoken text via
                // android.speech.RecognizerIntent.EXTRA_RESULTS
                val voiceResults = intent.getStringArrayListExtra("android.speech.extra.RESULTS")
                val spokenText = voiceResults?.firstOrNull() ?: return
                val cmd = com.dmzs.datawatchclient.auto.voice.parseVoiceCommand(spokenText)
                carContext.getCarService(ScreenManager::class.java).push(
                    com.dmzs.datawatchclient.auto.voice.VoiceStatusScreen(carContext, cmd),
                )
            }
        }
}
