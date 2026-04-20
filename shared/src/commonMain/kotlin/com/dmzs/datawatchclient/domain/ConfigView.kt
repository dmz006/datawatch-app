package com.dmzs.datawatchclient.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Permissive wrapper around the masked daemon config returned by
 * `GET /api/config`. Parent server's config schema is large and drifts
 * between minor releases; keep the values as raw [JsonElement] and let
 * the viewer render them at render-time so we don't break on new keys.
 *
 * Mobile client never writes this surface — `PUT /api/config` is a v0.13
 * scope item behind a structured form per ADR-0019.
 */
@Serializable
public data class ConfigView(
    val raw: Map<String, JsonElement> = emptyMap(),
)
