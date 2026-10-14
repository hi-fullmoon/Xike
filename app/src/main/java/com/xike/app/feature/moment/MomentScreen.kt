package com.xike.app.feature.moment

import com.xike.app.ui.components.journal.OutdoorContextCard
import com.xike.app.ui.components.journal.OutdoorCardAction

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
import com.xike.app.ui.components.XikeIcons
import com.xike.app.ui.components.XikeNotice
import com.xike.app.ui.components.DestructiveConfirmationDialog

import com.xike.app.ui.theme.LocalRecordedAtPickerContext
import com.xike.app.ui.theme.XikeCardPadding
import com.xike.app.ui.theme.XikeContentCardPadding
import com.xike.app.ui.theme.XikeInnerCardPadding
import com.xike.app.ui.theme.XikeSheetHorizontalPadding
import com.xike.app.ui.theme.XikeSheetBottomPadding
import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.XikeContentMaxWidth
import com.xike.app.ui.theme.XikeScreenHorizontalPadding
import com.xike.app.ui.theme.XikeScreenVerticalPadding
import com.xike.app.ui.theme.XikePageTitleStyle
import com.xike.app.ui.theme.XikeEyebrowStyle
import com.xike.app.ui.theme.xikeButtonElevation

import com.xike.app.rememberVideoChooser
import com.xike.app.data.model.retainOutdoorForEditedTime
import com.xike.app.feature.insights.entriesOnDate
import com.xike.app.VideoImportStatus
import com.xike.app.canTakePhoto
import com.xike.app.dailyQuestion
import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.AppLocale
import com.xike.app.data.Mood
import com.xike.app.data.JournalEntry
import com.xike.app.data.model.JournalDraft
import com.xike.app.PendingDraftAudio
import com.xike.app.DailyPromptStyle
import com.xike.app.DailyPromptSettings
import com.xike.app.data.MAX_IMAGES_PER_ENTRY
import com.xike.app.ui.components.VoiceCaptureSheet
import com.xike.app.ui.components.VoicePendingSaveCard
import com.xike.app.ui.components.VoicePlaybackCard
import com.xike.app.LocalVideoServices
import com.xike.app.VideoCard
import com.xike.app.PhotoSourceDialog
import com.xike.app.rememberJournalImagePickers



import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val ADD_PHOTO_TILE = "__add_photo__"


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentScreen(
    padding: PaddingValues,
    entries: List<JournalEntry>,
    draft: JournalDraft,
    dailyPromptSettings: DailyPromptSettings,
    onDraftMoodChange: (Mood?) -> Unit,
    onDraftNoteChange: (String) -> Unit,
    onDraftTagToggle: (String) -> Unit,
    onDraftImagesAdded: (List<Uri>) -> Boolean,
    onDraftImageRemoved: (String) -> Unit,
    pendingDraftAudio: PendingDraftAudio? = null,
    isDraftAudioSaving: Boolean = false,
    draftAudioSaveError: String? = null,
    onDraftAudioRecorded: (File, Long) -> Unit = { file, _ -> file.delete() },
    onDraftAudioRetry: () -> Unit = {},
    onDraftAudioDiscard: () -> Unit = {},
    onDraftAudioRemoved: () -> Unit = {},
    openAudio: (String) -> InputStream? = { null },
    onVoiceCaptureStateChange: (Boolean) -> Unit = {},
    onSave: suspend (JournalEntry, List<Uri>) -> Result<Unit>,
    isDraftSaving: Boolean = false,
    isDraftOutdoorLoading: Boolean? = null,
    isDraftImageResultPending: Boolean = false,
    draftImageResultsReady: Boolean = true,
    onDraftImageResultPending: (String, Boolean) -> Unit = { _, _ -> },
    saveFeedbackHandledExternally: Boolean = false,
    onDraftRecordedAtChange: (Long?) -> Unit = {},
    onAttachCurrentOutdoor: suspend () -> Result<Unit> = {
        Result.failure(IllegalStateException(localizedText("窗外天气暂时不可用。")))
    },
    onAttachOutdoorForCity: suspend (String) -> Result<Unit> = {
        Result.failure(IllegalStateException(localizedText("城市天气暂时不可用。")))
    },
    onClearDraftOutdoor: () -> Unit = {},
    onDraftDiscard: () -> Unit = {},
    hasResumedDraft: Boolean = false,
    recordedTodayCount: Int? = null,
) {
    var previewUri by rememberSaveable { mutableStateOf<String?>(null) }
    val videoServices = LocalVideoServices.current
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var revealDetailsRequest by remember { mutableIntStateOf(0) }
    val detailsAnchor = remember { BringIntoViewRequester() }
    var showDiscardConfirmation by rememberSaveable { mutableStateOf(false) }
    var localSaving by remember { mutableStateOf(false) }
    val isSaving = localSaving || isDraftSaving
    val chooseVideo = rememberVideoChooser(
        enabled = !isSaving && !videoServices.draftSaving,
        onPicked = { uri, access ->
            val accepted = videoServices.addDraft(uri)
            if (accepted) access.transferAll()
            accepted
        },
    )
    var isNoteFocused by remember { mutableStateOf(false) }
    var isOutdoorLoading by remember { mutableStateOf(false) }
    val outdoorLoading = isDraftOutdoorLoading ?: isOutdoorLoading
    var pendingRecordedAtChange by rememberSaveable { mutableStateOf<Long?>(null) }
    var showRecordedAtChangeConfirmation by rememberSaveable { mutableStateOf(false) }
    val currentDraft by rememberUpdatedState(draft)
    val currentIsSaving by rememberUpdatedState(isSaving)
    val currentRecordedAtChange by rememberUpdatedState(onDraftRecordedAtChange)
    val selectRecordedAt: (Long?) -> Unit = { selected ->
        if (!currentIsSaving) {
            val snapshot = currentDraft.outdoor
            if (snapshot != null && retainOutdoorForEditedTime(snapshot, snapshot.capturedAt, selected ?: System.currentTimeMillis()) == null) {
                pendingRecordedAtChange = selected
                showRecordedAtChangeConfirmation = true
            } else {
                currentRecordedAtChange(selected)
            }
        }
    }
    var outdoorPermissionDenied by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val locationPreferences = remember(context) {
        context.getSharedPreferences("xike-location", Context.MODE_PRIVATE)
    }
    var locationPermissionRequested by rememberSaveable {
        mutableStateOf(locationPreferences.getBoolean("permission_requested", false))
    }
    var outdoorError by rememberSaveable { mutableStateOf<String?>(null) }
    var showOutdoorDisclosure by rememberSaveable { mutableStateOf(false) }
    var showOutdoorCityDialog by rememberSaveable { mutableStateOf(false) }
    var showVoiceCapture by remember { mutableStateOf(false) }
    val pickerContext = LocalRecordedAtPickerContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val loadCurrentOutdoor: () -> Unit = {
        if (!outdoorLoading && !isSaving && draft.recordedAt == null) {
            isOutdoorLoading = true
            outdoorError = null
            scope.launch {
                try {
                    onAttachCurrentOutdoor()
                        .onSuccess { outdoorPermissionDenied = false }
                        .onFailure { error -> outdoorError = error.message ?: localizedText("暂时无法获取地点与天气。") }
                } finally {
                    isOutdoorLoading = false
                }
            }
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        outdoorPermissionDenied = !granted
        if (granted) {
            locationPreferences.edit().putBoolean("permission_requested", true).apply()
            loadCurrentOutdoor()
        }
    }
    val requestCurrentOutdoor: () -> Unit = {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
                (locationPermissionRequested &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED)
        ) {
            loadCurrentOutdoor()
        } else {
            showOutdoorDisclosure = true
        }
    }
    val loadCityOutdoor: (String) -> Unit = { city ->
        if (!outdoorLoading && !isSaving && draft.recordedAt == null && city.isNotBlank()) {
            isOutdoorLoading = true
            outdoorError = null
            scope.launch {
                try {
                    onAttachOutdoorForCity(city)
                        .onSuccess { outdoorPermissionDenied = false }
                        .onFailure { error -> outdoorError = error.message ?: localizedText("暂时无法获取这个城市的天气。") }
                } finally {
                    isOutdoorLoading = false
                }
            }
        }
    }
    val dismissKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    val today = LocalDate.now()
    val todayEntryCount = recordedTodayCount ?: entriesOnDate(entries, today)
    val draftHasDetails = draft.tags.isNotEmpty()
    LaunchedEffect(draftHasDetails) {
        if (draftHasDetails) showDetails = true
    }
    LaunchedEffect(revealDetailsRequest) {
        if (revealDetailsRequest > 0) {
            withFrameNanos { }
            detailsAnchor.bringIntoView()
        }
    }
    var showPhotoSourceDialog by rememberSaveable { mutableStateOf(false) }
    var showAddContent by rememberSaveable { mutableStateOf(false) }
    var showRecordedAtOptions by rememberSaveable { mutableStateOf(false) }
    val imagePickers = rememberJournalImagePickers(
        availableSlots = MAX_IMAGES_PER_ENTRY - draft.imageUriStrings.size,
        resultsReady = !isSaving && draftImageResultsReady,
        retainRejectedCapture = true,
        onPendingChange = onDraftImageResultPending,
    ) { uris, access ->
        val accepted = onDraftImagesAdded(uris)
        if (accepted) access.transferAll()
        accepted
    }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showVoiceCapture = true
        } else {
            XikeNotice.makeText(context, localizedText("没有麦克风权限，仍可使用文字和照片记录"), XikeNotice.LENGTH_LONG).show()
        }
    }
    val beginVoiceCapture = {
        dismissKeyboard()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            showVoiceCapture = true
            Unit
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(showVoiceCapture) {
        onVoiceCaptureStateChange(showVoiceCapture)
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = XikeContentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = XikeScreenHorizontalPadding,
                    end = XikeScreenHorizontalPadding,
                    top = XikeScreenVerticalPadding,
                    bottom = XikeScreenVerticalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BrandHeader(
                today = today,
                hasDraft = !draft.isEmpty,
                canDiscard = !isSaving && !showVoiceCapture && pendingDraftAudio == null && !videoServices.draftSaving,
                onDiscard = {
                    dismissKeyboard()
                    showDiscardConfirmation = true
                },
            )
            Column {
                Text(
                    tr("留一刻给自己", "A MOMENT FOR YOURSELF"),
                    style = XikeEyebrowStyle,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    tr("给这一刻，\n留个位置。", "Make room\nfor this moment."),
                    style = XikePageTitleStyle,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    if (todayEntryCount == 0) localizedText("不用解释，先感受自己。")
                    else tr("今天已经停下来听见自己 $todayEntryCount 次。", "You have paused to listen to yourself $todayEntryCount times today."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (dailyPromptSettings.enabled) {
                DailyQuestion(today, dailyPromptSettings.style)
            }

            MoodPicker(
                selectedMood = draft.mood,
                enabled = !isSaving,
                onSelected = onDraftMoodChange,
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(localizedText("写下一点点"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                MomentNoteCard(
                    note = draft.note,
                    enabled = !isSaving,
                    onNoteChange = { if (!isSaving) onDraftNoteChange(it) },
                    onFocusChange = { isNoteFocused = it },
                    placeholderAlpha = 0.56f,
                ) {
                    MomentContentToolbar(
                        recordedAt = draft.recordedAt,
                        enabled = !isSaving,
                        addIconSize = 20.dp,
                        onDone = dismissKeyboard.takeIf { isNoteFocused },
                        onAdd = {
                            dismissKeyboard()
                            showAddContent = true
                        },
                        onChooseTime = {
                            dismissKeyboard()
                            if (draft.recordedAt == null) {
                                showRecordedAtPicker(pickerContext, null, selectRecordedAt)
                            } else {
                                showRecordedAtOptions = true
                            }
                        },
                    )
                }
                val draftSecurityStatus = when {
                    showVoiceCapture -> localizedText("录音结束后将加密保存")
                    pendingDraftAudio != null -> localizedText("录音仍待加密保存")
                    else -> localizedText("草稿自动加密保存在本机")
                }
                Spacer(Modifier.height(8.dp))
                DraftSecurityRow(statusText = draftSecurityStatus)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = XikeShapes.card,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = !isSaving,
                                onClickLabel = if (showDetails) localizedText("收起主题") else localizedText("展开主题"),
                                role = Role.Button,
                            ) {
                                if (showDetails) dismissKeyboard()
                                showDetails = !showDetails
                                if (showDetails) revealDetailsRequest++
                            }
                            .padding(XikeContentCardPadding)
                            .heightIn(min = 42.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(localizedText("再留下一点"), style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (draft.tags.isEmpty()) localizedText("选主题 · 可选")
                                else tr("已选 ${draft.tags.size} 个主题", "${draft.tags.size} topics selected"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            if (showDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (showDetails) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = XikeContentCardPadding),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        )
                        Column(Modifier.bringIntoViewRequester(detailsAnchor).padding(XikeContentCardPadding)) {
                            MomentTopicsHeader(draft.tags.size)
                            Spacer(Modifier.height(8.dp))
                            TopicChoices(draft.tags) { topic ->
                                if (!isSaving) onDraftTagToggle(topic.label)
                            }
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(localizedText("此刻窗外"), style = MaterialTheme.typography.titleSmall)
                OutdoorContextCard(
                    snapshot = draft.outdoor,
                    contentPadding = XikeContentCardPadding,
                    minContentHeight = 42.dp,
                    showLocationIconBackground = true,
                    isBackdated = draft.recordedAt != null,
                    isLoading = outdoorLoading,
                    errorMessage = outdoorError ?: if (outdoorPermissionDenied) {
                        localizedText("没有位置权限，也可以只选择城市。")
                    } else {
                        null
                    },
                    enabled = !isSaving,
                    onAdd = requestCurrentOutdoor,
                    showRefresh = draft.recordedAt == null,
                    onChooseCity = { showOutdoorCityDialog = true },
                    onRemove = {
                        outdoorError = null
                        onClearDraftOutdoor()
                    },
                )
            }

            if (pendingDraftAudio != null || draft.audio != null) {
                val voiceAnchor = remember { BringIntoViewRequester() }
                LaunchedEffect(draft.audio?.fileName) {
                    withFrameNanos { }
                    voiceAnchor.bringIntoView()
                }
                Column(modifier = Modifier.fillMaxWidth().bringIntoViewRequester(voiceAnchor)) {
                    Text(
                        when {
                            pendingDraftAudio != null -> localizedText("收好这段声音")
                            else -> localizedText("这一刻的声音")
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        when {
                            pendingDraftAudio != null && isDraftAudioSaving -> localizedText("正在加密保存到草稿")
                            pendingDraftAudio != null -> localizedText("保存失败，录音仍暂存在本机")
                            else -> localizedText("录音已加密保存在本机")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (pendingDraftAudio != null) {
                        VoicePendingSaveCard(
                            durationMillis = pendingDraftAudio.durationMillis,
                            isSaving = isDraftAudioSaving,
                            errorMessage = draftAudioSaveError,
                            onRetry = onDraftAudioRetry,
                            onDiscard = onDraftAudioDiscard,
                        )
                    } else {
                        draft.audio?.let { audio ->
                            VoicePlaybackCard(
                                audio = audio,
                                openAudio = openAudio,
                                onDelete = if (isSaving) null else onDraftAudioRemoved,
                                onReplace = if (isSaving || isDraftAudioSaving || showVoiceCapture) null else beginVoiceCapture,
                                showBorder = false,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                showDeleteAtTopEnd = true,
                                useActionMenu = true,
                                shape = XikeShapes.inner,
                            )
                        }
                    }
                }
            }

            if (draft.imageUriStrings.isNotEmpty()) {
                val previewAnchor = remember { BringIntoViewRequester() }
                LaunchedEffect(draft.imageUriStrings) {
                    withFrameNanos { }
                    previewAnchor.bringIntoView()
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("这一刻的照片", "Photos of this moment"), style = MaterialTheme.typography.titleSmall)
                    PaperCard {
                        MultiImagePicker(
                            selectedUris = draft.imageUriStrings,
                            firstPhotoModifier = Modifier.bringIntoViewRequester(previewAnchor),
                            onPreview = {
                                dismissKeyboard()
                                previewUri = it
                            },
                            onPick = {
                                if (!isSaving) {
                                    dismissKeyboard()
                                    showPhotoSourceDialog = true
                                }
                            },
                            onRemove = { if (!isSaving) onDraftImageRemoved(it) },
                        )
                    }
                }
            }

            if (videoServices.draftSaving || draft.video != null || draft.pendingVideoUri != null) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("这一刻的视频", "Video of this moment"), style = MaterialTheme.typography.titleSmall)
                    if (videoServices.draftSaving) VideoImportStatus(videoServices.draftProgress)
                    draft.video?.let { video ->
                        val canChangeVideo = !isSaving && !videoServices.draftSaving && draft.pendingVideoUri == null
                        VideoCard(
                            video,
                            onRemove = if (isSaving || videoServices.draftSaving) null else videoServices.removeDraft,
                            onReplace = if (canChangeVideo) chooseVideo else null,
                            useActionMenu = true,
                        )
                    }
                    if (draft.pendingVideoUri != null && !videoServices.draftSaving) {
                        Text(
                            tr("视频导入尚未完成，原视频已保留。", "Video import is incomplete. The previous video is preserved."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutdoorCardAction(tr("重试导入", "Retry import"), !isSaving) { videoServices.addDraft(Uri.parse(draft.pendingVideoUri)) }
                            OutdoorCardAction(tr("取消本次导入", "Cancel this import"), !isSaving, videoServices.cancelPendingDraft)
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
        ) {
            Button(
                onClick = {
                    val mood = draft.mood ?: return@Button
                    if (isSaving || isDraftImageResultPending || outdoorLoading || showVoiceCapture || pendingDraftAudio != null || videoServices.draftSaving || draft.pendingVideoUri != null) return@Button
                    dismissKeyboard()
                    localSaving = true
                    scope.launch {
                        onSave(
                            JournalEntry(
                                createdAt = draft.recordedAt ?: System.currentTimeMillis(),
                                mood = mood,
                                tags = draft.tags.toList(),
                                note = draft.note.trim(),
                                audio = draft.audio,
                                video = draft.video,
                                outdoor = draft.outdoor,
                            ),
                            draft.imageUriStrings.map(Uri::parse),
                        ).onSuccess {
                            showDetails = false
                            if (!saveFeedbackHandledExternally) XikeNotice.makeText(
                                context,
                                if (draft.recordedAt == null) localizedText("这一刻，已经好好收下了") else localizedText("那一刻，已经好好收下了"),
                                XikeNotice.LENGTH_SHORT,
                            ).show()
                        }.onFailure { error ->
                            if (!saveFeedbackHandledExternally) XikeNotice.makeText(context, error.message ?: localizedText("保存失败，请重试"), XikeNotice.LENGTH_LONG).show()
                        }
                        localSaving = false
                    }
                },
                enabled = draft.mood != null && !isSaving && !isDraftImageResultPending && !outdoorLoading && !showVoiceCapture && pendingDraftAudio == null && !videoServices.draftSaving && draft.pendingVideoUri == null,
                modifier = Modifier
                    .widthIn(max = XikeContentMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = XikeScreenHorizontalPadding, vertical = 8.dp)
                    .heightIn(min = 48.dp),
                shape = XikeShapes.button,
                elevation = xikeButtonElevation(),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                ),
            ) {
                Text(
                    when {
                        isSaving -> localizedText("正在收下…")
                        outdoorLoading -> tr("正在获取地点与天气…", "Getting location and weather…")
                        showVoiceCapture -> localizedText("请先完成录音")
                        pendingDraftAudio != null -> if (isDraftAudioSaving) localizedText("正在保存语音…") else localizedText("请先处理录音")
                        videoServices.draftSaving -> tr("正在导入视频…", "Importing video…")
                        draft.pendingVideoUri != null -> tr("请先处理视频", "Please resolve the video import")
                        draft.mood == null -> localizedText("先选择一种心情")
                        draft.recordedAt != null -> localizedText("补记这一刻")
                        else -> localizedText("记下此刻")
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }

    if (showVoiceCapture) {
        VoiceCaptureSheet(
            enabled = !isSaving,
            onRecorded = onDraftAudioRecorded,
            onClosed = { showVoiceCapture = false },
        )
    }

    previewUri?.let { uri ->
        if (uri in draft.imageUriStrings) {
            PhotoGalleryDialog(
                fileNames = draft.imageUriStrings,
                initialPage = draft.imageUriStrings.indexOf(uri),
                openImage = { context.contentResolver.openInputStream(Uri.parse(it)) },
                onDismiss = { previewUri = null },
            )
        }
    }

    if (showRecordedAtOptions) {
        AlertDialog(
            onDismissRequest = { showRecordedAtOptions = false },
            shape = XikeShapes.dialog,
            title = { Text(localizedText("记录时间")) },
            text = {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = {
                            showRecordedAtOptions = false
                            showRecordedAtPicker(pickerContext, draft.recordedAt, selectRecordedAt)
                        },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        shape = XikeShapes.button,
                    ) { Text(tr("修改日期时间", "Change date and time")) }
                    TextButton(
                        onClick = {
                            showRecordedAtOptions = false
                            selectRecordedAt(null)
                        },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        shape = XikeShapes.button,
                    ) { Text(localizedText("改为现在")) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRecordedAtOptions = false }, shape = XikeShapes.button) {
                    Text(localizedText("取消"))
                }
            },
        )
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
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = XikeSheetHorizontalPadding).padding(bottom = XikeSheetBottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    tr("添加内容", "Add content"),
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                MomentMediaAction(
                    tr("录音", "Record audio"),
                    !isSaving && draft.audio == null && pendingDraftAudio == null && !showVoiceCapture,
                ) {
                    showAddContent = false
                    beginVoiceCapture()
                }
                MomentMediaAction(
                    localizedText("照片"),
                    !isSaving && draft.imageUriStrings.size < MAX_IMAGES_PER_ENTRY,
                ) {
                    showAddContent = false
                    showPhotoSourceDialog = true
                }
                MomentMediaAction(
                    tr("视频", "Video"),
                    !isSaving && !videoServices.draftSaving,
                ) {
                    showAddContent = false
                    chooseVideo()
                }
            }
        }
    }

    if (showPhotoSourceDialog) {
        PhotoSourceDialog(
            cameraAvailable = canTakePhoto(context),
            onTakePhoto = {
                showPhotoSourceDialog = false
                imagePickers.openCamera()
            },
            onChoosePhotos = {
                showPhotoSourceDialog = false
                imagePickers.openPhotoPicker()
            },
            onDismiss = { showPhotoSourceDialog = false },
        )
    }

    if (showDiscardConfirmation) {
        DestructiveConfirmationDialog(
            onDismiss = { showDiscardConfirmation = false },
            title = localizedText("放弃这份草稿？"),
            dismissText = localizedText("继续保留"),
            confirmText = localizedText("放弃草稿"),
            onConfirm = {
                onDraftDiscard()
                showDiscardConfirmation = false
                showDetails = false
            },
            text = {
                Text(
                    localizedText("将清空尚未保存的心情、此刻窗外、注脚、主题、照片、语音、视频和补记时间。已保存的记录不会受影响。"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }

    if (showRecordedAtChangeConfirmation) {
        AlertDialog(
            onDismissRequest = { showRecordedAtChangeConfirmation = false },
            shape = XikeShapes.dialog,
            title = { Text(tr("修改记录日期？", "Change the entry date?")) },
            text = {
                Text(
                    tr("改为其他日期会移除已添加的地点与天气；改回现在也不会自动恢复。", "Changing to another date removes the added location and weather. Returning to now will not restore them automatically."),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!isSaving) onDraftRecordedAtChange(pendingRecordedAtChange)
                    showRecordedAtChangeConfirmation = false
                }, enabled = !isSaving, shape = XikeShapes.button) { Text(tr("修改日期", "Change date")) }
            },
            dismissButton = {
                TextButton(onClick = { showRecordedAtChangeConfirmation = false }, shape = XikeShapes.button) { Text(localizedText("取消")) }
            },
        )
    }

    if (showOutdoorDisclosure) {
        AlertDialog(
            onDismissRequest = { showOutdoorDisclosure = false },
            shape = XikeShapes.dialog,
            title = { Text(localizedText("添加此刻窗外？")) },
            text = {
                Text(
                    localizedText("息刻只会在你点击后获取一次位置，用于显示区、街道；也可以只允许粗略定位。系统地址服务会使用坐标查询地点，粗略经纬度会发送给 Open-Meteo 查询天气。") +
                        localizedText("经纬度不会保存，也不会上传日记内容；地点和天气快照会随草稿与日记加密保存在本机。"),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            dismissButton = {
                TextButton(
                    shape = XikeShapes.button,
                    onClick = {
                        showOutdoorDisclosure = false
                        showOutdoorCityDialog = true
                    },
                ) { Text(localizedText("手动选城市")) }
            },
            confirmButton = {
                TextButton(
                    shape = XikeShapes.button,
                    onClick = {
                        showOutdoorDisclosure = false
                        locationPermissionRequested = true
                        locationPermissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ))
                    },
                ) { Text(localizedText("允许定位")) }
            },
        )
    }

    if (showOutdoorCityDialog) {
        OutdoorCityDialog(
            onDismiss = { showOutdoorCityDialog = false },
            onConfirm = { city ->
                showOutdoorCityDialog = false
                loadCityOutdoor(city)
            },
        )
    }
}


@Composable
private fun DraftSecurityRow(
    statusText: String,
) {
    val defaultFirstLineHeight = with(LocalDensity.current) {
        MaterialTheme.typography.bodySmall.lineHeight.toDp()
    }
    var firstLineCenter by remember(statusText, defaultFirstLineHeight) {
        mutableStateOf<Float?>(null)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier.height(defaultFirstLineHeight).alignBy { it.measuredHeight / 2 },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            statusText,
            modifier = Modifier.weight(1f).alignBy {
                firstLineCenter?.roundToInt() ?: (it.measuredHeight / 2)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            onTextLayout = { layout ->
                firstLineCenter = (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f
            },
        )
    }
}


@Composable
private fun BrandHeader(
    today: LocalDate,
    hasDraft: Boolean,
    canDiscard: Boolean,
    onDiscard: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(32.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    XikeIcons.Mark,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(localizedText("息刻"), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(8.dp))
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                today.format(DateTimeFormatter.ofPattern(tr("M月d日 · E", "MMM d · E"), AppLocale.locale)),
                modifier = Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasDraft) {
                TextButton(
                    onClick = onDiscard,
                    enabled = canDiscard,
                    modifier = Modifier.heightIn(min = 48.dp).align(Alignment.CenterVertically),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(localizedText("放弃草稿"))
                }
            }
        }
    }
}


@Composable
private fun MultiImagePicker(
    selectedUris: List<String>,
    onPick: () -> Unit,
    onRemove: (String) -> Unit,
    onPreview: (String) -> Unit,
    firstPhotoModifier: Modifier = Modifier,
) {
    if (selectedUris.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onPick),
            shape = XikeShapes.inner,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        ) {
            Row(Modifier.padding(XikeInnerCardPadding), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(38.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(localizedText("添加照片"), style = MaterialTheme.typography.titleSmall)
                    Text(tr("最多 $MAX_IMAGES_PER_ENTRY 张 · 仅保存在本机", "Up to $MAX_IMAGES_PER_ENTRY photos · Stored only on this device"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(localizedText("加密保存在本机"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    "${selectedUris.size} / $MAX_IMAGES_PER_ENTRY",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val tiles = buildList {
            addAll(selectedUris)
            if (selectedUris.size < MAX_IMAGES_PER_ENTRY) add(ADD_PHOTO_TILE)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(3).forEach { rowTiles ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowTiles.forEach { tile ->
                        if (tile == ADD_PHOTO_TILE) {
                            AddPhotoTile(
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                                label = localizedText("继续添加"),
                                onClick = onPick,
                            )
                        } else {
                            SelectedPhotoTile(
                                uriString = tile,
                                order = selectedUris.indexOf(tile) + 1,
                                onRemove = { onRemove(tile) },
                                onPreview = { onPreview(tile) },
                                modifier = Modifier.weight(1f).aspectRatio(1f)
                                    .then(if (tile == selectedUris.first()) firstPhotoModifier else Modifier),
                            )
                        }
                    }
                    repeat(3 - rowTiles.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}


@Composable
private fun SelectedPhotoTile(
    uriString: String,
    order: Int,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = rememberPreviewBitmap(uriString) {
        context.contentResolver.openInputStream(Uri.parse(uriString))
    }
    Box(
        modifier = modifier.clip(XikeShapes.inner)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = localizedText("预览照片"), onClick = onPreview)
            .semantics { contentDescription = tr("待保存的第 $order 张照片", "Unsaved photo $order") },
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(7.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.62f),
        ) {
            Text(
                "$order",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .semantics { contentDescription = tr("移除第 $order 张照片", "Remove photo $order") },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shadowElevation = 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyQuestion(today: LocalDate, style: DailyPromptStyle) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = XikeShapes.card, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)) {
        Row(Modifier.padding(XikeCardPadding), verticalAlignment = Alignment.Top) {
            Icon(XikeIcons.Mark, contentDescription = null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(11.dp))
            Column {
                Text(localizedText("今日一刻"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(dailyQuestion(today, style), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
