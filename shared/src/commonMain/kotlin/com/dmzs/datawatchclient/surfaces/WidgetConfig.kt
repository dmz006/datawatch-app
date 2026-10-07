package com.dmzs.datawatchclient.surfaces

import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One enabled server as the iOS widget extension sees it. Carries the Keychain
 * alias of the bearer token ([bearerTokenRef]), never the token itself — the
 * widget reads the token from the shared Keychain access group.
 */
@Serializable
public data class WidgetServer(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val bearerTokenRef: String = "",
    val trustAnchorSha256: String? = null,
) {
    /** Minimal [ServerProfile] so the widget can reuse the shared REST transport. */
    public fun toProfile(): ServerProfile =
        ServerProfile(
            id = id,
            displayName = displayName,
            baseUrl = baseUrl,
            bearerTokenRef = bearerTokenRef,
            trustAnchorSha256 = trustAnchorSha256,
            reachabilityProfileId = "",
            enabled = true,
            createdTs = 0,
        )
}

/**
 * What the app shares with its home-screen widgets: the enabled servers (in the
 * app's order) and the active server id. Mirrors what Android's widgets read
 * from `ServerProfileRepository` + `ActiveServerStore`.
 */
@Serializable
public data class WidgetConfig(
    val activeId: String? = null,
    val servers: List<WidgetServer> = emptyList(),
) {
    /**
     * Server the widgets show — Android `SessionsWidget.refresh`: the active
     * server when it is enabled, else the first enabled one.
     */
    public fun pick(): WidgetServer? = servers.firstOrNull { it.id == activeId } ?: servers.firstOrNull()

    /**
     * Android `WidgetActions.cycleActiveServer`: advance to the next enabled
     * server, wrapping at the end; no-op with one server or none.
     */
    public fun cycled(): WidgetConfig {
        if (servers.size <= 1) return this
        val idx = servers.indexOfFirst { it.id == activeId }
        return copy(activeId = servers[(idx + 1).mod(servers.size)].id)
    }

    public fun encode(): String = json.encodeToString(serializer(), this)

    public companion object {
        private val json = Json { ignoreUnknownKeys = true }

        public fun decode(text: String?): WidgetConfig? =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }

        /** Build from the app's profile list; disabled profiles are left out. */
        public fun from(
            profiles: List<ServerProfile>,
            activeId: String?,
        ): WidgetConfig =
            WidgetConfig(
                activeId = activeId,
                servers =
                    profiles.filter { it.enabled }.map {
                        WidgetServer(
                            id = it.id,
                            displayName = it.displayName,
                            baseUrl = it.baseUrl,
                            bearerTokenRef = it.bearerTokenRef,
                            trustAnchorSha256 = it.trustAnchorSha256,
                        )
                    },
            )
    }
}
