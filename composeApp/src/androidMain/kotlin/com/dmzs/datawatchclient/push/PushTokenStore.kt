package com.dmzs.datawatchclient.push

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists push registration state per server profile.
 *
 * - The FCM token is shared across all profiles (one token per app install).
 * - Each profile gets its own server-assigned `device_id` after a successful
 *   POST /api/devices/register, so we can DELETE /api/devices/{id} on profile
 *   removal or token rotation.
 * - ntfy fallback profiles store a topic name + a server-assigned device_id.
 *
 * Plain SharedPreferences — these values are non-secret identifiers. The bearer
 * token used to call the registration endpoint stays in TokenVault.
 */
public class PushTokenStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    public fun fcmToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)

    public fun setFcmToken(token: String?) {
        prefs.edit().apply {
            if (token == null) remove(KEY_FCM_TOKEN) else putString(KEY_FCM_TOKEN, token)
        }.apply()
    }

    public fun deviceIdFor(profileId: String): String? = prefs.getString("$KEY_DEVICE_ID_PREFIX$profileId", null)

    public fun setDeviceIdFor(
        profileId: String,
        deviceId: String?,
    ) {
        prefs.edit().apply {
            val k = "$KEY_DEVICE_ID_PREFIX$profileId"
            if (deviceId == null) remove(k) else putString(k, deviceId)
        }.apply()
    }

    public fun ntfyTopicFor(profileId: String): String? = prefs.getString("$KEY_NTFY_TOPIC_PREFIX$profileId", null)

    public fun setNtfyTopicFor(
        profileId: String,
        topic: String?,
    ) {
        prefs.edit().apply {
            val k = "$KEY_NTFY_TOPIC_PREFIX$profileId"
            if (topic == null) remove(k) else putString(k, topic)
        }.apply()
    }

    public fun ntfyServerFor(profileId: String): String? = prefs.getString("$KEY_NTFY_SERVER_PREFIX$profileId", null)

    public fun setNtfyServerFor(
        profileId: String,
        server: String?,
    ) {
        prefs.edit().apply {
            val k = "$KEY_NTFY_SERVER_PREFIX$profileId"
            if (server == null) remove(k) else putString(k, server)
        }.apply()
    }

    public fun clearProfile(profileId: String) {
        prefs.edit().apply {
            remove("$KEY_DEVICE_ID_PREFIX$profileId")
            remove("$KEY_NTFY_TOPIC_PREFIX$profileId")
            remove("$KEY_NTFY_SERVER_PREFIX$profileId")
        }.apply()
    }

    public companion object {
        public const val PREFS_FILE: String = "dw.push.v1"
        public const val KEY_FCM_TOKEN: String = "fcm_token"
        public const val KEY_DEVICE_ID_PREFIX: String = "device_id."
        public const val KEY_NTFY_TOPIC_PREFIX: String = "ntfy_topic."
        public const val KEY_NTFY_SERVER_PREFIX: String = "ntfy_server."
        public const val DEFAULT_NTFY_SERVER: String = "https://ntfy.sh"
    }
}
