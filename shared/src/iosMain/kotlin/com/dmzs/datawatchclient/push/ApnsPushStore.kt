package com.dmzs.datawatchclient.push

import platform.Foundation.NSUserDefaults

/**
 * Persists APNs push registration state per server profile.
 *
 * - The APNs device token is shared across all profiles (one token per app install).
 *   It is kept IN MEMORY only: Apple — "Never cache device tokens in local storage";
 *   the app re-registers on every launch and APNs returns the current token.
 * - Each profile gets its own server-assigned `device_id` after a successful
 *   POST /api/devices/register, used to DELETE /api/devices/{id} on profile
 *   removal or token rotation.
 *
 * Uses NSUserDefaults — these values are non-secret identifiers. Bearer tokens
 * stay in the iOS Keychain via IosTokenStore.
 */
public class ApnsPushStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    init {
        // Earlier builds persisted the token; drop it (must not be cached).
        defaults.removeObjectForKey(KEY_APNS_TOKEN)
    }

    public fun apnsToken(): String? = currentToken

    public fun setApnsToken(token: String?) {
        currentToken = token
    }

    public fun deviceIdFor(profileId: String): String? =
        defaults.stringForKey("$KEY_DEVICE_ID_PREFIX$profileId")

    public fun setDeviceIdFor(profileId: String, deviceId: String?) {
        val key = "$KEY_DEVICE_ID_PREFIX$profileId"
        if (deviceId == null) defaults.removeObjectForKey(key)
        else defaults.setObject(deviceId, key)
    }

    public fun clearProfile(profileId: String) {
        defaults.removeObjectForKey("$KEY_DEVICE_ID_PREFIX$profileId")
    }

    private companion object {
        /** This launch's token from APNs (never persisted). */
        var currentToken: String? = null

        const val KEY_APNS_TOKEN = "dw.push.apns_token"
        const val KEY_DEVICE_ID_PREFIX = "dw.push.device_id."
    }
}
