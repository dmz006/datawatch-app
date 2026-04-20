package com.dmzs.datawatchclient.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ConfigView
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.TransportError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Drives the Settings → Daemon config card. Reads `GET /api/config` on
 * init / refresh; the parent server emits masked sensitive values as the
 * literal string `***`. We apply a second client-side mask on top that
 * catches any field whose *name* matches common secret patterns
 * (`token`, `secret`, `key`, `password`, `api_key`, etc.) — protection
 * against a parent-side mask miss leaking a real value through.
 *
 * This is a v0.12 read-only surface; mutate lands in v0.13 behind a
 * structured form per ADR-0019.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class ConfigViewerViewModel : ViewModel() {
    public data class UiState(
        val config: ConfigView = ConfigView(),
        val loading: Boolean = false,
        val banner: String? = null,
        val serverName: String? = null,
        val supported: Boolean = true,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Re-fetch whenever the active profile becomes reachable so a cold-
        // start unreachable-blip auto-recovers instead of sticking.
        ServiceLocator.profileRepository.observeAll()
            .flatMapLatest { profiles ->
                val profile = profiles.firstOrNull { it.enabled } ?: return@flatMapLatest flowOf(null)
                ServiceLocator.transportFor(profile).isReachable
                    .map { reachable -> if (reachable) profile else null }
            }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    public fun refresh() {
        viewModelScope.launch {
            val profile = resolveActiveProfile() ?: return@launch
            _state.value = _state.value.copy(loading = true, serverName = profile.displayName)
            ServiceLocator.transportFor(profile).fetchConfig().fold(
                onSuccess = { cfg ->
                    _state.value =
                        _state.value.copy(
                            config = ConfigView(raw = cfg.raw.mapValues { (_, v) -> maskSecrets(v) }),
                            loading = false,
                            banner = null,
                            supported = true,
                        )
                },
                onFailure = { err ->
                    if (err is TransportError.NotFound) {
                        _state.value =
                            _state.value.copy(
                                loading = false,
                                supported = false,
                                banner =
                                    "This server doesn't expose /api/config. Upgrade datawatch to v4.0.3+.",
                            )
                    } else {
                        _state.value =
                            _state.value.copy(
                                loading = false,
                                banner =
                                    "Couldn't load config — ${err.message ?: err::class.simpleName}",
                            )
                    }
                },
            )
        }
    }

    public fun dismissBanner() {
        _state.value = _state.value.copy(banner = null)
    }

    private suspend fun resolveActiveProfile(): ServerProfile? {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile =
            profiles.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            }
                ?: profiles.firstOrNull { it.enabled }
        if (profile == null) {
            _state.value = UiState(banner = "No enabled server.")
        }
        return profile
    }

    private companion object {
        /**
         * Replaces leaf values under keys that look secretive with `"***"`
         * regardless of what the server sent. Defence in depth against
         * a parent-side mask miss.
         */
        fun maskSecrets(value: JsonElement): JsonElement =
            when (value) {
                is JsonObject ->
                    buildJsonObject {
                        value.forEach { (k, v) ->
                            if (isSecretKey(k) && v is JsonPrimitive && v.isString) {
                                put(k, JsonPrimitive("***"))
                            } else {
                                put(k, maskSecrets(v))
                            }
                        }
                    }
                else ->
                    when {
                        // Arrays pass through — top-level array fields aren't
                        // a secret-leak vector today.
                        runCatching { value.jsonArray }.isSuccess -> value
                        runCatching { value.jsonObject }.isSuccess -> value
                        runCatching { value.jsonPrimitive }.isSuccess -> value
                        else -> value
                    }
            }

        private val SECRET_KEY_PATTERN =
            Regex(
                "(?i)(token|secret|api[_-]?key|password|passphrase|bearer|webhook[_-]?url|" +
                    "signing[_-]?key|private[_-]?key|access[_-]?key)",
            )

        fun isSecretKey(key: String): Boolean = SECRET_KEY_PATTERN.containsMatchIn(key)
    }
}
