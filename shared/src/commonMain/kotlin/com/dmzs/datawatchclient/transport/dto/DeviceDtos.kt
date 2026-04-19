package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body for POST /api/devices/register (parent issue #1, datawatch v3.0.0).
 *
 * Wire shape (verified against `internal/server/devices.go` in dmz006/datawatch):
 * `{ "device_token", "kind"=fcm|ntfy, "app_version", "platform"=android|ios, "profile_hint" }`
 */
@Serializable
public data class DeviceRegisterDto(
    @SerialName("device_token") val deviceToken: String,
    val kind: String,
    @SerialName("app_version") val appVersion: String,
    val platform: String,
    @SerialName("profile_hint") val profileHint: String,
)

/** Response: `{ "device_id": "<uuid>" }`. */
@Serializable
public data class DeviceRegisterResponseDto(
    @SerialName("device_id") val deviceId: String,
)
