package com.dmzs.datawatchclient.domain

import kotlinx.serialization.Serializable

/**
 * Wire-shape-independent view of GET /api/info, used by the About card and
 * connection-status UI. Mirrors the parent's response with nullable fields
 * so a degraded server (missing backend configured, stripped-down build) does
 * not block rendering.
 */
@Serializable
public data class ServerInfo(
    val hostname: String,
    /** Daemon version string (e.g. "3.0.0"). */
    val version: String,
    /** Currently-active LLM backend name, or null if none configured. */
    val llmBackend: String? = null,
    /** Currently-active messaging backend name, or null if none configured. */
    val messagingBackend: String? = null,
    val sessionCount: Int = 0,
    val serverHost: String? = null,
    val serverPort: Int? = null,
)
