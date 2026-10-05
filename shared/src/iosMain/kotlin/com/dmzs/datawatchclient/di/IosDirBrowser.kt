package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.FileList
import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** One folder in a server directory listing, flattened for Swift. */
public data class IosDirEntry(
    val name: String,
    val path: String,
)

/**
 * A folder-only listing of [path] on the server (PWA dir browser / Android
 * FilePickerDialog FolderOnly). [parent] is null at the root.
 */
public data class IosDirListing(
    val path: String,
    val parent: String?,
    val dirs: List<IosDirEntry>,
)

/**
 * Server directory browser for the New Session and Launch Automaton wizards
 * (parity 08 › Directory browser, 05 › Wizard directory + Browse). Reuses the
 * Android picker endpoints: `GET /api/files?path=` and `POST /api/files` mkdir.
 */
public object IosDirBrowser {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Lists folders under [path]; blank lists the daemon's default root. */
    public fun list(
        profile: ServerProfile,
        path: String,
        onSuccess: (IosDirListing) -> Unit,
        onError: (String) -> Unit,
    ) {
        val requested: String? = path.trim().ifBlank { null }
        scope.launch {
            IosServiceLocator.transportFor(profile).browseFiles(requested).fold(
                onSuccess = { fl: FileList -> onSuccess(toListing(fl)) },
                onFailure = { e -> onError(e.message ?: "Couldn't list this folder.") },
            )
        }
    }

    /** Creates [name] inside [parentPath], then reports the new folder's path. */
    public fun mkdir(
        profile: ServerProfile,
        parentPath: String,
        name: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val clean: String = name.trim().trim('/')
        if (clean.isEmpty() || clean.contains('/') || clean == "." || clean == "..") {
            onError("Enter a single folder name.")
            return
        }
        val base: String = parentPath.trimEnd('/')
        val full: String = if (base.isEmpty()) "/$clean" else "$base/$clean"
        scope.launch {
            IosServiceLocator.transportFor(profile).mkdir(full).fold(
                onSuccess = { onSuccess(full) },
                onFailure = { e -> onError(e.message ?: "Couldn't create the folder.") },
            )
        }
    }

    /** Android FilePickerViewModel.goUp: strip trailing '/', drop the last segment. */
    public fun parentOf(path: String): String? {
        val trimmed: String = path.trimEnd('/')
        if (trimmed.isEmpty()) return null
        val parent: String = trimmed.substringBeforeLast('/', missingDelimiterValue = "")
        return parent.ifBlank { "/" }
    }

    internal fun toListing(fl: FileList): IosDirListing {
        val dirs: List<IosDirEntry> =
            fl.entries
                .filter { it.isDirectory && it.name != "." && it.name != ".." }
                .sortedBy { it.name.lowercase() }
                .map { e -> IosDirEntry(name = e.name, path = e.path) }
        // The daemon lists its own ".." entry (PWA ⬆ row); fall back to string math.
        val serverParent: String? = fl.entries.firstOrNull { it.name == ".." }?.path?.takeIf { it.isNotBlank() }
        return IosDirListing(path = fl.path, parent = serverParent ?: parentOf(fl.path), dirs = dirs)
    }
}
