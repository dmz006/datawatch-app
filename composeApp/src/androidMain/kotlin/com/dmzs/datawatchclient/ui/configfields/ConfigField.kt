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

    /** Shown only while another field's value is in [ShowWhen.values] (PWA conditional blocks). */
    public val showWhen: ShowWhen? get() = null

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
        override val showWhen: ShowWhen? = null,
    ) : ConfigField

    public data class Select(
        override val key: String,
        override val label: String,
        public val options: List<String>,
        /** Display text per option value (PWA `<option>` labels); the value is what is saved. */
        public val optionLabels: Map<String, String> = emptyMap(),
        /** Shown and used for [showWhen] checks while the server value is empty. */
        public val defaultValue: String? = null,
        override val showWhen: ShowWhen? = null,
    ) : ConfigField

    /**
     * Comma-separated list (`acme.domains`): the server returns an array and
     * accepts a comma-separated string in the patch, like the PWA input.
     */
    public data class ListField(
        override val key: String,
        override val label: String,
        public val placeholder: String? = null,
        override val showWhen: ShowWhen? = null,
    ) : ConfigField

    /** Boolean key that is loaded and saved but not drawn — set by another control (e.g. [CertSource]). */
    public data class Hidden(
        override val key: String,
    ) : ConfigField {
        override val label: String get() = ""
    }

    /**
     * PWA `acme_cert_source` (BL397, web v8.62): one selector over
     * `server.tls_auto_generate` + `acme.enabled` — Self-signed / Custom cert
     * path / Let's Encrypt. Pseudo-key, never sent itself; the section must
     * also list both booleans as [Hidden].
     */
    public data class CertSource(
        override val key: String,
        override val label: String,
    ) : ConfigField

    /** Static hint text under a group of fields. */
    public data class Note(
        override val key: String,
        public val text: String,
        override val showWhen: ShowWhen? = null,
    ) : ConfigField {
        override val label: String get() = ""
    }

    /** Live `/api/acme/status` lines + Renew now / Verify (PWA `acmeStatusCard`). */
    public data class AcmeStatus(
        override val key: String,
        override val showWhen: ShowWhen? = null,
    ) : ConfigField {
        override val label: String get() = ""
    }

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

/** Visibility rule: show while the value of [key] is one of [values] (and [and], if set, also holds). */
public data class ShowWhen(
    public val key: String,
    public val values: Set<String>,
    public val and: ShowWhen? = null,
)

/**
 * A named group of fields — maps to one PWA settings-section
 * (e.g. "Session", "Episodic Memory"). Cards render with the
 * section title as header and each field as its own row.
 */
public data class ConfigSection(
    public val id: String,
    public val title: String,
    public val fields: List<ConfigField>,
    /** "?" docs target; null = look [id] up in `DocsLinks` (BL414). */
    public val docsPath: String? = null,
)
