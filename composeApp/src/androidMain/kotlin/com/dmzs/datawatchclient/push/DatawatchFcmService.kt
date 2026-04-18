package com.dmzs.datawatchclient.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM wake handler per ADR-0012.
 * Sprint 2 implementation parses the minimal dumb-ping payload (profile_id / event /
 * session_hint / ts), fetches content over the bearer-authenticated channel, then
 * posts a MessagingStyle notification.
 *
 * Pre-MVP scaffold: logs receipt only. No session content ever lands here.
 */
public class DatawatchFcmService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        // Sprint 2: dispatch to shared domain PushBackend.incoming() flow.
    }

    override fun onNewToken(token: String) {
        // Sprint 2: persist token + POST /api/devices/register against each enabled profile.
    }
}
