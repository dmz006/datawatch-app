package com.dmzs.datawatchclient.domain

import kotlinx.serialization.Serializable

/**
 * Persistent representation of a user-owned datawatch server.
 * `bearerTokenRef` is ALWAYS the Android Keystore alias — the plaintext token
 * never travels through this type (ADR-0011, security-model.md key hierarchy).
 */
@Serializable
public data class ServerProfile(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val bearerTokenRef: String,
    val trustAnchorSha256: String? = null,
    val reachabilityProfileId: String,
    val enabled: Boolean = true,
    val createdTs: Long,
    val lastSeenTs: Long? = null,
)
