package com.dmzs.datawatchclient.auto.messaging

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.dmzs.datawatchclient.auto.AutoSummaryScreen

/**
 * Public Android Auto Messaging-template service per ADR-0031.
 * Play-compliant: TTS inbound, voice reply, no free-form UI, no terminal.
 *
 * Opens [AutoSummaryScreen] — live Running / Waiting / Total
 * counts polled every 15 s. Tapping "Waiting input" pushes
 * [com.dmzs.datawatchclient.auto.WaitingSessionsScreen] → per-
 * session reply with Yes / No / Continue / Stop quick actions.
 */
public class DatawatchMessagingService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR // TODO: strict allowlist pre-Play-submit

    override fun onCreateSession(): Session =
        object : Session() {
            override fun onCreateScreen(intent: android.content.Intent) = AutoSummaryScreen(carContext)
        }
}
