package com.dmzs.datawatchclient.storage

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dmzs.datawatchclient.db.DatawatchDb
import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * Persisted access to user-configured datawatch server profiles. Tokens are NEVER
 * stored here — only a keystore alias reference — per security-model.md.
 */
public class ServerProfileRepository(
    private val db: DatawatchDb,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock = Clock.System,
) {
    public fun observeAll(): Flow<List<ServerProfile>> =
        db.profileQueries.selectAllProfiles()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    public suspend fun upsert(profile: ServerProfile) {
        db.profileQueries.insertProfile(
            id = profile.id,
            display_name = profile.displayName,
            base_url = profile.baseUrl,
            bearer_token_ref = profile.bearerTokenRef,
            trust_anchor_sha256 = profile.trustAnchorSha256,
            reachability_profile_id = profile.reachabilityProfileId,
            enabled = if (profile.enabled) 1L else 0L,
            created_ts = profile.createdTs,
            last_seen_ts = profile.lastSeenTs,
        )
    }

    public suspend fun delete(id: String) {
        db.profileQueries.deleteProfile(id)
    }

    public suspend fun touchLastSeen(id: String) {
        // Read-modify-write; single-user v1 means no contention.
        val existing = db.profileQueries.selectAllProfiles().executeAsList()
            .firstOrNull { it.id == id } ?: return
        upsert(existing.toDomain().copy(lastSeenTs = clock.now().toEpochMilliseconds()))
    }

    // -- SQLDelight row -> domain mapper --

    private fun com.dmzs.datawatchclient.db.Server_profile.toDomain(): ServerProfile =
        ServerProfile(
            id = id,
            displayName = display_name,
            baseUrl = base_url,
            bearerTokenRef = bearer_token_ref,
            trustAnchorSha256 = trust_anchor_sha256,
            reachabilityProfileId = reachability_profile_id,
            enabled = enabled != 0L,
            createdTs = created_ts,
            lastSeenTs = last_seen_ts,
        )
}
