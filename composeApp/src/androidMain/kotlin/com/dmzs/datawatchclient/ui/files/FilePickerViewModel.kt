package com.dmzs.datawatchclient.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.FileEntry
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.TransportError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Backs [FilePickerDialog]. Browses directories on the active datawatch
 * server via `GET /api/files?path=`. Entries are sorted dirs-first then
 * alphabetical so tapping always lands somewhere predictable.
 *
 * Null [UiState.path] means "default root" — we let the server pick
 * (typically the user's home). On every browse, the server returns the
 * absolute path it resolved, so breadcrumbs can be built from the [FileList]
 * without the client doing its own path arithmetic.
 */
public class FilePickerViewModel : ViewModel() {
    public data class UiState(
        val path: String? = null,
        val entries: List<FileEntry> = emptyList(),
        val loading: Boolean = false,
        val banner: String? = null,
        val serverName: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        browse(null)
    }

    public fun browse(path: String?) {
        viewModelScope.launch {
            val profile = resolveActiveProfile() ?: return@launch
            _state.value =
                _state.value.copy(
                    loading = true,
                    banner = null,
                    serverName = profile.displayName,
                    path = path,
                )
            ServiceLocator.transportFor(profile).browseFiles(path).fold(
                onSuccess = { list ->
                    _state.value =
                        _state.value.copy(
                            path = list.path,
                            entries =
                                list.entries.sortedWith(
                                    // Dirs before files; alphabetical within each.
                                    compareByDescending<FileEntry> { it.isDirectory }
                                        .thenBy { it.name.lowercase() },
                                ),
                            loading = false,
                        )
                },
                onFailure = { err ->
                    val msg =
                        when (err) {
                            is TransportError.NotFound ->
                                "Server doesn't expose /api/files. Upgrade datawatch to v4.0.3+."
                            else -> err.message ?: err::class.simpleName ?: "unknown error"
                        }
                    _state.value =
                        _state.value.copy(
                            loading = false,
                            banner = "Browse failed — $msg",
                        )
                },
            )
        }
    }

    public fun goUp() {
        val current = _state.value.path ?: return
        // Strip trailing slash, then drop the last segment. "/" → null (root);
        // "/home/user" → "/home".
        val trimmed = current.trimEnd('/')
        val parent = trimmed.substringBeforeLast('/', missingDelimiterValue = "")
        browse(parent.ifBlank { "/" })
    }

    public fun dismissBanner() {
        _state.value = _state.value.copy(banner = null)
    }

    /**
     * Create a new directory under the current path and re-browse so it
     * appears in the listing. Mirrors PWA v5.26.46 "+ New folder" (#14).
     * Refuses path-separator characters client-side; the daemon will
     * also reject them, but failing fast keeps the UI responsive.
     */
    public fun newFolder(name: String) {
        val safeName = name.trim()
        if (safeName.isEmpty()) return
        if (safeName.contains('/') || safeName.contains('\\')) {
            _state.value = _state.value.copy(
                banner = "Folder name can't contain '/' or '\\\\'.",
            )
            return
        }
        val parent = _state.value.path ?: return
        viewModelScope.launch {
            val profile = resolveActiveProfile() ?: return@launch
            val newPath =
                if (parent.endsWith("/")) "$parent$safeName"
                else "$parent/$safeName"
            ServiceLocator.transportFor(profile).mkdir(newPath).fold(
                onSuccess = {
                    // Re-browse current dir to surface the new folder.
                    browse(parent)
                },
                onFailure = { err ->
                    _state.value = _state.value.copy(
                        banner = "Create folder failed — ${err.message ?: err::class.simpleName}",
                    )
                },
            )
        }
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
            _state.value = UiState(banner = "No enabled server to browse.")
        }
        return profile
    }
}
