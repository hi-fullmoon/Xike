package com.xike.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Commute
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Interests
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.InputStream
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ADD_PHOTO_TILE = "__add_photo__"

enum class AppScreen(val title: String) {
    HOME("此刻"),
    INSIGHTS("轨迹"),
    ARCHIVE("回望"),
    SETTINGS("设置"),
}

enum class AppTheme(
    val title: String,
    val subtitle: String,
    val primary: Color,
    val accent: Color,
    val secondary: Color,
) {
    PINE("雾松", "沉静的森林绿", Color(0xFF34584A), Color(0xFFDDEAE2), Color(0xFFF0E8D9)),
    VIOLET("暮紫", "柔和的暮色紫", Color(0xFF66567E), Color(0xFFEAE3F0), Color(0xFFF1E4DC)),
    OCEAN("潮汐", "克制的雾蓝绿", Color(0xFF355E5B), Color(0xFFDFECE8), Color(0xFFF0E5D6)),
    TERRA("陶日", "温暖的赤陶色", Color(0xFF925B49), Color(0xFFF2E3DC), Color(0xFFE8EBDD)),
}

internal data class JournalTopic(val label: String, val accent: Color, val icon: ImageVector)

internal val journalTopics = listOf(
    JournalTopic("工作", Color(0xFF56758B), Icons.Outlined.BusinessCenter),
    JournalTopic("学习", Color(0xFF766B98), Icons.AutoMirrored.Outlined.MenuBook),
    JournalTopic("关系", Color(0xFF9A6173), Icons.Outlined.FavoriteBorder),
    JournalTopic("家庭", Color(0xFF976A50), Icons.Outlined.Home),
    JournalTopic("身体", Color(0xFF568063), Icons.Outlined.SelfImprovement),
    JournalTopic("睡眠", Color(0xFF637197), Icons.Outlined.Bedtime),
    JournalTopic("饮食", Color(0xFFA87548), Icons.Outlined.Restaurant),
    JournalTopic("运动", Color(0xFF4F866B), Icons.AutoMirrored.Outlined.DirectionsRun),
    JournalTopic("金钱", Color(0xFF987838), Icons.Outlined.AccountBalanceWallet),
    JournalTopic("自我", Color(0xFF447E78), Icons.Outlined.PersonOutline),
    JournalTopic("兴趣", Color(0xFF8A6190), Icons.Outlined.Interests),
    JournalTopic("社交", Color(0xFF5E719A), Icons.Outlined.Groups),
    JournalTopic("出行", Color(0xFF4C7F8C), Icons.Outlined.Commute),
    JournalTopic("居住", Color(0xFF8B7057), Icons.Outlined.HomeWork),
    JournalTopic("创作", Color(0xFF9A626C), Icons.Outlined.Brush),
    JournalTopic("其他", Color(0xFF6F766F), Icons.Outlined.MoreHoriz),
)

object XikeShapes {
    val card = RoundedCornerShape(28.dp)
    val inner = RoundedCornerShape(20.dp)
    val button = RoundedCornerShape(20.dp)
    val dialog = RoundedCornerShape(30.dp)
}

private val XikeTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 42.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 44.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable
fun XikeTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val colors: ColorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = theme.accent,
            onPrimary = Color(0xFF18352B),
            primaryContainer = theme.primary.copy(alpha = 0.58f),
            onPrimaryContainer = Color(0xFFF4FAF5),
            secondaryContainer = theme.secondary.copy(alpha = 0.18f),
            onSecondaryContainer = Color(0xFFF2EEE6),
            background = Color(0xFF121614),
            onBackground = Color(0xFFE8E9E4),
            surface = Color(0xFF1B211E),
            onSurface = Color(0xFFE8E9E4),
            surfaceVariant = Color(0xFF282D29),
            onSurfaceVariant = Color(0xFFB8BDB7),
            outline = Color(0xFF68706A),
            outlineVariant = Color(0xFF363C37),
        )
    } else {
        lightColorScheme(
            primary = theme.primary,
            onPrimary = Color.White,
            primaryContainer = theme.accent,
            onPrimaryContainer = Color(0xFF183229),
            secondaryContainer = theme.secondary,
            onSecondaryContainer = Color(0xFF3D372E),
            background = Color(0xFFF7F6F1),
            onBackground = Color(0xFF20231F),
            surface = Color(0xFFFFFEFB),
            onSurface = Color(0xFF20231F),
            surfaceVariant = Color(0xFFEAE7E0),
            onSurfaceVariant = Color(0xFF626761),
            outline = Color(0xFF7C817B),
            outlineVariant = Color(0xFFDADDD6),
        )
    }
    MaterialTheme(colorScheme = colors, typography = XikeTypography, content = content)
}

@Composable
internal fun xikeButtonElevation(): ButtonElevation = ButtonDefaults.buttonElevation(
    defaultElevation = 0.dp,
    pressedElevation = 0.dp,
    focusedElevation = 0.dp,
    hoveredElevation = 0.dp,
    disabledElevation = 0.dp,
)

@Composable
fun XikeNavigationBar(selected: AppScreen, onSelected: (AppScreen) -> Unit) {
    Surface(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 5.dp,
            tonalElevation = 0.dp,
        ) {
            Row(modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp).selectableGroup()) {
                AppScreen.entries.forEach { item ->
                    val isSelected = selected == item
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f)
                                else Color.Transparent,
                            )
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                onClick = { onSelected(item) },
                            )
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = when (item) {
                                AppScreen.HOME -> XikeIcons.Moment
                                AppScreen.INSIGHTS -> XikeIcons.Insights
                                AppScreen.ARCHIVE -> XikeIcons.Archive
                                AppScreen.SETTINGS -> XikeIcons.Settings
                            },
                            contentDescription = null,
                            modifier = Modifier.size(21.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppLockDialog(
    authenticationAvailable: Boolean,
    onUnlock: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        AppLockScreen(
            authenticationAvailable = authenticationAvailable,
            onUnlock = onUnlock,
            onOpenSecuritySettings = onOpenSecuritySettings,
        )
    }
}

@Composable
fun AppLockScreen(
    authenticationAvailable: Boolean,
    onUnlock: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 32.dp, vertical = 28.dp),
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.size(86.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(38.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("息刻已锁定", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (authenticationAvailable) {
                        "验证身份后，回到只属于你的留白。"
                    } else {
                        "需要先在系统中设置屏幕锁，才能继续验证身份。"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = if (authenticationAvailable) onUnlock else onOpenSecuritySettings,
                    shape = XikeShapes.button,
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                    elevation = xikeButtonElevation(),
                ) {
                    Icon(
                        if (authenticationAvailable) Icons.Outlined.LockOpen else Icons.Outlined.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(if (authenticationAvailable) "解锁息刻" else "设置设备锁屏")
                }
            }

            Text(
                "身份信息仅由 Android 系统验证，息刻不会读取或保存。",
                modifier = Modifier.align(Alignment.BottomCenter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MomentScreen(
    padding: PaddingValues,
    entries: List<JournalEntry>,
    draft: JournalDraft,
    dailyPromptSettings: DailyPromptSettings,
    onDraftMoodChange: (Mood?) -> Unit,
    onDraftNoteChange: (String) -> Unit,
    onDraftTagToggle: (String) -> Unit,
    onDraftImagesAdded: (List<Uri>) -> Unit,
    onDraftImageRemoved: (String) -> Unit,
    onSave: suspend (JournalEntry, List<Uri>) -> Result<Unit>,
    onDraftRecordedAtChange: (Long?) -> Unit = {},
    onDraftDiscard: () -> Unit = {},
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showDiscardConfirmation by rememberSaveable { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isNoteFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val dismissKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    val today = LocalDate.now()
    val todayEntryCount = entriesOnDate(entries, today)
    val draftHasDetails = draft.note.isNotBlank() ||
        draft.tags.isNotEmpty() ||
        draft.imageUriStrings.isNotEmpty() ||
        draft.recordedAt != null
    LaunchedEffect(draftHasDetails) {
        if (draftHasDetails) showDetails = true
    }
    val onImagesPicked: (List<Uri>) -> Unit = { uris ->
        onDraftImagesAdded(uris)
        if (uris.isNotEmpty()) showDetails = true
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES_PER_ENTRY),
        onImagesPicked,
    )
    val photoDocumentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
        onImagesPicked,
    )

    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BrandHeader(today)
            Spacer(Modifier.height(6.dp))
            Text("给此刻，一种天气", style = MaterialTheme.typography.headlineLarge)
            Text(
                if (todayEntryCount == 0) "不用解释，先看看心里是什么天气。"
                else "今天已经停下来听见自己 $todayEntryCount 次。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            WeatherPicker(
                selectedMood = draft.mood,
                enabled = !isSaving,
                onSelected = onDraftMoodChange,
            )

            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    if (showDetails) dismissKeyboard()
                    showDetails = !showDetails
                },
                shape = XikeShapes.inner,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("再留下一点", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (draft.isEmpty) "写句话、选关键词、加照片或补记过去"
                            else "草稿已加密保存在本机",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (showDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (showDetails) "收起更多内容" else "展开更多内容",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (showDetails) {
                PaperCard {
                    DraftSecurityRow(
                        hasDraft = !draft.isEmpty,
                        enabled = !isSaving,
                        onDiscard = { showDiscardConfirmation = true },
                    )
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    RecordedAtSelector(
                        recordedAt = draft.recordedAt,
                        enabled = !isSaving,
                        onChoose = {
                            dismissKeyboard()
                            showRecordedAtPicker(context, draft.recordedAt, onDraftRecordedAtChange)
                        },
                        onReset = { onDraftRecordedAtChange(null) },
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("此刻的注脚", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = draft.note,
                        onValueChange = { if (!isSaving) onDraftNoteChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isNoteFocused = it.isFocused },
                        minLines = 3,
                        maxLines = 5,
                        placeholder = { Text("发生了什么？也可以只留下一句话……") },
                        shape = XikeShapes.inner,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (draft.note.isEmpty()) "" else "${draft.note.length} / $MAX_DRAFT_NOTE_LENGTH",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isNoteFocused) {
                            TextButton(onClick = dismissKeyboard) {
                                Icon(Icons.Outlined.KeyboardHide, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("完成")
                            }
                        }
                    }
                    Spacer(Modifier.height(if (isNoteFocused) 4.dp else 12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("此刻关键词", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "这一刻与什么有关？",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (draft.tags.isEmpty()) "可多选" else "已选 ${draft.tags.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (draft.tags.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        journalTopics.chunked(4).forEach { rowTopics ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                rowTopics.forEach { topic ->
                                    TopicChip(
                                        topic = topic,
                                        selected = topic.label in draft.tags,
                                        onClick = { if (!isSaving) onDraftTagToggle(topic.label) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))
                    MultiImagePicker(
                        selectedUris = draft.imageUriStrings,
                        onPick = {
                            if (!isSaving) {
                                dismissKeyboard()
                                val openDocuments = {
                                    val contract = ActivityResultContracts.OpenMultipleDocuments()
                                    val input = arrayOf("image/*")
                                    val canOpen = contract.createIntent(context, input)
                                        .resolveActivity(context.packageManager) != null
                                    if (canOpen) photoDocumentPicker.launch(input)
                                    canOpen
                                }
                                val opened = if (
                                    ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)
                                ) {
                                    runCatching {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                        true
                                    }.onFailure { error ->
                                        Log.w("XikePhotoPicker", "Photo picker launch failed", error)
                                    }.getOrElse {
                                        runCatching(openDocuments).onFailure { error ->
                                            Log.w("XikePhotoPicker", "Document picker launch failed", error)
                                        }.getOrDefault(false)
                                    }
                                } else {
                                    runCatching(openDocuments).onFailure { error ->
                                        Log.w("XikePhotoPicker", "Document picker launch failed", error)
                                    }.getOrDefault(false)
                                }
                                if (!opened) {
                                    Toast.makeText(context, "无法打开系统照片选择器", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onRemove = { if (!isSaving) onDraftImageRemoved(it) },
                    )
                }
            }

            if (dailyPromptSettings.enabled) {
                DailyQuestion(today, dailyPromptSettings.style)
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
        ) {
            Button(
                onClick = {
                    val mood = draft.mood ?: return@Button
                    if (isSaving) return@Button
                    dismissKeyboard()
                    isSaving = true
                    scope.launch {
                        onSave(
                            JournalEntry(
                                createdAt = draft.recordedAt ?: System.currentTimeMillis(),
                                mood = mood,
                                tags = draft.tags.toList(),
                                note = draft.note.trim(),
                            ),
                            draft.imageUriStrings.map(Uri::parse),
                        ).onSuccess {
                            showDetails = false
                            Toast.makeText(
                                context,
                                if (draft.recordedAt == null) "这一刻，已经好好收下了" else "那一刻，已经好好收下了",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }.onFailure { error ->
                            Toast.makeText(context, error.message ?: "保存失败，请重试", Toast.LENGTH_LONG).show()
                        }
                        isSaving = false
                    }
                },
                enabled = draft.mood != null && !isSaving,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp).height(52.dp),
                shape = XikeShapes.button,
                elevation = xikeButtonElevation(),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                ),
            ) {
                Text(
                    when {
                        isSaving -> "正在收下…"
                        draft.mood == null -> "先选择一种天气"
                        draft.recordedAt != null -> "补记这一刻"
                        else -> "记下此刻"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                if (draft.mood != null && !isSaving) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(19.dp))
                }
            }
        }
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            shape = XikeShapes.dialog,
            title = { Text("放弃这份草稿？") },
            text = {
                Text(
                    "将清空尚未保存的内在天气、注脚、关键词、照片和补记时间。已保存的记录不会受影响。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) { Text("继续保留") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDraftDiscard()
                        showDiscardConfirmation = false
                        showDetails = false
                    },
                ) { Text("放弃草稿") }
            },
        )
    }
}

@Composable
private fun DraftSecurityRow(
    hasDraft: Boolean,
    enabled: Boolean,
    onDiscard: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("草稿自动保存在本机", style = MaterialTheme.typography.titleSmall)
            Text(
                "未完成内容不会离开设备",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (hasDraft) {
            TextButton(onClick = onDiscard, enabled = enabled) { Text("清空") }
        }
    }
}

@Composable
private fun RecordedAtSelector(
    recordedAt: Long?,
    enabled: Boolean,
    onChoose: () -> Unit,
    onReset: () -> Unit,
) {
    Text("记录时间", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onChoose),
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    recordedAt?.asDraftMomentLabel() ?: "就在此刻",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (recordedAt == null) "保存时使用当前时间 · 点按可补记" else "将归入所选日期 · 点按可修改",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (recordedAt != null) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "补记也会参与对应日期的回望与轨迹",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onReset, enabled = enabled) { Text("改为现在") }
        }
    }
}

internal fun showRecordedAtPicker(
    context: Context,
    recordedAt: Long?,
    onSelected: (Long) -> Unit,
) {
    val nowMillis = System.currentTimeMillis()
    val zoneId = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
    val initial = recordedAt
        ?.coerceAtMost(nowMillis)
        ?.let { Instant.ofEpochMilli(it).atZone(zoneId) }
        ?: now

    DatePickerDialog(
        context,
        { _, year, month, day ->
            val date = LocalDate.of(year, month + 1, day)
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val selected = date.atTime(hour, minute).atZone(zoneId).toInstant().toEpochMilli()
                    if (selected > System.currentTimeMillis()) {
                        Toast.makeText(context, "记录时间不能晚于现在", Toast.LENGTH_SHORT).show()
                    } else {
                        onSelected(selected)
                    }
                },
                initial.hour,
                initial.minute,
                true,
            ).show()
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).apply {
        datePicker.maxDate = nowMillis
    }.show()
}

private fun Long.asDraftMomentLabel(zoneId: ZoneId = ZoneId.systemDefault()): String {
    val moment = Instant.ofEpochMilli(this).atZone(zoneId)
    val today = LocalDate.now(zoneId)
    val dateLabel = when (moment.toLocalDate()) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> DateTimeFormatter.ofPattern(
            if (moment.year == today.year) "M月d日 EEEE" else "yyyy年M月d日 EEEE",
            Locale.CHINA,
        ).format(moment)
    }
    return "$dateLabel ${DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA).format(moment)}"
}

@Composable
private fun BrandHeader(today: LocalDate) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    XikeIcons.Mark,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("息刻", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Text(today.asChineseDay(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeatherPicker(
    selectedMood: Mood?,
    enabled: Boolean,
    onSelected: (Mood?) -> Unit,
) {
    var showGuide by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = XikeShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("内在天气", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "用天气比喻感受，没有标准答案",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { showGuide = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) { Text("看看含义") }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Mood.entries.forEach { mood ->
                    MoodChoice(
                        mood = mood,
                        selected = selectedMood == mood,
                        enabled = enabled,
                        onClick = { onSelected(mood) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (selectedMood != null) {
                Spacer(Modifier.height(12.dp))
                val visual = selectedMood.visualStyle()
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = visual.container.copy(alpha = if (isSystemInDarkTheme()) 0.16f else 0.62f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(visual.accent))
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "${selectedMood.label} · ${selectedMood.weatherDescription()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showGuide) {
        WeatherGuideDialog(onDismiss = { showGuide = false })
    }
}

@Composable
private fun WeatherGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        title = { Text("五种天气，怎么选？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "选择最接近此刻的一种即可，同一种感受也可能有不同的天气。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Mood.entries.forEach { mood ->
                    val visual = mood.visualStyle()
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = visual.container.copy(alpha = if (isSystemInDarkTheme()) 0.16f else 0.58f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                mood.weatherIcon(),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = visual.accent,
                            )
                            Spacer(Modifier.width(11.dp))
                            Column {
                                Text(mood.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    mood.weatherDescription(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

@Composable
private fun MoodChoice(
    mood: Mood,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = mood.visualStyle()
    val isDark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor = when {
        selected && isDark -> visual.accent.copy(alpha = 0.28f)
        selected -> visual.container
        isDark -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        else -> visual.container.copy(alpha = 0.45f)
    }

    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(50.dp),
            shape = RoundedCornerShape(17.dp),
            color = containerColor,
            border = BorderStroke(
                if (selected) 1.5.dp else 1.dp,
                if (selected) visual.accent else MaterialTheme.colorScheme.outlineVariant,
            ),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    mood.weatherIcon(),
                    contentDescription = mood.label,
                    modifier = Modifier.size(25.dp),
                    tint = visual.accent.copy(alpha = if (selected) 1f else 0.78f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            mood.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) visual.accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun TopicChip(
    topic: JournalTopic,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) topic.accent.copy(alpha = if (isDark) 0.26f else 0.13f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        border = BorderStroke(
            1.dp,
            if (selected) topic.accent.copy(alpha = 0.58f) else Color.Transparent,
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = topic.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = topic.accent.copy(alpha = if (selected) 1f else 0.72f),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                topic.label,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    selected && isDark -> MaterialTheme.colorScheme.onSurface
                    selected -> topic.accent
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun MultiImagePicker(
    selectedUris: List<String>,
    onPick: () -> Unit,
    onRemove: (String) -> Unit,
) {
    if (selectedUris.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onPick),
            shape = XikeShapes.inner,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        ) {
            Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(38.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("添加照片", style = MaterialTheme.typography.titleSmall)
                    Text("最多 $MAX_IMAGES_PER_ENTRY 张 · 仅保存在本机", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("照片", style = MaterialTheme.typography.titleSmall)
                Text("加密保存在本机", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
                            AddPhotoTile(Modifier.weight(1f).aspectRatio(1f), onPick)
                        } else {
                            SelectedPhotoTile(
                                uriString = tile,
                                order = selectedUris.indexOf(tile) + 1,
                                onRemove = { onRemove(tile) },
                                modifier = Modifier.weight(1f).aspectRatio(1f),
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
private fun AddPhotoTile(modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text("继续添加", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SelectedPhotoTile(
    uriString: String,
    order: Int,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = rememberPreviewBitmap(uriString) {
        context.contentResolver.openInputStream(Uri.parse(uriString))
    }
    Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "待保存的第 $order 张照片",
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
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shadowElevation = 0.dp,
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "移除第 $order 张照片", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun DailyQuestion(today: LocalDate, style: DailyPromptStyle) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = XikeShapes.inner, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(XikeIcons.Mark, contentDescription = null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(11.dp))
            Column {
                Text("今日一刻", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(dailyQuestion(today, style), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun WeeklyInsightsScreen(padding: PaddingValues, entries: List<JournalEntry>) {
    var selectedPeriodName by rememberSaveable { mutableStateOf(InsightsPeriod.WEEK.name) }
    val selectedPeriod = InsightsPeriod.entries.firstOrNull { it.name == selectedPeriodName } ?: InsightsPeriod.WEEK
    val today = LocalDate.now()
    val summary = remember(entries, selectedPeriod, today) {
        journalPeriodSummary(entries, selectedPeriod, today)
    }

    ScreenColumn(padding) {
        ScreenHeader(
            eyebrow = summary.startDate.asDateRange(summary.endDate),
            title = "洞察",
            supporting = "看见变化，而不是评判自己。",
        )

        InsightsPeriodSelector(
            selected = selectedPeriod,
            onSelected = { selectedPeriodName = it.name },
        )

        Surface(modifier = Modifier.fillMaxWidth(), shape = XikeShapes.card, color = MaterialTheme.colorScheme.primary) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${selectedPeriod.contextName}概览",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.13f)) {
                        Text(
                            "${summary.entryCount} 次 · ${summary.recordedDayCount} 天",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    summary.averageScore?.let(::weatherBandLabel) ?: "等待第一条记录",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    summary.averageScore?.let(::weatherSummary) ?: "记录第一片天气，让趋势从这里开始。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                )
                summary.averageScore?.let { average ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "天气平均位置 ${String.format(Locale.CHINA, "%.1f", average)} / 5",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f),
                    )
                }
            }
        }

        PaperCard {
            SectionTitle(
                index = "趋势",
                title = selectedPeriod.trendTitle,
                trailing = if (selectedPeriod == InsightsPeriod.YEAR) "按月" else null,
            )
            Spacer(Modifier.height(14.dp))
            if (summary.entryCount < 3) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = XikeShapes.inner,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(XikeIcons.Mark, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("还需要一点时间", style = MaterialTheme.typography.titleSmall)
                            Text(
                                when (summary.entryCount) {
                                    0 -> "记录第一刻后，这里会开始描出变化。"
                                    else -> "再记录 ${3 - summary.entryCount} 次，就能看到更清晰的趋势。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (summary.trendPoints.size > 9) 3.dp else 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    summary.trendPoints.forEach { point ->
                        val isCurrent = !today.isBefore(point.startDate) && today.isBefore(point.endDateExclusive)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Text(
                                point.entryCount.takeIf { it > 0 }?.toString().orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                modifier = Modifier
                                    .width(if (summary.trendPoints.size > 9) 13.dp else 22.dp)
                                    .height(point.averageScore?.let { (14 + it * 9).dp } ?: 5.dp),
                                shape = CircleShape,
                                color = if (point.averageScore == null) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.primary.copy(alpha = if (isCurrent) 1f else 0.48f),
                            ) {}
                            Spacer(Modifier.height(8.dp))
                            Text(
                                point.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (summary.trendPoints.size > 9) 10.sp else 11.sp,
                                ),
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        if (summary.entryCount > 0) {
            InsightCard(
                icon = Icons.Outlined.LocalOffer,
                label = summary.mostUsedTag ?: "还没有高频关键词",
                content = if (summary.mostUsedTag == null) "添加此刻关键词后，这里会帮你发现反复出现的线索。"
                else "“${summary.mostUsedTag}”是${selectedPeriod.contextName}最常出现的关键词，或许值得多留意一点。",
            )
        }
    }
}

@Composable
private fun InsightsPeriodSelector(
    selected: InsightsPeriod,
    onSelected: (InsightsPeriod) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    ) {
        Row(
            modifier = Modifier.padding(4.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            InsightsPeriod.entries.forEach { period ->
                val isSelected = selected == period
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                        )
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelected(period) },
                        )
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        period.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightCard(icon: ImageVector, label: String, content: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = XikeShapes.card, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
            Surface(modifier = Modifier.size(42.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(5.dp))
                Text(content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun DateSectionHeader(date: LocalDate, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)),
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(date.asFullWeekday(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text("$count 条", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun JournalEntryCard(
    entry: JournalEntry,
    modifier: Modifier = Modifier,
    openImage: (String) -> InputStream?,
    onImageClick: (Int) -> Unit,
    onClick: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            if (entry.imageFileNames.isNotEmpty()) {
                JournalPhotoMosaic(entry.imageFileNames, openImage, onImageClick)
            }
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
                Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.76f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            entry.mood.weatherIcon(),
                            contentDescription = entry.mood.label,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.mood.label, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        Text(entry.createdAt.asChineseTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (entry.tags.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(entry.tags.joinToString("  ·  "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (entry.note.isNotBlank()) {
                        Spacer(Modifier.height(9.dp))
                        Text(entry.note, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalPhotoMosaic(
    fileNames: List<String>,
    openImage: (String) -> InputStream?,
    onImageClick: (Int) -> Unit,
) {
    when (fileNames.size) {
        1 -> SavedJournalImage(
            fileName = fileNames.first(),
            openStream = { openImage(fileNames.first()) },
            modifier = Modifier.fillMaxWidth().aspectRatio(1.75f),
            order = 1,
            onClick = { onImageClick(0) },
        )
        2 -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            fileNames.forEachIndexed { index, fileName ->
                SavedJournalImage(
                    fileName = fileName,
                    openStream = { openImage(fileName) },
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    order = index + 1,
                    onClick = { onImageClick(index) },
                )
            }
        }
        else -> Row(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val first = fileNames[0]
            SavedJournalImage(
                fileName = first,
                openStream = { openImage(first) },
                modifier = Modifier.weight(1.35f).fillMaxHeight(),
                order = 1,
                onClick = { onImageClick(0) },
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                fileNames.take(3).drop(1).forEachIndexed { index, fileName ->
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        SavedJournalImage(
                            fileName = fileName,
                            openStream = { openImage(fileName) },
                            modifier = Modifier.fillMaxSize(),
                            order = index + 2,
                            onClick = { onImageClick(index + 1) },
                        )
                        if (index == 1 && fileNames.size > 3) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "+${fileNames.size - 3}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedJournalImage(
    fileName: String,
    openStream: () -> InputStream?,
    modifier: Modifier,
    order: Int,
    onClick: (() -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Crop,
    maxDimension: Int = 960,
) {
    val bitmap = rememberPreviewBitmap(fileName, maxDimension, openStream)
    val interactiveModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Box(
        modifier = interactiveModifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "日记第 $order 张照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PhotoGalleryDialog(
    fileNames: List<String>,
    initialPage: Int,
    openImage: (String) -> InputStream?,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, fileNames.lastIndex),
        pageCount = { fileNames.size },
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val fileName = fileNames[page]
                    SavedJournalImage(
                        fileName = fileName,
                        openStream = { openImage(fileName) },
                        modifier = Modifier.fillMaxSize(),
                        order = page + 1,
                        contentScale = ContentScale.Fit,
                        maxDimension = 2048,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.56f)) {
                        Text(
                            "${pagerState.currentPage + 1} / ${fileNames.size}",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.56f)) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "关闭图片查看", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileSettingsScreen(
    padding: PaddingValues,
    selectedTheme: AppTheme,
    appLockEnabled: Boolean,
    appLockTimeout: AppLockTimeout,
    authenticationAvailable: Boolean,
    reminderSettings: ReminderSettings,
    dailyPromptSettings: DailyPromptSettings,
    notificationPermissionGranted: Boolean,
    onThemeChange: (AppTheme) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onAppLockTimeoutChange: (AppLockTimeout) -> Unit,
    onLockNow: () -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderSettingsChange: (ReminderSettings) -> Unit,
    onDailyPromptSettingsChange: (DailyPromptSettings) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    canUndoRestore: Boolean,
    onUndoRestore: () -> Unit,
) {
    var showTimeoutDialog by rememberSaveable { mutableStateOf(false) }
    var showReminderDialog by rememberSaveable { mutableStateOf(false) }
    var showPromptStyleDialog by rememberSaveable { mutableStateOf(false) }

    ScreenColumn(padding) {
        ScreenHeader(
            eyebrow = "偏好与数据",
            title = "设置",
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = XikeShapes.inner,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("离线且加密", style = MaterialTheme.typography.titleSmall)
                    Text("无需账号，数据仅保存在这台设备上", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(index = "隐私", title = "应用锁")
            Surface(modifier = Modifier.fillMaxWidth(), shape = XikeShapes.card, color = MaterialTheme.colorScheme.surface) {
                Column {
                    AppLockToggleRow(
                        enabled = appLockEnabled,
                        authenticationAvailable = authenticationAvailable,
                        onEnabledChange = onAppLockChange,
                    )
                    if (appLockEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsAction(
                            icon = Icons.Outlined.Timer,
                            title = "自动锁定",
                            subtitle = appLockTimeout.label,
                            onClick = { showTimeoutDialog = true },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsAction(
                            icon = Icons.Outlined.Lock,
                            title = "立即锁定",
                            subtitle = "隐藏当前内容并返回锁定页",
                            onClick = onLockNow,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(index = "节奏", title = "温和陪伴")
            Surface(modifier = Modifier.fillMaxWidth(), shape = XikeShapes.card, color = MaterialTheme.colorScheme.surface) {
                Column {
                    SettingsToggleRow(
                        icon = Icons.Outlined.NotificationsNone,
                        title = "记录提醒",
                        subtitle = when {
                            reminderSettings.enabled && !notificationPermissionGranted -> "系统通知已关闭；记录功能不受影响"
                            reminderSettings.enabled -> reminderSettings.pauseLabel() ?: reminderSettings.summary()
                            else -> "默认关闭，仅在选定的本地时间提醒"
                        },
                        enabled = reminderSettings.enabled,
                        onEnabledChange = onReminderEnabledChange,
                    )
                    if (reminderSettings.enabled) {
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsAction(
                            icon = Icons.Outlined.Timer,
                            title = "提醒时间",
                            subtitle = reminderSettings.summary(),
                            onClick = { showReminderDialog = true },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        val pauseLabel = reminderSettings.pauseLabel()
                        SettingsAction(
                            icon = Icons.Outlined.PauseCircle,
                            title = if (pauseLabel == null) "暂停一周" else "继续提醒",
                            subtitle = pauseLabel ?: "临时安静下来，不改变原来的时间",
                            onClick = {
                                onReminderSettingsChange(
                                    reminderSettings.copy(
                                        pausedUntilEpochDay = if (pauseLabel == null) {
                                            LocalDate.now().plusDays(7).toEpochDay()
                                        } else {
                                            null
                                        },
                                    ),
                                )
                            },
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsToggleRow(
                        icon = XikeIcons.Mark,
                        title = "每日一问",
                        subtitle = if (dailyPromptSettings.enabled) {
                            dailyPromptSettings.style.label
                        } else {
                            "默认关闭，问题全部来自本地题库"
                        },
                        enabled = dailyPromptSettings.enabled,
                        onEnabledChange = { enabled ->
                            onDailyPromptSettingsChange(dailyPromptSettings.copy(enabled = enabled))
                        },
                    )
                    if (dailyPromptSettings.enabled) {
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsAction(
                            icon = XikeIcons.Mark,
                            title = "本地问题库",
                            subtitle = dailyPromptSettings.style.description,
                            onClick = { showPromptStyleDialog = true },
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(index = "外观", title = "色调")
            AppTheme.entries.chunked(2).forEach { themes ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    themes.forEach { theme ->
                        ThemeTile(
                            theme = theme,
                            selected = selectedTheme == theme,
                            onClick = { onThemeChange(theme) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(index = "数据", title = "备份与迁移")
            Surface(modifier = Modifier.fillMaxWidth(), shape = XikeShapes.card, color = MaterialTheme.colorScheme.surface) {
                Column {
                    SettingsAction(
                        icon = Icons.Outlined.CloudUpload,
                        title = "导出加密备份",
                        subtitle = "保存到本地或系统云盘",
                        onClick = onExport,
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsAction(
                        icon = Icons.Outlined.Restore,
                        title = "从备份恢复",
                        subtitle = "使用密码恢复已有记录",
                        onClick = onImport,
                    )
                    if (canUndoRestore) {
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsAction(
                            icon = Icons.Outlined.Restore,
                            title = "撤销上次恢复",
                            subtitle = "找回恢复前的设备内容，仅可撤销一次",
                            onClick = onUndoRestore,
                        )
                    }
                }
            }
        }

        Text(
            "息刻 · ${BuildConfig.VERSION_NAME}",
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showTimeoutDialog) {
        AppLockTimeoutDialog(
            selected = appLockTimeout,
            onSelected = {
                onAppLockTimeoutChange(it)
                showTimeoutDialog = false
            },
            onDismiss = { showTimeoutDialog = false },
        )
    }

    if (showReminderDialog) {
        ReminderScheduleDialog(
            settings = reminderSettings,
            onConfirm = {
                onReminderSettingsChange(it)
                showReminderDialog = false
            },
            onDismiss = { showReminderDialog = false },
        )
    }

    if (showPromptStyleDialog) {
        DailyPromptStyleDialog(
            selected = dailyPromptSettings.style,
            onSelected = {
                onDailyPromptSettingsChange(dailyPromptSettings.copy(style = it))
                showPromptStyleDialog = false
            },
            onDismiss = { showPromptStyleDialog = false },
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onEnabledChange(!enabled) }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(modifier = Modifier.size(42.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun ReminderScheduleDialog(
    settings: ReminderSettings,
    onConfirm: (ReminderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selectedHour by rememberSaveable(settings.hour) { mutableStateOf(settings.hour) }
    var selectedMinute by rememberSaveable(settings.minute) { mutableStateOf(settings.minute) }
    var selectedDays by remember(settings.weekdays) { mutableStateOf(settings.weekdays) }
    var quietHoursEnabled by rememberSaveable(settings.quietHoursEnabled) {
        mutableStateOf(settings.quietHoursEnabled)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        title = { Text("提醒时间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                selectedHour = hour
                                selectedMinute = minute
                            },
                            selectedHour,
                            selectedMinute,
                            true,
                        ).show()
                    },
                    shape = XikeShapes.inner,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "%02d:%02d".format(Locale.ROOT, selectedHour, selectedMinute),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.weight(1f))
                        Text("修改", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    "为减少耗电，系统可能稍后送达，但不会早于所选时间。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("提醒星期", style = MaterialTheme.typography.titleSmall)
                    DayOfWeek.entries.chunked(4).forEach { days ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            days.forEach { day ->
                                val selected = day in selectedDays
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedDays = if (selected) selectedDays - day else selectedDays + day
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ) {
                                    Text(
                                        day.getDisplayName(DateTextStyle.NARROW, Locale.CHINA),
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            repeat(4 - days.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (selectedDays.isEmpty()) {
                        Text("至少选择一天", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Switch) { quietHoursEnabled = !quietHoursEnabled },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("夜间勿扰", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "%02d:00–%02d:00 内的提醒会延后".format(
                                Locale.ROOT,
                                settings.quietHoursStart,
                                settings.quietHoursEnd,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = quietHoursEnabled, onCheckedChange = null)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        settings.copy(
                            hour = selectedHour,
                            minute = selectedMinute,
                            weekdays = selectedDays,
                            quietHoursEnabled = quietHoursEnabled,
                        ),
                    )
                },
                enabled = selectedDays.isNotEmpty(),
                elevation = xikeButtonElevation(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DailyPromptStyleDialog(
    selected: DailyPromptStyle,
    onSelected: (DailyPromptStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        title = { Text("本地问题库") },
        text = {
            Column(Modifier.selectableGroup()) {
                DailyPromptStyle.entries.forEach { style ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = style == selected,
                                role = Role.RadioButton,
                                onClick = { onSelected(style) },
                            )
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = style == selected, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(style.label, style = MaterialTheme.typography.titleSmall)
                            Text(style.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AppLockToggleRow(
    enabled: Boolean,
    authenticationAvailable: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onEnabledChange(!enabled) }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(modifier = Modifier.size(42.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("应用锁", style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    enabled && authenticationAvailable -> "离开后使用系统身份验证解锁"
                    enabled -> "设备验证不可用，请恢复系统屏幕锁"
                    authenticationAvailable -> "使用面容、指纹或设备密码保护"
                    else -> "开启时需要先设置设备屏幕锁"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun AppLockTimeoutDialog(
    selected: AppLockTimeout,
    onSelected: (AppLockTimeout) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = XikeShapes.dialog,
        title = { Text("自动锁定") },
        text = {
            Column(Modifier.selectableGroup()) {
                AppLockTimeout.entries.forEach { timeout ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == timeout,
                                role = Role.RadioButton,
                                onClick = { onSelected(timeout) },
                            )
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == timeout, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(timeout.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(onClick = onDismiss, elevation = xikeButtonElevation()) { Text("取消") }
        },
    )
}

@Composable
private fun ThemeTile(theme: AppTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = XikeShapes.inner,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(theme.primary))
                    Box(Modifier.padding(start = 4.dp).size(18.dp).clip(CircleShape).background(theme.accent))
                }
                Spacer(Modifier.weight(1f))
                if (selected) Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            Text(theme.title, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun SettingsAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(modifier = Modifier.size(42.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScreenColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
internal fun ScreenHeader(eyebrow: String? = null, title: String, supporting: String? = null) {
    Column {
        if (eyebrow != null) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (supporting != null) {
            Spacer(Modifier.height(2.dp))
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PaperCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = XikeShapes.card, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun SectionTitle(index: String, title: String, trailing: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(index, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun rememberPreviewBitmap(
    key: String,
    maxDimension: Int = 720,
    openStream: () -> InputStream?,
): ImageBitmap? {
    var bitmap by remember(key, maxDimension) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(key, maxDimension) {
        bitmap = withContext(Dispatchers.IO) { decodeScaledPreview(openStream, maxDimension) }
    }
    return bitmap
}

private fun decodeScaledPreview(openStream: () -> InputStream?, maxDimension: Int): ImageBitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        openStream()?.use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

private fun weatherBandLabel(average: Double): String = when {
    average >= 4.5 -> "更多晴朗经过"
    average >= 3.5 -> "更多晴间经过"
    average >= 2.5 -> "大多停在微风附近"
    average >= 1.5 -> "低云停留得更多"
    else -> "风雨停留得更多"
}

private fun weatherSummary(average: Double): String = when {
    average >= 4.5 -> "这段时间里，有不少明亮舒展的时刻。"
    average >= 3.5 -> "云正在散开，也允许自己偶尔停一停。"
    average >= 2.5 -> "微风与起伏都曾经过，慢慢看见就好。"
    average >= 1.5 -> "低云停留得更多，记得给自己留一点余地。"
    else -> "风雨似乎停留了一阵，请更温柔地照顾自己。"
}

private data class MoodVisualStyle(val accent: Color, val container: Color)

private fun Mood.visualStyle(): MoodVisualStyle = when (this) {
    Mood.LOW -> MoodVisualStyle(
        accent = Color(0xFF596779),
        container = Color(0xFFE1E5EA),
    )
    Mood.TIRED -> MoodVisualStyle(
        accent = Color(0xFF627A8D),
        container = Color(0xFFE0E8ED),
    )
    Mood.CALM -> MoodVisualStyle(
        accent = Color(0xFF4F7D75),
        container = Color(0xFFDDEAE5),
    )
    Mood.GOOD -> MoodVisualStyle(
        accent = Color(0xFFAF754D),
        container = Color(0xFFF1E4D7),
    )
    Mood.JOYFUL -> MoodVisualStyle(
        accent = Color(0xFFC18A31),
        container = Color(0xFFF5E7C7),
    )
}

private fun LocalDate.asChineseDay(): String = DateTimeFormatter.ofPattern("EEEE · M月d日", Locale.CHINA).format(this)

private fun LocalDate.asDateRange(end: LocalDate): String = if (year == end.year) {
    "$year · ${monthValue}月${dayOfMonth}日 — ${end.monthValue}月${end.dayOfMonth}日"
} else {
    "${year}年${monthValue}月${dayOfMonth}日 — ${end.year}年${end.monthValue}月${end.dayOfMonth}日"
}

private fun LocalDate.asWeekday(): String = when (dayOfWeek) {
    java.time.DayOfWeek.MONDAY -> "一"
    java.time.DayOfWeek.TUESDAY -> "二"
    java.time.DayOfWeek.WEDNESDAY -> "三"
    java.time.DayOfWeek.THURSDAY -> "四"
    java.time.DayOfWeek.FRIDAY -> "五"
    java.time.DayOfWeek.SATURDAY -> "六"
    java.time.DayOfWeek.SUNDAY -> "日"
}

private fun LocalDate.asFullWeekday(): String = dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.CHINA)

private fun Long.asChineseTime(): String = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
