package com.xike.app

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Owns only new grants acquired for a pending result; pre-existing grants are never released. */
internal class SystemActivityReadAccess(context: Context, ownedUris: Collection<Uri>) {
    private val resolver = context.applicationContext.contentResolver
    private val owned = ownedUris.toMutableSet()
    private var deferred = false

    fun transfer(uris: Collection<Uri>) { owned.removeAll(uris.toSet()) }
    fun transferAll() { owned.clear() }

    /** The async consumer must call release() in its completion/failure path. */
    fun deferRelease() { deferred = true }

    fun closeUnlessDeferred() { if (!deferred) release() }

    fun release() {
        owned.toList().forEach { uri ->
            runCatching { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                .onSuccess { owned.remove(uri) }
        }
    }
}

/** Acquires metadata-only read access before authentication; no media contents are opened. */
internal fun retainSystemActivityReadAccess(context: Context, result: Any?): List<Uri> {
    val uris = when (result) {
        is Uri -> listOf(result)
        is List<*> -> result.filterIsInstance<Uri>()
        else -> emptyList()
    }.distinct()
    val resolver = context.applicationContext.contentResolver
    val existing = resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }.toSet()
    return uris.filterNot(existing::contains).filter { uri ->
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess
    }
}
