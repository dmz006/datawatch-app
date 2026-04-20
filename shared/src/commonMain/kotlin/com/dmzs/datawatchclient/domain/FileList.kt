package com.dmzs.datawatchclient.domain

import kotlinx.serialization.Serializable

/**
 * A single entry in a directory listing returned by `GET /api/files?path=`.
 */
@Serializable
public data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
)

/**
 * Directory listing container. [path] is the absolute server-side path the
 * entries belong to, so callers can build a breadcrumb without parsing child
 * paths.
 */
@Serializable
public data class FileList(
    val path: String,
    val entries: List<FileEntry>,
)
