package com.xike.app.data.draft

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.xike.app.data.model.JournalDraft
import com.xike.app.data.model.parseJournalDraft
import com.xike.app.localizedText
import java.util.concurrent.ConcurrentHashMap

internal class JournalDraftStore(context: Context) {
    private val appContext = context.applicationContext
    private val coordinator =
        coordinators.computeIfAbsent(appContext.filesDir.absolutePath) { WriteCoordinator() }
    private var writerSession: Long? = null
    private val masterKey =
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val preferences =
        EncryptedSharedPreferences.create(
            appContext,
            PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun load(): JournalDraft {
        synchronized(coordinator) {
            coordinator.pending?.let {
                return it
            }
        }
        return parseJournalDraft(preferences.getString(DRAFT_KEY, null))
    }

    val hasPendingWrite: Boolean
        get() =
            synchronized(coordinator) {
                coordinator.pending != null
            }

    fun save(draft: JournalDraft) {
        val (previousPending, revision) =
            synchronized(coordinator) {
                coordinator.pending to nextRevision()
            }
        try {
            check(saveIfCurrent(draft, revision)) { localizedText("草稿保存会话已经更新，请重新打开记录页面。") }
        } catch (error: Throwable) {
            synchronized(coordinator) {
                if (writerSession == coordinator.session && coordinator.revision == revision) {
                    coordinator.pending = previousPending
                }
            }
            throw error
        }
    }

    fun nextRevision(): Long =
        synchronized(coordinator) {
            if (writerSession == null) writerSession = ++coordinator.session
            if (writerSession != coordinator.session) return@synchronized -1L
            ++coordinator.revision
        }

    fun queue(draft: JournalDraft): Long =
        synchronized(coordinator) {
            val revision = nextRevision()
            if (revision >= 0L) coordinator.pending = draft.normalized()
            revision
        }

    fun flushPending() {
        val pending =
            synchronized(coordinator) {
                coordinator.pending?.let { it to coordinator.revision }
            } ?: return
        saveIfCurrent(pending.first, pending.second)
    }

    fun saveIfCurrent(draft: JournalDraft, expectedRevision: Long): Boolean {
        val normalized = draft.normalized()
        synchronized(coordinator) {
            if (writerSession != coordinator.session || coordinator.revision != expectedRevision)
                return false
            coordinator.pending = normalized
        }
        return synchronized(coordinator.diskLock) {
            synchronized(coordinator) {
                if (
                    writerSession != coordinator.session || coordinator.revision != expectedRevision
                )
                    return false
            }
            val editor = preferences.edit()
            if (normalized.isEmpty) {
                editor.remove(DRAFT_KEY)
            } else {
                editor.putString(DRAFT_KEY, normalized.toJson().toString())
            }
            check(editor.commit()) { localizedText("无法保存当前草稿。") }
            synchronized(coordinator) {
                if (
                    writerSession == coordinator.session && coordinator.revision == expectedRevision
                ) {
                    coordinator.pending = null
                }
            }
            true
        }
    }

    private class WriteCoordinator {
        val diskLock = Any()
        var session = 0L
        var revision = 0L
        var pending: JournalDraft? = null
    }

    private companion object {
        val coordinators = ConcurrentHashMap<String, WriteCoordinator>()
        const val PREFERENCES_NAME = "xike-journal-draft"
        const val DRAFT_KEY = "draft"
    }
}
