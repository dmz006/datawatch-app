package com.dmzs.datawatchclient.domain

import kotlinx.serialization.Serializable

/**
 * Named command snippet persisted on the server via
 * `GET/POST/DELETE /api/commands`. Users save frequently-used task strings
 * under a short name and recall them from the New Session form.
 *
 * Two fields today — no categories, no tags. BL20 absorbs into v0.12 with
 * this minimal shape; richer modelling tracked for v0.13+.
 */
@Serializable
public data class SavedCommand(
    val name: String,
    val command: String,
)
