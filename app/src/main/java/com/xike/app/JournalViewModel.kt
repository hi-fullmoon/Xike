package com.xike.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JournalViewModel(application: Application) : AndroidViewModel(application) {
    private val store = JournalStore(application)

    var entries by mutableStateOf(emptyList<JournalEntry>())
        private set

    var selectedTheme by mutableStateOf(AppTheme.OCEAN)
        private set

    var dataError by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(true)
        private set

    var canUndoRestore by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(store::initialize) }
            result.onSuccess { snapshot ->
                entries = snapshot.entries
                selectedTheme = AppTheme.entries.firstOrNull { it.name == snapshot.themeName } ?: AppTheme.OCEAN
                canUndoRestore = runCatching(store::canUndoLastRestore).getOrDefault(false)
                dataError = null
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching(store::removeOrphanedImages)
                }
            }.onFailure { error ->
                dataError = error.message ?: "日记数据库暂时无法读取，原数据未被覆盖。"
            }
            isLoading = false
        }
    }

    fun dismissDataError() {
        dataError = null
    }

    fun selectTheme(theme: AppTheme) {
        selectedTheme = theme
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { store.saveThemeName(theme.name) } }
                .onFailure { error ->
                    dataError = error.message ?: "外观设置保存失败，请重试。"
                }
        }
    }

    suspend fun save(entry: JournalEntry, imageUris: List<Uri>): Result<Unit> = viewModelScope.async {
        val result = withContext(Dispatchers.IO) {
            runCatching { store.add(entry, imageUris) }
        }
        result.onSuccess { restoredEntries ->
            entries = restoredEntries
            dataError = null
        }
        result.map { }
    }.await()

    suspend fun exportBackup(uri: Uri, password: String): Result<Unit> = viewModelScope.async(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                store.writeEncryptedBackup(output, password)
            } ?: error("无法写入备份文件")
        }
    }.await()

    suspend fun inspectBackup(uri: Uri, password: String): Result<BackupSummary> = viewModelScope.async(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                store.inspectEncryptedBackup(input, password)
            } ?: error("无法读取备份文件")
        }
    }.await()

    suspend fun restoreBackup(uri: Uri, password: String): Result<Int> = viewModelScope.async {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    store.restoreEncryptedBackup(input, password)
                } ?: error("无法读取备份文件")
            }
        }
        result.onSuccess {
            entries = it
            canUndoRestore = true
            dataError = null
        }
        result.map { it.size }
    }.await()

    suspend fun undoRestore(): Result<Int> = viewModelScope.async {
        val result = withContext(Dispatchers.IO) { runCatching(store::undoLastRestore) }
        result.onSuccess {
            entries = it
            canUndoRestore = false
            dataError = null
        }
        result.map { it.size }
    }.await()

    fun openImage(fileName: String): InputStream? = store.openImage(fileName)
}
