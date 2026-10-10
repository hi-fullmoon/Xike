package com.xike.app

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Select a real file using the system photo picker first, on an isolated device. */
class JournalEditImageLeaseTest {
    @Test fun sharedDraftAndEditorReferencesReleaseTheRealPersistedGrantOnlyAfterBothEnd() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("editIsolated") == "true")
        val application = ApplicationProvider.getApplicationContext<Application>()
        val grants = application.contentResolver.persistedUriPermissions.filter { it.isReadPermission }
        assumeTrue("Requires one real photo-picker permission", grants.size == 1)
        val uri = grants.single().uri
        val entries = JournalStore(application).entries()
        assumeTrue("Requires one app-created record and an empty draft", entries.size == 1 && JournalDraftStore(application).load().isEmpty)
        val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        val model = withContext(Dispatchers.Main) {
            ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[JournalViewModel::class.java]
        }
        while (model.isLoading) kotlinx.coroutines.delay(25)
        val operations = model.editOperations
        val id = entries.single().id
        fun granted() = application.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        try {
            withContext(Dispatchers.Main) {
                operations.retainImage(id, uri)
                model.addDraftImages(listOf(uri))
            }
            assertTrue(uri.toString() in model.draft.imageUriStrings)
            operations.releaseImage(id, uri)
            assertTrue(granted())
            withContext(Dispatchers.Main) {
                operations.retainImage(id, uri)
                model.removeDraftImage(uri.toString())
            }
            assertTrue(model.draft.imageUriStrings.isEmpty())
            assertTrue(granted())
            operations.releaseImages(id)
            assertFalse(granted())
        } finally {
            withContext(Dispatchers.Main) { model.removeDraftImage(uri.toString()) }
            operations.releaseImages(id)
            withContext(Dispatchers.Main) { owner.viewModelStore.clear() }
        }
    }
}
