package com.dmzs.datawatchclient.push

import android.content.Context
import com.dmzs.datawatchclient.Version
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.DeviceKind
import com.dmzs.datawatchclient.transport.DevicePlatform
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.first

/**
 * Reconciles per-profile push registrations against the parent server. Idempotent:
 * if a profile already has a `device_id` stored locally, registration is skipped
 * unless [force] is set.
 *
 * Registration strategy:
 * 1. Resolve a delivery token: prefer the cached FCM token; if FCM is not
 *    available (no Firebase config / failure), fall back to a per-profile ntfy
 *    topic generated from the profile id.
 * 2. POST /api/devices/register with the token + metadata.
 * 3. Persist the returned `device_id` keyed by profile id.
 *
 * Errors are swallowed (logged) — push registration failure should never block
 * the user from interacting with sessions over REST.
 */
public class PushRegistrationCoordinator(private val context: Context) {

    public suspend fun registerAll(force: Boolean = false) {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
            .filter { it.enabled }
        val store = ServiceLocator.pushTokenStore
        val token = ensureFcmToken() ?: store.fcmToken()
        for (profile in profiles) {
            if (!force && store.deviceIdFor(profile.id) != null) continue
            registerOne(profile, token)
        }
    }

    public suspend fun registerOne(profile: ServerProfile, fcmToken: String?) {
        val store = ServiceLocator.pushTokenStore
        val transport = ServiceLocator.transportFor(profile)

        val (kind, deliveryToken) = if (fcmToken != null) {
            DeviceKind.Fcm to fcmToken
        } else {
            // ntfy fallback — synthesise a topic per profile so different profiles
            // don't share a delivery channel.
            val topic = store.ntfyTopicFor(profile.id) ?: ntfyTopicFor(profile.id).also {
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

    private fun ensureFcmToken(): String? {
        // FirebaseMessaging.getInstance().token returns a Task; we block briefly
        // on the IO dispatcher (caller) — typical resolution is sub-100 ms when
        // a token already exists. If Firebase isn't initialised (no
        // google-services.json), this throws — we catch and return null so the
        // ntfy fallback path takes over.
        return try {
            val task = FirebaseMessaging.getInstance().token
            val token = Tasks.await(task)
            ServiceLocator.pushTokenStore.setFcmToken(token)
            token
        } catch (e: Throwable) {
            android.util.Log.i("PushRegistration", "FCM unavailable, will use ntfy: ${e.message}")
            null
        }
    }

    private fun ntfyTopicFor(profileId: String): String {
        val safeId = profileId.replace(Regex("[^a-zA-Z0-9_-]"), "")
        val seconds = System.currentTimeMillis() / 1000
        return "dw-$safeId-$seconds"
    }
}
