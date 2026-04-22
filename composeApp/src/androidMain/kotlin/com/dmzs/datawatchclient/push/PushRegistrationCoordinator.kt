package com.dmzs.datawatchclient.push

import android.content.Context
import com.dmzs.datawatchclient.Version
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.DeviceKind
import com.dmzs.datawatchclient.transport.DevicePlatform
import kotlinx.coroutines.flow.first

/**
 * Reconciles per-profile push registrations against the parent server.
 * Idempotent: if a profile already has a `device_id` stored locally,
 * registration is skipped unless [force] is set.
 *
 * **FCM removed in v0.33.17** (B6 closed). datawatch is a self-hosted
 * / privacy-minded stack; depending on Google FCM for push delivery
 * pulled in Firebase as a required dependency and required an
 * operator-provisioned `google-services.json`. Ntfy runs over the
 * user's own ntfy server (or a public topic of their choice) and is
 * the single push path now.
 *
 * Registration strategy:
 * 1. Get or generate a per-profile ntfy topic.
 * 2. POST /api/devices/register with the topic + metadata.
 * 3. Persist the returned `device_id` keyed by profile id.
 *
 * Errors are swallowed (logged) — push registration failure should
 * never block the user from interacting with sessions over REST.
 */
public class PushRegistrationCoordinator(private val context: Context) {
    public suspend fun registerAll(force: Boolean = false) {
        val profiles =
            ServiceLocator.profileRepository.observeAll().first()
                .filter { it.enabled }
        val store = ServiceLocator.pushTokenStore
        for (profile in profiles) {
            if (!force && store.deviceIdFor(profile.id) != null) continue
            registerOne(profile, fcmToken = null)
        }
    }

    public suspend fun registerOne(
        profile: ServerProfile,
        fcmToken: String?,
    ) {
        val store = ServiceLocator.pushTokenStore
        val transport = ServiceLocator.transportFor(profile)

        val (kind, deliveryToken) =
            if (fcmToken != null) {
                DeviceKind.Fcm to fcmToken
            } else {
                // ntfy fallback — synthesise a topic per profile so different profiles
                // don't share a delivery channel.
                val topic =
                    store.ntfyTopicFor(profile.id) ?: ntfyTopicFor(profile.id).also {
                        store.setNtfyTopicFor(profile.id, it)
                    }
                DeviceKind.Ntfy to topic
            }

        transport.registerDevice(
            deviceToken = deliveryToken,
            kind = kind,
            appVersion = Version.VERSION,
            platform = DevicePlatform.Android,
            profileHint = profile.displayName,
        ).fold(
            onSuccess = { deviceId -> store.setDeviceIdFor(profile.id, deviceId) },
            onFailure = { err ->
                android.util.Log.w(
                    "PushRegistration",
                    "registerDevice failed for ${profile.id}: ${err.message}",
                )
            },
        )
    }

    public suspend fun unregister(profile: ServerProfile) {
        val store = ServiceLocator.pushTokenStore
        val deviceId = store.deviceIdFor(profile.id) ?: return
        ServiceLocator.transportFor(profile).unregisterDevice(deviceId)
        store.clearProfile(profile.id)
    }

    private fun ntfyTopicFor(profileId: String): String {
        val safeId = profileId.replace(Regex("[^a-zA-Z0-9_-]"), "")
        val seconds = System.currentTimeMillis() / 1000
        return "dw-$safeId-$seconds"
    }
}
