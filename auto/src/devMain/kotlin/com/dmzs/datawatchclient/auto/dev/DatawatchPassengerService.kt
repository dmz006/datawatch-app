package com.dmzs.datawatchclient.auto.dev

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.dmzs.datawatchclient.auto.AutoSummaryScreen

/**
 * Internal-only full-passenger Auto service per ADR-0031.
 * Distributed exclusively via Play Console Internal Testing track on
 * applicationId `com.dmzs.datawatchclient.dev`. Never promoted to public production.
 *
 * Sprint 4 implements the full surface (session list, terminal mirror, stats, voice).
 */
public class DatawatchPassengerService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session =
        object : Session() {
            // BL303-A6.7: promote from placeholder to full mission control screen
            override fun onCreateScreen(intent: android.content.Intent) = AutoSummaryScreen(carContext)
        }
}
