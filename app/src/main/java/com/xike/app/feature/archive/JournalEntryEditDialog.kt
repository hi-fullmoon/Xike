package com.xike.app.feature.archive

import com.xike.app.ui.components.journal.OutdoorContextCard

import com.xike.app.ui.components.MomentNoteCard
import com.xike.app.ui.components.MomentContentToolbar
import com.xike.app.ui.components.MomentMediaAction
import com.xike.app.ui.components.showRecordedAtPicker
import com.xike.app.ui.components.MoodPicker
import com.xike.app.ui.components.MomentTopicsHeader
import com.xike.app.ui.components.TopicChoices
import com.xike.app.ui.components.AddPhotoTile
import com.xike.app.ui.components.PhotoGalleryDialog
import com.xike.app.ui.components.rememberPreviewBitmap
import com.xike.app.ui.components.PaperCard
import com.xike.app.ui.components.XikeNotice
import com.xike.app.ui.components.DestructiveConfirmationDialog

import com.xike.app.ui.theme.LocalRecordedAtPickerContext
import com.xike.app.ui.theme.XikeContentCardPadding
import com.xike.app.ui.theme.XikeInnerCardPadding
import com.xike.app.ui.theme.XikeSheetHorizontalPadding
import com.xike.app.ui.theme.XikeSheetBottomPadding
import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.XikeContentMaxWidth
import com.xike.app.ui.theme.XikeScreenHorizontalPadding
import com.xike.app.ui.theme.XikeScreenVerticalPadding
import com.xike.app.ui.theme.XikeContentGap
import com.xike.app.ui.theme.xikeButtonElevation

import com.xike.app.LocalAudioEditServices
import com.xike.app.data.model.MAX_DRAFT_NOTE_LENGTH
import com.xike.app.EditOperation
import com.xike.app.EditOutcome
import com.xike.app.LocalJournalEditOperations
import com.xike.app.data.Mood
import com.xike.app.data.MAX_IMAGES_PER_ENTRY
import com.xike.app.data.JournalAudio
import com.xike.app.data.JournalEntry
import com.xike.app.canonicalTopics
import com.xike.app.toggleTopic
import com.xike.app.data.model.JournalVideo
import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.data.model.retainOutdoorForEditedTime
import com.xike.app.StagedDraftAudio
import com.xike.app.canTakePhoto
import com.xike.app.isCameraCaptureUri
import com.xike.app.deleteCameraCapture
import com.xike.app.rememberJournalImagePickers
import com.xike.app.PhotoSourceDialog
import com.xike.app.LocalVideoServices
import com.xike.app.rememberVideoChooser
import com.xike.app.VideoCard
import com.xike.app.VideoImportStatus
import com.xike.app.ui.components.VoiceCaptureSheet
import com.xike.app.ui.components.VoicePendingSaveCard
import com.xike.app.ui.components.VoicePlaybackCard




import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.InputStream
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun JournalEntryEditDialog(
    entry: JournalEntry,
    openImage: (String) -> InputStream?,
    openAudio: (String) -> InputStream?,
    onDismiss: () -> Unit,
    onSave: suspend (JournalEntry, List<String>, List<Uri>) -> Result<JournalEntry>,
    onSaved: (JournalEntry) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val operations = LocalJournalEditOperations.current
    val operationState = operations.state(entry.id)
    val editorScrollState = rememberScrollState()
    var editorFocusManager by remember { mutableStateOf<FocusManager?>(null) }
    var editorKeyboardController by remember { mutableStateOf<SoftwareKeyboardController?>(null) }
    var isNoteFocused by remember { mutableStateOf(false) }
    val dismissKeyboard: () -> Unit = { editorFocusManager?.clearFocus(); editorKeyboardController?.hide() }
    var selectedMoodName by rememberSaveable(entry.id) { mutableStateOf(entry.mood.name) }
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.note) }
    var selectedTags by rememberSaveable(entry.id) { mutableStateOf(entry.tags) }
    var showTopics by rememberSaveable(entry.id) { mutableStateOf(entry.tags.isNotEmpty()) }
    val topicsAnchor = remember { BringIntoViewRequester() }
    var revealTopicsRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(revealTopicsRequest) {
        if (revealTopicsRequest > 0) { withFrameNanos { }; topicsAnchor.bringIntoView() }
    }
    var showAddContent by rememberSaveable(entry.id) { mutableStateOf(false) }
    var retainedAudioJson by rememberSaveable(entry.id) { mutableStateOf(entry.audio?.toJson()?.toString()) }
    val retainedAudio = retainedAudioJson?.let { JournalAudio.fromJson(org.json.JSONObject(it)) }
    val audioServices = LocalAudioEditServices.current
    var importedAudioJsons by rememberSaveable(entry.id) { mutableStateOf(emptyList<String>()) }
    val importedAudios = importedAudioJsons.map { JournalAudio.fromJson(org.json.JSONObject(it))!! }
    var lastImportedAudioFileName by rememberSaveable(entry.id) { mutableStateOf(importedAudios.lastOrNull()?.fileName) }
    var showVoiceCapture by remember { mutableStateOf(false) }
    val pendingAudioSaver = Saver<Pair<java.io.File, Long>?, List<String>>(
        save = { it?.let { (file, duration) -> listOf(file.absolutePath, duration.toString()) } ?: emptyList() },
        restore = { if (it.isEmpty()) null else java.io.File(it[0]) to it[1].toLong() },
    )
    var pendingAudio by rememberSaveable(entry.id, stateSaver = pendingAudioSaver) { mutableStateOf<Pair<java.io.File, Long>?>(null) }
    val isAudioImporting = operationState.operation == EditOperation.AUDIO
    var audioImportError by remember { mutableStateOf<String?>(null) }
    val stagedAudioSaver = Saver<StagedDraftAudio?, List<String>>(
        save = { it?.let { staged -> listOf(staged.fileName, staged.durationMillis.toString()) } ?: emptyList() },
        restore = { if (it.isEmpty()) null else StagedDraftAudio(it[0], it[1].toLong()) },
    )
    var pendingStagedAudio by rememberSaveable(entry.id, stateSaver = stagedAudioSaver) { mutableStateOf<StagedDraftAudio?>(null) }
    var audioRecoveryFailed by rememberSaveable(entry.id) { mutableStateOf(false) }
    val importPendingAudio: () -> Unit = {
        if (pendingAudio != null || pendingStagedAudio != null) {
            val raw = pendingAudio
            val staged = pendingStagedAudio
            operations.start(entry.id, EditOperation.AUDIO) { operations.importAudio(entry.id, raw, staged) }
        }
    }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showVoiceCapture = true
        else XikeNotice.makeText(context, localizedText("没有麦克风权限，仍可使用文字和照片记录"), XikeNotice.LENGTH_LONG).show()
    }
    val beginVoiceCapture: () -> Unit = {
        dismissKeyboard()
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) showVoiceCapture = true
        else microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val videoServices = LocalVideoServices.current
    var retainedVideoJson by rememberSaveable(entry.id) {
        mutableStateOf(entry.video?.toJson()?.toString())
    }
    LaunchedEffect(entry.id) {
        if (operationState.outcome == null && !operationState.busy) {
            operations.start(entry.id, EditOperation.RECOVER) {
                val videoDraft = withContext(Dispatchers.IO) { videoServices.editDraft(entry) }
                EditOutcome.Restored(operations.recoverAudio(entry.id), videoDraft)
            }
        }
    }
    val retainedVideo = retainedVideoJson?.let { JournalVideo.fromJson(org.json.JSONObject(it)) }
    val isVideoImporting = operationState.operation == EditOperation.VIDEO
    val videoProgress = operationState.progress
    var importedVideoJsons by rememberSaveable(entry.id) { mutableStateOf(emptyList<String>()) }
    val importedVideos = importedVideoJsons.map { JournalVideo.fromJson(org.json.JSONObject(it))!! }
    var createdAt by rememberSaveable(entry.id) { mutableStateOf(entry.createdAt) }
    val pickerContext = LocalRecordedAtPickerContext.current
    var previewPhoto by rememberSaveable(entry.id) { mutableStateOf<String?>(null) }
    var retainedImages by rememberSaveable(entry.id) { mutableStateOf(entry.imageFileNames) }
    var newImageUriStrings by rememberSaveable(entry.id) { mutableStateOf(emptyList<String>()) }
    val isSaving = operationState.operation == EditOperation.SAVE || operationState.operation == EditOperation.DISCARD
    val isVideoRemoving = operationState.operation == EditOperation.REMOVE_VIDEO
    val isDiscarding = operationState.operation == EditOperation.DISCARD
    var saveError by remember { mutableStateOf<String?>(null) }
    var saveFailureCount by remember { mutableIntStateOf(0) }
    var showDiscardConfirmation by rememberSaveable(entry.id) { mutableStateOf(false) }
    var showPhotoSourceDialog by rememberSaveable(entry.id) { mutableStateOf(false) }
    LaunchedEffect(operationState.outcome) {
        val delivered = operationState.outcome ?: return@LaunchedEffect
        val outcome = if (delivered is EditOutcome.Restored) {
            delivered.videoDraft?.let { draft -> retainedVideoJson = draft.video?.toJson()?.toString() }
            delivered.audio
        } else delivered
        val operation = operationState.operation
        when (outcome) {
            is EditOutcome.AudioReady -> {
                if (lastImportedAudioFileName != outcome.audio.fileName) {
                    importedAudioJsons = (importedAudioJsons + outcome.audio.toJson().toString()).distinct()
                    retainedAudioJson = outcome.audio.toJson().toString()
                    lastImportedAudioFileName = outcome.audio.fileName
                }
                pendingAudio = null; pendingStagedAudio = null; audioImportError = null; audioRecoveryFailed = false
            }
            EditOutcome.AudioCleared -> { pendingAudio = null; pendingStagedAudio = null; audioImportError = null; audioRecoveryFailed = false }
            is EditOutcome.AudioPending -> {
                pendingAudio = null; pendingStagedAudio = outcome.staged; audioImportError = outcome.message
            }
            is EditOutcome.Recovered -> {
                if (outcome.raw != null || pendingAudio?.first?.isFile != true) pendingAudio = outcome.raw
                pendingStagedAudio = outcome.staged ?: pendingStagedAudio
                audioRecoveryFailed = false
            }
            is EditOutcome.Restored -> error("Nested editor recovery result")
            is EditOutcome.VideoReady -> {
                retainedVideoJson = outcome.video?.toJson()?.toString()
                outcome.video?.let { importedVideoJsons = importedVideoJsons + it.toJson().toString() }
            }
            is EditOutcome.Failed -> {
                if (operation == EditOperation.RECOVER) audioRecoveryFailed = true
                if (operation == EditOperation.AUDIO) audioImportError = outcome.message
                saveError = outcome.message; saveFailureCount++
            }
            is EditOutcome.Saved -> {
                operations.acknowledge(entry.id)
                XikeNotice.makeText(context,
                    if (outcome.cleanupFailed) tr("修改已保存，部分临时附件或照片授权暂未清理。", "Changes saved. Some temporary attachments or photo permissions could not be cleaned up yet.") else localizedText("修改已保存"),
                    if (outcome.cleanupFailed) XikeNotice.LENGTH_LONG else XikeNotice.LENGTH_SHORT).show()
                onSaved(outcome.entry)
                return@LaunchedEffect
            }
            EditOutcome.Discarded -> {
                operations.acknowledge(entry.id); onDismiss(); return@LaunchedEffect
            }
        }
        operations.acknowledge(entry.id)
        if (outcome is EditOutcome.Recovered) importPendingAudio()
    }
    LaunchedEffect(entry.id, newImageUriStrings) {
        newImageUriStrings.forEach { operations.retainImage(entry.id, Uri.parse(it)) }
    }
    val selectedMood = Mood.entries.firstOrNull { it.name == selectedMoodName } ?: entry.mood
    var outdoorRemoved by rememberSaveable(entry.id) { mutableStateOf(false) }
    val timeRetainedOutdoor = retainOutdoorForEditedTime(entry.outdoor, entry.createdAt, createdAt)
    val editedOutdoor = if (outdoorRemoved) null else timeRetainedOutdoor
    val removesOutdoor = !outdoorRemoved && entry.outdoor != null && timeRetainedOutdoor == null
    val availableImageSlots = MAX_IMAGES_PER_ENTRY - retainedImages.size - newImageUriStrings.size
    val imagePickers = rememberJournalImagePickers(entry.id) { uris, access ->
        val additions = uris
            .map(Uri::toString)
            .distinct()
            .filterNot { it in newImageUriStrings }
            .take(availableImageSlots.coerceAtLeast(0))
        val readableAdditions = additions.filter { source ->
            val uri = Uri.parse(source)
            isCameraCaptureUri(context, uri) || runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
        }
        readableAdditions.forEach { operations.retainImage(entry.id, Uri.parse(it)) }
        newImageUriStrings = newImageUriStrings + readableAdditions
        access.transfer(newImageUriStrings.map(Uri::parse))
        if (readableAdditions.size < additions.size) {
            XikeNotice.makeText(context, localizedText("所选照片无法获得长期读取权限，请重新选择。"), XikeNotice.LENGTH_LONG).show()
        }
        if (additions.size < uris.distinct().size) {
            XikeNotice.makeText(context, tr("每条记录最多保留 $MAX_IMAGES_PER_ENTRY 张照片", "Each entry can keep up to $MAX_IMAGES_PER_ENTRY photos"), XikeNotice.LENGTH_SHORT).show()
        }
        true
    }
    val openPhotoPicker = { if (availableImageSlots > 0 && !isSaving) imagePickers.openPhotoPicker() }
    val openCamera = { if (availableImageSlots > 0 && !isSaving) imagePickers.openCamera() }
    val hasUnsavedChanges = selectedMood != entry.mood ||
        createdAt != entry.createdAt ||
        note != entry.note ||
        selectedTags != entry.tags ||
        retainedAudio != entry.audio || pendingAudio != null || pendingStagedAudio != null || audioRecoveryFailed ||
        retainedVideo != entry.video ||
        editedOutdoor != entry.outdoor ||
        retainedImages != entry.imageFileNames ||
        newImageUriStrings.isNotEmpty()
    val discardEditor: () -> Unit = {
        val audios = importedAudios.toList()
        val videos = importedVideos.toList()
        val selectedVideo = retainedVideo
        val cameraUri = imagePickers.pendingCameraUri()
        operations.start(entry.id, EditOperation.DISCARD) {
            if (entry.video != null || selectedVideo != null || videos.isNotEmpty()) videoServices.clearEdit(entry.id).getOrThrow()
            operations.discardAudio(entry.id)
            operations.releaseImages(entry.id)
            audios.forEach(audioServices.release)
            videos.forEach(videoServices.release)
            selectedVideo?.takeIf { it != entry.video }?.let(videoServices.release)
            cameraUri?.let(Uri::parse)?.let { deleteCameraCapture(context, it) }
            EditOutcome.Discarded
        }
    }
    val dismissEditor = {
        if (!operationState.busy) {
            if (hasUnsavedChanges) showDiscardConfirmation = true else discardEditor()
        }
    }

    val canSave = !operationState.busy && pendingAudio == null && pendingStagedAudio == null && !audioRecoveryFailed && !showVoiceCapture
    val saveChanges: () -> Unit = saveChanges@{
        if (!canSave) return@saveChanges
        dismissKeyboard()
        saveError = null
        val updatedEntry = entry.copy(
            createdAt = createdAt, mood = selectedMood, tags = selectedTags,
            note = note.trim(), audio = retainedAudio, video = retainedVideo, outdoor = editedOutdoor,
        )
        val imagesToRetain = retainedImages.toList()
        val imagesToAdd = newImageUriStrings.map(Uri::parse)
        val audiosToRelease = importedAudios.toList()
        val videosToRelease = importedVideos.toList()
        operations.start(entry.id, EditOperation.SAVE) {
            val savedEntry = onSave(updatedEntry, imagesToRetain, imagesToAdd).getOrThrow()
            val cleanupFailed = withContext(NonCancellable) {
                // The database is committed. Cleanup must not turn success into a second save.
                val videoCleanup = videoServices.clearEdit(entry.id)
                val audioCleanup = runCatching { operations.discardAudio(entry.id) }
                audiosToRelease.forEach(audioServices.release)
                videosToRelease.forEach(videoServices.release)
                val imageCleanup = runCatching { operations.releaseImages(entry.id) }
                videoCleanup.isFailure || audioCleanup.isFailure || imageCleanup.isFailure
            }
            EditOutcome.Saved(savedEntry, cleanupFailed)
        }
    }
    val chooseVideo = rememberVideoChooser(enabled = !operationState.busy, ownerKey = entry.id, onPicked = { uri, access ->
        val previousVideo = retainedVideo
        val started = operations.start(entry.id, EditOperation.VIDEO) {
            try {
                val video = videoServices.import(uri) { progress -> operations.progress(entry.id, progress) }.getOrThrow()
                val result = videoServices.saveEdit(entry, video)
                if (result.isFailure) { videoServices.release(video); result.getOrThrow() }
                previousVideo?.takeIf { it != entry.video }?.let(videoServices.release)
                EditOutcome.VideoReady(video)
            } finally { access.release() }
        }
        if (started) access.deferRelease()
        started
    })
    Dialog(
        onDismissRequest = { if (!isSaving && !isVideoImporting && !isAudioImporting) dismissEditor() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogFocusManager = LocalFocusManager.current
        val dialogKeyboardController = LocalSoftwareKeyboardController.current
        SideEffect {
            editorFocusManager = dialogFocusManager
            editorKeyboardController = dialogKeyboardController
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier.widthIn(max = XikeContentMaxWidth)
                        .fillMaxWidth().align(Alignment.CenterHorizontally)
                        .padding(horizontal = XikeScreenHorizontalPadding, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(enabled = !isSaving && !isVideoImporting && !isAudioImporting && !isVideoRemoving && !isDiscarding, onClick = dismissEditor) {
                        Icon(Icons.Outlined.Close, contentDescription = localizedText("取消编辑"))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(localizedText("编辑这一刻"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // A short viewport must let the error scroll away so the form stays usable.
                    val pinnedErrorMaxHeight = 160.dp
                    val minimumBodyHeight = 96.dp
                    val scrollErrorWithContent = maxHeight < pinnedErrorMaxHeight + minimumBodyHeight
                    LaunchedEffect(saveError, saveFailureCount) {
                        if (saveError != null && scrollErrorWithContent) editorScrollState.scrollTo(0)
                    }
                    val errorPanel: @Composable () -> Unit = {
                        saveError?.let { error ->
                            Surface(
                                modifier = Modifier.testTag("journal-edit-error").fillMaxWidth(),
                                shape = XikeShapes.inner,
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Text(
                                    error,
                                    modifier = (if (scrollErrorWithContent) Modifier else Modifier.heightIn(max = pinnedErrorMaxHeight)
                                        .verticalScroll(rememberScrollState()))
                                        .padding(XikeInnerCardPadding),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    Column(
                        Modifier.widthIn(max = XikeContentMaxWidth).fillMaxWidth()
                            .align(Alignment.TopCenter).fillMaxSize(),
                    ) {
                        if (saveError != null && !scrollErrorWithContent) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = XikeScreenHorizontalPadding)) { errorPanel() }
                        }

                Column(
                    modifier = Modifier
                        .testTag("journal-edit-body")
                        .weight(1f)
                        .widthIn(max = XikeContentMaxWidth)
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .verticalScroll(editorScrollState)
                        .padding(horizontal = XikeScreenHorizontalPadding, vertical = XikeScreenVerticalPadding),
                    verticalArrangement = Arrangement.spacedBy(XikeContentGap),
                ) {
                if (saveError != null && scrollErrorWithContent) errorPanel()
                MoodPicker(selectedMood, !isSaving) { mood -> mood?.let { selectedMoodName = it.name } }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("写下一点点"), style = MaterialTheme.typography.titleMedium)
                    MomentNoteCard(
                        note = note,
                        enabled = !isSaving,
                        onNoteChange = { if (!isSaving) note = it.take(MAX_DRAFT_NOTE_LENGTH) },
                        onFocusChange = { isNoteFocused = it },
                    ) {
                        MomentContentToolbar(
                            recordedAt = createdAt,
                            enabled = !isSaving,
                            onDone = dismissKeyboard.takeIf { isNoteFocused },
                            isEditing = true,
                            onAdd = { dismissKeyboard(); showAddContent = true },
                            onChooseTime = { dismissKeyboard(); showRecordedAtPicker(pickerContext, createdAt) { createdAt = it } },
                        )
                    }
                    if (removesOutdoor) {
                        Text(
                            localizedText("日期已经改变，保存时会移除原先的窗外天气，避免误记到另一天。"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                PaperCard(contentPadding = PaddingValues(0.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !isSaving, role = Role.Button,
                            onClickLabel = if (showTopics) localizedText("收起主题") else localizedText("展开主题"),
                        ) {
                            dismissKeyboard()
                            showTopics = !showTopics
                            if (showTopics) revealTopicsRequest++
                        }
                            .padding(XikeContentCardPadding).heightIn(min = 42.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(localizedText("再留下一点"), style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (selectedTags.isEmpty()) localizedText("选主题 · 可选") else tr("已选 ${selectedTags.canonicalTopics().size} 个主题", "${selectedTags.canonicalTopics().size} topics selected"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(if (showTopics) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    if (showTopics) {
                        HorizontalDivider(Modifier.padding(horizontal = XikeContentCardPadding), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        Column(Modifier.bringIntoViewRequester(topicsAnchor).padding(XikeContentCardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MomentTopicsHeader(selectedTags.canonicalTopics().size, highlightSelection = false)
                            TopicChoices(selectedTags) { topic -> if (!isSaving) selectedTags = toggleTopic(selectedTags, topic.label) }
                        }
                    }
                }

                editedOutdoor?.let { outdoor ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(localizedText("此刻窗外"), style = MaterialTheme.typography.titleSmall)
                        OutdoorContextCard(
                            snapshot = outdoor, isBackdated = false, isLoading = false,
                            errorMessage = null, enabled = !isSaving,
                            onAdd = {}, onChooseCity = {}, onRemove = { outdoorRemoved = true },
                            showRefresh = false,
                        )
                    }
                }

                if (retainedAudio != null || pendingAudio != null || pendingStagedAudio != null) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizedText("这一刻的声音"), style = MaterialTheme.typography.titleSmall)
                    val audio = retainedAudio
                    if (audio != null) {
                        VoicePlaybackCard(
                            audio = audio,
                            openAudio = openAudio,
                            onDelete = if (operationState.busy || pendingAudio != null || pendingStagedAudio != null) null else ({ retainedAudioJson = null }),
                            onReplace = if (operationState.busy || pendingAudio != null || pendingStagedAudio != null) null else beginVoiceCapture,
                            showBorder = false,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            showDeleteAtTopEnd = true,
                            shape = XikeShapes.inner,
                        )
                    }
                    (pendingStagedAudio?.durationMillis ?: pendingAudio?.second)?.let { duration ->
                        VoicePendingSaveCard(
                            durationMillis = duration,
                            isSaving = isAudioImporting,
                            errorMessage = audioImportError,
                            onRetry = importPendingAudio,
                            onDiscard = {
                                operations.start(entry.id, EditOperation.AUDIO) {
                                    operations.discardAudio(entry.id, preserveReceipt = true); EditOutcome.AudioCleared
                                }
                            },
                        )
                    }
                }
                }

                if (retainedImages.isNotEmpty() || newImageUriStrings.isNotEmpty()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("这一刻的照片", "Photos of this moment"), style = MaterialTheme.typography.titleSmall)
                    PaperCard {
                    EditPhotoGrid(
                        retainedImages = retainedImages,
                        newImageUriStrings = newImageUriStrings,
                        canAdd = availableImageSlots > 0 && !isSaving,
                        enabled = !isSaving,
                        openImage = openImage,
                        onAdd = { showPhotoSourceDialog = true },
                        onPreview = { previewPhoto = it },
                        onRemoveRetained = { if (!isSaving) retainedImages = retainedImages - it },
                        onRemoveNew = { uriString ->
                            if (!operationState.busy) {
                                scope.launch {
                                    runCatching { operations.releaseImage(entry.id, Uri.parse(uriString)) }
                                        .onSuccess { newImageUriStrings = newImageUriStrings - uriString }
                                        .onFailure { saveError = it.message }
                                }
                            }
                        },
                    )
                }
                }
                }

                if (retainedVideo != null || isVideoImporting) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(localizedText("这一刻的视频"), style = MaterialTheme.typography.titleSmall)
                        if (isVideoImporting) VideoImportStatus(videoProgress)
                        val video = retainedVideo
                        if (video != null) {
                            VideoCard(
                                video = video,
                                onRemove = if (isSaving || isVideoImporting || isVideoRemoving || isDiscarding) null else ({
                                    operations.start(entry.id, EditOperation.REMOVE_VIDEO) {
                                        videoServices.saveEdit(entry, null).getOrThrow(); EditOutcome.VideoReady(null)
                                    }
                                }),
                                onReplace = if (isSaving || isVideoImporting || isVideoRemoving || isDiscarding) null else ({ dismissKeyboard(); chooseVideo() }),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                }
                    }
                }
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = saveChanges,
                            enabled = canSave,
                            modifier = Modifier.widthIn(max = XikeContentMaxWidth).fillMaxWidth()
                                .padding(horizontal = XikeScreenHorizontalPadding, vertical = 8.dp)
                                .heightIn(min = 48.dp),
                            shape = XikeShapes.button,
                            elevation = xikeButtonElevation(),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            ),
                        ) {
                            if (isSaving && !isDiscarding) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(7.dp))
                            }
                            Text(
                                when {
                                    isSaving && !isDiscarding -> localizedText("保存中")
                                    isVideoImporting -> tr("正在导入视频…", "Importing video…")
                                    isAudioImporting -> localizedText("正在保存语音…")
                                    pendingAudio != null || pendingStagedAudio != null || audioRecoveryFailed -> localizedText("请先处理录音")
                                    showVoiceCapture -> localizedText("请先完成录音")
                                    else -> localizedText("保存")
                                },
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddContent) {
        ModalBottomSheet(
            onDismissRequest = { showAddContent = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            sheetMaxWidth = XikeContentMaxWidth,
            shape = XikeShapes.sheet,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.fillMaxWidth().testTag("journal-edit-add-content").verticalScroll(rememberScrollState())
                    .padding(horizontal = XikeSheetHorizontalPadding).padding(bottom = XikeSheetBottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(tr("添加内容", "Add content"), Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.titleLarge)
                MomentMediaAction(
                    if (retainedAudio == null) tr("录音", "Record audio") else tr("替换录音", "Replace audio"),
                    !operationState.busy && pendingAudio == null && pendingStagedAudio == null && !audioRecoveryFailed && !showVoiceCapture,
                ) { showAddContent = false; beginVoiceCapture() }
                MomentMediaAction(localizedText("照片"), availableImageSlots > 0 && !isSaving) {
                    showAddContent = false; showPhotoSourceDialog = true
                }
                MomentMediaAction(tr("视频", "Video"), !isSaving && !isVideoImporting && !isVideoRemoving && !isDiscarding) {
                    showAddContent = false; chooseVideo()
                }
            }
        }
    }

    if (showVoiceCapture) {
        VoiceCaptureSheet(
            enabled = !isSaving && !isAudioImporting,
            onRecorded = { file, duration ->
                pendingAudio = file to duration
                importPendingAudio()
            },
            onClosed = { showVoiceCapture = false },
            recordingDirectory = operations.recordingDirectory(entry.id),
        )
    }

    val photos = retainedImages + newImageUriStrings
    previewPhoto?.let { photo ->
        if (photo in photos) {
            PhotoGalleryDialog(
                fileNames = photos,
                initialPage = photos.indexOf(photo),
                openImage = { source ->
                    if (source in retainedImages) openImage(source)
                    else context.contentResolver.openInputStream(Uri.parse(source))
                },
                onDismiss = { previewPhoto = null },
            )
        }
    }

    if (showPhotoSourceDialog) {
        PhotoSourceDialog(
            cameraAvailable = canTakePhoto(context),
            onTakePhoto = {
                showPhotoSourceDialog = false
                openCamera()
            },
            onChoosePhotos = {
                showPhotoSourceDialog = false
                openPhotoPicker()
            },
            onDismiss = { showPhotoSourceDialog = false },
        )
    }

    if (showDiscardConfirmation) {
        DestructiveConfirmationDialog(
            onDismiss = { showDiscardConfirmation = false },
            title = localizedText("放弃未保存的修改？"),
            dismissText = localizedText("继续编辑"),
            confirmText = localizedText("放弃修改"),
            text = { Text(localizedText("心情、时间、注脚、主题和附件的本次修改都不会保存。")) },
            onConfirm = {
                showDiscardConfirmation = false
                discardEditor()
            },
        )
    }
}

@Composable
private fun EditPhotoGrid(
    retainedImages: List<String>,
    newImageUriStrings: List<String>,
    canAdd: Boolean,
    enabled: Boolean,
    openImage: (String) -> InputStream?,
    onAdd: () -> Unit,
    onPreview: (String) -> Unit,
    onRemoveRetained: (String) -> Unit,
    onRemoveNew: (String) -> Unit,
) {
    val context = LocalContext.current
    val photos = retainedImages + newImageUriStrings
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val columns = ((maxWidth + 8.dp) / (104.dp + 8.dp)).toInt().coerceIn(1, 3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (newImageUriStrings.isEmpty()) localizedText("加密保存在本机") else tr("保存后加密保存在本机", "Encrypted on this device after saving"),
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("${photos.size} / $MAX_IMAGES_PER_ENTRY", style = MaterialTheme.typography.labelSmall)
        }
        val tiles: List<String?> = photos + if (canAdd) listOf(null) else emptyList()
        tiles.chunked(columns).forEach { rowTiles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTiles.forEach { source ->
                    val tileModifier = Modifier.weight(1f).aspectRatio(1f)
                    if (source == null) {
                        AddPhotoTile(tileModifier, localizedText("继续添加"), onAdd)
                    } else {
                        val retained = source in retainedImages
                        val order = if (retained) retainedImages.indexOf(source) + 1 else newImageUriStrings.indexOf(source) + 1
                        EditPhotoTile(
                            key = source,
                            modifier = tileModifier,
                            photoDescription = if (retained) tr("已有照片 $order", "Existing photo $order") else tr("新照片 $order", "New photo $order"),
                            openStream = { if (retained) openImage(source) else context.contentResolver.openInputStream(Uri.parse(source)) },
                            onRemove = { if (retained) onRemoveRetained(source) else onRemoveNew(source) },
                            enabled = enabled,
                            onPreview = { onPreview(source) },
                        )
                    }
                }
                repeat(columns - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    }
}

@Composable
private fun EditPhotoTile(
    key: String,
    modifier: Modifier,
    photoDescription: String,
    openStream: () -> InputStream?,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
    enabled: Boolean,
) {
    val bitmap = rememberPreviewBitmap(key = key, maxDimension = 360, openStream = openStream)
    val removeDescription = tr("移除$photoDescription", "Remove $photoDescription")
    Box(
        modifier = modifier
            .clip(XikeShapes.inner)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = localizedText("预览照片"), onClick = onPreview),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = photoDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Outlined.Image,
                contentDescription = photoDescription,
                modifier = Modifier.align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .semantics { contentDescription = removeDescription },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.66f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}
