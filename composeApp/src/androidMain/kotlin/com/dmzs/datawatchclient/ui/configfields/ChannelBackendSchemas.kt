package com.dmzs.datawatchclient.ui.configfields

import com.dmzs.datawatchclient.ui.configfields.ConfigField.NumberField
import com.dmzs.datawatchclient.ui.configfields.ConfigField.Select
import com.dmzs.datawatchclient.ui.configfields.ConfigField.TextField
import com.dmzs.datawatchclient.ui.configfields.ConfigField.Toggle

/**
 * Per-backend field schemas for the messaging-channel config
 * dialog. Two address spaces are supported:
 *
 *  - **Per-channel instance**: `channels.<id>.<field>` — used when
 *    the server has multiple instances of the same type (e.g. two
 *    ntfy topics). Editable via [instanceSectionFor].
 *  - **Global backend**: `messaging.<type>.<field>` — used for the
 *    "Messaging Backends" sweep that surfaces configured backends
 *    even when `/api/channels` doesn't return an instance row (the
 *    2026-04-22 Signal case). Editable via [globalSectionFor].
 *
 * When the parent daemon adds a field, mirror it here.
 */
public object ChannelBackendSchemas {
    public val KnownTypes: List<String> =
        listOf(
            "signal", "telegram", "discord", "slack", "matrix",
            "ntfy", "email", "twilio", "webhook", "github_webhook",
        )

    private fun enabled(prefix: String): Toggle =
        Toggle("$prefix.enabled", "Enabled")

    /**
     * Field list for channel [type]. Prefix is applied by caller
     * (either `channels.<id>` or `messaging.<type>`). This keeps the
     * schema reusable across per-instance and global address spaces.
     */
    public fun fieldsFor(prefix: String, type: String): List<ConfigField> =
        when (type.lowercase()) {
            "signal" ->
                listOf(
                    enabled(prefix),
                    TextField("$prefix.service_url", "signal-cli REST URL", placeholder = "http://signal-cli-rest-api:8080"),
                    TextField("$prefix.phone_number", "Registered phone (E.164)", placeholder = "+15551234567"),
                    TextField("$prefix.recipient", "Default recipient (phone or group id)"),
                )
            "telegram" ->
                listOf(
                    enabled(prefix),
                    TextField("$prefix.bot_token", "Bot token", password = true),
                    TextField("$prefix.chat_id", "Chat id"),
                    TextField("$prefix.parse_mode", "Parse mode (MarkdownV2 / HTML)", placeholder = "MarkdownV2"),
                )
            "discord" ->
                listOf(
                    enabled(prefix),
                    TextField("$prefix.webhook_url", "Webhook URL", password = true),
                    TextField("$prefix.username", "Display username (optional)"),
                    TextField("$prefix.avatar_url", "Avatar URL (optional)"),
                )
            "slack" ->
                listOf(
                    enabled(prefix),
                    TextField("$prefix.webhook_url", "Incoming webhook URL", password = true),
                    TextField("$prefix.bot_token", "Bot token (optional)", password = true),
                    TextField("$prefix.channel", "Channel (optional override)"),
                )
            "matrix" ->
                listOf(
                    enabled(prefix),
                    TextField("$prefix.homeserver_url", "Homeserver URL", placeholder = "https://matrix.org"),
                    TextField("$prefix.access_token", "Access token", password = true),
                    TextField("$prefix.room_id", "Room id", placeholder = "!abc:matrix.org"),
                )
            "ntfy" ->
                listOf(
                    enabled(prefix),
                    TextField("$prefix.server_url", "Server URL", placeholder = "https://ntfy.sh"),
                    TextField("$prefix.topic", "Topic"),
                    TextField("$prefix.auth_token", "Auth token (optional)", password = true),
                    Select(
                        "$prefix.priority",
                        "Priority",
                        listOf("min", "low", "default", "high", "urgent"),
                    ),
                )
            "email" ->
                listOf(
                    enabled(prefix),
                    TextField("$prefix.smtp_host", "SMTP host", placeholder = "smtp.example.com"),
                    NumberField("$prefix.smtp_port", "SMTP port", placeholder = "587"),
                    TextField("$prefix.smtp_user", "SMTP user"),
                    TextField("$prefix.smtp_password", "SMTP password", password = true),
                    Toggle("$prefix.use_tls", "Use STARTTLS"),
                    TextField("$prefix.from_address", "From address"),
                    TextField("$prefix.to_address", "To address"),
                )
            "twilio" ->
                listOf(
                    enabled(prefix),
                    TextField("$prefix.account_sid", "Account SID"),
                    TextField("$prefix.auth_token", "Auth token", password = true),
                    TextField("$prefix.from_number", "From number (E.164)", placeholder = "+15551234567"),
                    TextField("$prefix.to_number", "To number (E.164)"),
                )
            "webhook" ->
                listOf(
                    enabled(prefix),
                    TextField("$prefix.url", "Target URL"),
                    Select(
                        "$prefix.method",
                        "HTTP method",
                        listOf("POST", "PUT", "PATCH", "GET"),
                    ),
                    TextField("$prefix.auth_header", "Authorization header (optional)", password = true),
                    TextField("$prefix.body_template", "Body template (optional, Go template)"),
                )
            "github_webhook" ->
                listOf(
                    enabled(prefix),
                    TextField("$prefix.webhook_url", "GitHub webhook URL"),
                    TextField("$prefix.secret", "Secret", password = true),
                )
            else ->
                // Unknown type — expose the enable toggle and a single
                // free-form field so the user isn't locked out.
                listOf(
                    enabled(prefix),
                    TextField("$prefix.config", "Config blob (JSON)"),
                )
        }

    /**
     * Per-channel-instance section: `channels.<id>.*`. Used when a
     * row exists in `/api/channels`.
     */
    public fun instanceSectionFor(channelId: String, type: String): ConfigSection =
        ConfigSection(
            id = "cc_channel_$channelId",
            title = "$channelId · $type",
            fields = fieldsFor("channels.$channelId", type),
        )

    /**
     * Global-per-type section: `messaging.<type>.*`. Surfaces the
     * configured backend even when no channel instance exists yet
     * — fixes the 2026-04-22 "signal configured but not in list"
     * gap.
     */
    public fun globalSectionFor(type: String): ConfigSection =
        ConfigSection(
            id = "cc_global_$type",
            title = type.replaceFirstChar { it.titlecase() },
            fields = fieldsFor("messaging.$type", type),
        )
}
