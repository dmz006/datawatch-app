package com.dmzs.datawatchclient.ui.configfields

/**
 * One editable row in a structured config form. Mirrors PWA's
 * `{key, label, type, options?, placeholder?}` field definitions in
 * `COMMS_CONFIG_FIELDS` / `LLM_CONFIG_FIELDS` /
 * `GENERAL_CONFIG_FIELDS` (app.js lines 3532–3716).
 *
 * ADR-0019: mobile only renders the field types it understands. Raw
 * YAML editing stays off the phone; all edits go through
 * [com.dmzs.datawatchclient.transport.TransportClient.writeConfig]
 * as a merged-document PUT.
 */
public sealed interface ConfigField {
    public val key: String
    public val label: String

    public data class Toggle(
        override val key: String,
        override val label: String,
    ) : ConfigField

    public data class NumberField(
        override val key: String,
        override val label: String,
        public val placeholder: String? = null,
    ) : ConfigField

    public data class TextField(
        override val key: String,
        override val label: String,
        public val placeholder: String? = null,
        public val password: Boolean = false,
    ) : ConfigField

    public data class Select(
        override val key: String,
        override val label: String,
        public val options: List<String>,
    ) : ConfigField

    /**
     * Populated dynamically from `GET /api/interfaces`. The
     * ConfigFieldsPanel fetches on open and offers the interface
     * names (`eth0`, `wlan0`, …) plus explicit `0.0.0.0` and
     * `127.0.0.1` entries matching PWA.
     */
    public data class InterfaceSelect(
        override val key: String,
        override val label: String,
    ) : ConfigField

    /**
     * Populated dynamically from `GET /api/backends` — matches
     * PWA's `llm_select`. Used by `session.llm_backend` default
     * picker.
     */
    public data class LlmSelect(
        override val key: String,
        override val label: String,
    ) : ConfigField
}

/**
 * A named group of fields — maps to one PWA settings-section
 * (e.g. "Session", "Episodic Memory"). Cards render with the
 * section title as header and each field as its own row.
 */
public data class ConfigSection(
    public val id: String,
    public val title: String,
    public val fields: List<ConfigField>,
)
