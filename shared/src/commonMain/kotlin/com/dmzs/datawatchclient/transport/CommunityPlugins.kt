package com.dmzs.datawatchclient.transport

/**
 * Community Plugins card rules shared by Android and iOS, matching the web UI
 * (datawatch v8.66 GH#191 `loadCommunityPluginsPanel`): registries come from
 * Skill Registries; default to "community" when present, else the first; show a
 * picker only when there is more than one; a browse error saying "not connected"
 * becomes a Connect action instead of an error.
 */
public object CommunityPlugins {
    public const val PREFERRED_REGISTRY: String = "community"

    /** Registry to show: [current] if still listed, else "community", else the first. */
    public fun pickRegistry(
        names: List<String>,
        current: String?,
    ): String? =
        when {
            names.isEmpty() -> null
            current != null && current in names -> current
            PREFERRED_REGISTRY in names -> PREFERRED_REGISTRY
            else -> names.first()
        }

    /** True when a browse failure means the registry just needs connecting. */
    public fun isNotConnected(error: String?): Boolean = error.orEmpty().contains("not connected", ignoreCase = true)

    /** Row subtitle: manifest description, else "v<version>", else empty. */
    public fun subtitle(
        description: String,
        version: String,
    ): String = description.ifBlank { if (version.isBlank()) "" else "v$version" }
}
