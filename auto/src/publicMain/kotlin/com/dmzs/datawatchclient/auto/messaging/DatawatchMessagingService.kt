package com.dmzs.datawatchclient.auto.messaging

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.dmzs.datawatchclient.auto.PreMvpPlaceholderScreen

/**
 * Public Android Auto Messaging-template service per ADR-0031.
 * Play-compliant: TTS inbound, voice reply, no free-form UI, no terminal.
 *
 * Sprint 4 implements the session + message list. Pre-MVP scaffold returns an
 * empty allowlist session for build validation only.
 */
public class DatawatchMessagingService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR // Sprint 4: replace with strict allowlist

    override fun onCreateSession(): Session =
        object : Session() {
            override fun onCreateScreen(intent: android.content.Intent) = PreMvpPlaceholderScreen(carContext)
        }
}
