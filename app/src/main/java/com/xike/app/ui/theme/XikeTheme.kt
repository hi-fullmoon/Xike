package com.xike.app.ui.theme

import com.xike.app.ui.components.XikeNoticeHost
import com.xike.app.ui.components.PreviewBitmapCache
import com.xike.app.ui.components.LocalPreviewBitmapCache

import com.xike.app.R
import com.xike.app.localizedText

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

enum class AppTheme(
    private val sourceTitle: String,
    private val sourceSubtitle: String,
    val primary: Color,
    val accent: Color,
    val secondary: Color,
) {
    PINE("雾松", "森林绿", Color(0xFF496738), Color(0xFFE4ECD9), Color(0xFFF0E8D9)),
    VIOLET("暮紫", "暮色紫", Color(0xFF66567E), Color(0xFFEAE3F0), Color(0xFFF1E4DC)),
    OCEAN("潮汐", "雾蓝绿", Color(0xFF355E5B), Color(0xFFDFECE8), Color(0xFFF0E5D6)),
    TERRA("陶日", "赤陶色", Color(0xFF925B49), Color(0xFFF2E3DC), Color(0xFFE8EBDD)),
    AMBER("麦芒", "琥珀金", Color(0xFF82611E), Color(0xFFF4E8C8), Color(0xFFE5ECE5)),
    ROSE("山茶", "柔雾红", Color(0xFF8B5360), Color(0xFFF3E0E4), Color(0xFFE8E8F0));

    val title: String get() = localizedText(sourceTitle)
    val subtitle: String get() = localizedText(sourceSubtitle)
}

enum class AppStyle(
    private val sourceTitle: String,
    private val sourceSubtitle: String,
) {
    BREATHE("呼吸", "圆润留白，像一段缓慢的停顿"),
    PAPER("纸页", "克制线条，像翻开一册手记"),
    FOCUS("专注", "紧凑清晰，让内容更快抵达");

    val title: String get() = localizedText(sourceTitle)
    val subtitle: String get() = localizedText(sourceSubtitle)
}

internal enum class XikeNavigationTreatment { CAPSULE, UNDERLINE, SOLID }

private data class XikeStyleSpec(
    val style: AppStyle,
    val cardCorner: Dp,
    val innerCorner: Dp,
    val buttonCorner: Dp,
    val dialogCorner: Dp,
    val navigationCorner: Dp,
    val contentMaxWidth: Dp,
    val screenHorizontalPadding: Dp,
    val screenVerticalPadding: Dp,
    val contentGap: Dp,
    val contentCardPadding: Dp,
    val pageTitleStyle: TextStyle,
    val sectionTitleStyle: TextStyle,
    val eyebrowStyle: TextStyle,
    val navigationTreatment: XikeNavigationTreatment,
)

private val BreatheStyleSpec = XikeStyleSpec(
    style = AppStyle.BREATHE,
    cardCorner = 28.dp,
    innerCorner = 18.dp,
    buttonCorner = 18.dp,
    dialogCorner = 30.dp,
    navigationCorner = 18.dp,
    contentMaxWidth = 640.dp,
    screenHorizontalPadding = 20.dp,
    screenVerticalPadding = 18.dp,
    contentGap = 15.dp,
    contentCardPadding = 18.dp,
    pageTitleStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 35.sp,
        lineHeight = 44.sp,
    ),
    sectionTitleStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 23.sp,
        lineHeight = 32.sp,
    ),
    eyebrowStyle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 17.sp,
        letterSpacing = 1.7.sp,
    ),
    navigationTreatment = XikeNavigationTreatment.CAPSULE,
)

private val PaperStyleSpec = XikeStyleSpec(
    style = AppStyle.PAPER,
    cardCorner = 6.dp,
    innerCorner = 3.dp,
    buttonCorner = 6.dp,
    dialogCorner = 12.dp,
    navigationCorner = 0.dp,
    contentMaxWidth = 680.dp,
    screenHorizontalPadding = 24.dp,
    screenVerticalPadding = 22.dp,
    contentGap = 19.dp,
    contentCardPadding = 14.dp,
    pageTitleStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 38.sp,
        lineHeight = 48.sp,
        letterSpacing = 0.2.sp,
    ),
    sectionTitleStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 34.sp,
    ),
    eyebrowStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.2.sp,
    ),
    navigationTreatment = XikeNavigationTreatment.UNDERLINE,
)

private val FocusStyleSpec = XikeStyleSpec(
    style = AppStyle.FOCUS,
    cardCorner = 14.dp,
    innerCorner = 10.dp,
    buttonCorner = 10.dp,
    dialogCorner = 18.dp,
    navigationCorner = 10.dp,
    contentMaxWidth = 720.dp,
    screenHorizontalPadding = 16.dp,
    screenVerticalPadding = 12.dp,
    contentGap = 10.dp,
    contentCardPadding = 14.dp,
    pageTitleStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 31.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
    ),
    sectionTitleStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    eyebrowStyle = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        letterSpacing = 1.1.sp,
    ),
    navigationTreatment = XikeNavigationTreatment.SOLID,
)

private fun AppStyle.spec(): XikeStyleSpec = when (this) {
    AppStyle.BREATHE -> BreatheStyleSpec
    AppStyle.PAPER -> PaperStyleSpec
    AppStyle.FOCUS -> FocusStyleSpec
}

private val LocalXikeStyle = staticCompositionLocalOf { BreatheStyleSpec }
private class XikePickerContext(context: Context, val cornerRadius: Float, val contentMaxWidth: Int) :
    ContextThemeWrapper(context, R.style.Theme_Xike_Picker)

internal fun android.app.Dialog.showWithXikeShape() {
    show()
    var baseContext = context
    while (baseContext is android.content.ContextWrapper && baseContext !is XikePickerContext) {
        baseContext = baseContext.baseContext
    }
    val pickerContext = baseContext as? XikePickerContext ?: return
    val background = TypedValue()
    context.theme.resolveAttribute(android.R.attr.colorBackground, background, true)
    window?.apply {
        val metrics = context.resources.displayMetrics
        val configuration = context.resources.configuration
        val insets = android.graphics.Rect()
        decorView.background?.getPadding(insets)
        val roundedBackground = GradientDrawable().apply {
            setColor(background.data)
            cornerRadius = pickerContext.cornerRadius
        }
        setBackgroundDrawable(android.graphics.drawable.InsetDrawable(
            roundedBackground,
            insets.left,
            insets.top,
            insets.right,
            insets.bottom,
        ))
        decorView.clipToOutline = true
        val availableWidth = minOf((configuration.screenWidthDp * metrics.density).roundToInt(), pickerContext.contentMaxWidth)
        val availableHeight = (configuration.screenHeightDp * metrics.density).roundToInt()
        // Resolve the width before the calendar lays out its columns for the first time.
        decorView.measure(
            View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(availableHeight, View.MeasureSpec.AT_MOST),
        )
        setLayout(minOf(decorView.measuredWidth, availableWidth), attributes.height)
    }
}

internal val LocalRecordedAtPickerContext = staticCompositionLocalOf<Context> { error("Missing picker theme") }

internal val XikeCardPadding = 14.dp
internal val XikeContentCardPadding: Dp
    @Composable get() = LocalXikeStyle.current.contentCardPadding
internal val XikeInnerCardPadding = 10.dp
internal val XikeSheetHorizontalPadding = 20.dp
internal val XikeSheetBottomPadding = 20.dp
object XikeShapes {
    val card: RoundedCornerShape
        @Composable get() = RoundedCornerShape(LocalXikeStyle.current.cardCorner)
    val inner: RoundedCornerShape
        @Composable get() = RoundedCornerShape(LocalXikeStyle.current.innerCorner)
    val button: RoundedCornerShape
        @Composable get() = RoundedCornerShape(LocalXikeStyle.current.buttonCorner)
    val dialog: RoundedCornerShape
        @Composable get() = RoundedCornerShape(LocalXikeStyle.current.dialogCorner)
    val sheet: RoundedCornerShape
        @Composable get() = RoundedCornerShape(
            topStart = LocalXikeStyle.current.cardCorner,
            topEnd = LocalXikeStyle.current.cardCorner,
        )
}

internal val XikeContentMaxWidth: Dp
    @Composable get() = LocalXikeStyle.current.contentMaxWidth

internal val XikeScreenHorizontalPadding: Dp
    @Composable get() = LocalXikeStyle.current.screenHorizontalPadding

internal val XikeScreenVerticalPadding: Dp
    @Composable get() = LocalXikeStyle.current.screenVerticalPadding

internal val XikeContentGap: Dp
    @Composable get() = LocalXikeStyle.current.contentGap

internal val XikePageTitleStyle: TextStyle
    @Composable get() = LocalXikeStyle.current.pageTitleStyle

internal val XikeSectionTitleStyle: TextStyle
    @Composable get() = LocalXikeStyle.current.sectionTitleStyle

internal val XikeEyebrowStyle: TextStyle
    @Composable get() = LocalXikeStyle.current.eyebrowStyle

private val BreatheTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 42.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
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

private val PaperTypography = BreatheTypography.copy(
    displaySmall = BreatheTypography.displaySmall.copy(fontFamily = FontFamily.Serif),
    headlineLarge = BreatheTypography.headlineLarge.copy(fontFamily = FontFamily.Serif),
    headlineMedium = BreatheTypography.headlineMedium.copy(fontFamily = FontFamily.Serif),
    headlineSmall = BreatheTypography.headlineSmall.copy(fontFamily = FontFamily.Serif),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 29.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 15.sp, lineHeight = 25.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 13.sp, lineHeight = 21.sp),
)

private val FocusTypography = BreatheTypography.copy(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 46.sp, letterSpacing = (-0.7).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 35.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
)

private fun typographyFor(style: AppStyle): Typography = when (style) {
    AppStyle.BREATHE -> BreatheTypography
    AppStyle.PAPER -> PaperTypography
    AppStyle.FOCUS -> FocusTypography
}

@Composable
fun XikeTheme(
    theme: AppTheme,
    style: AppStyle = AppStyle.BREATHE,
    content: @Composable () -> Unit,
) {
    val styleSpec = style.spec()
    val context = LocalContext.current
    val selectedTheme = theme
    val pickerContext = remember(context, theme, style) {
        XikePickerContext(
            context,
            styleSpec.dialogCorner.value * context.resources.displayMetrics.density,
            (styleSpec.contentMaxWidth.value * context.resources.displayMetrics.density).roundToInt(),
        ).apply {
            this.theme.applyStyle(when (selectedTheme) {
                AppTheme.PINE -> R.style.PickerAccent_Pine
                AppTheme.VIOLET -> R.style.PickerAccent_Violet
                AppTheme.OCEAN -> R.style.PickerAccent_Ocean
                AppTheme.TERRA -> R.style.PickerAccent_Terra
                AppTheme.AMBER -> R.style.PickerAccent_Amber
                AppTheme.ROSE -> R.style.PickerAccent_Rose
            }, true)
            this.theme.applyStyle(when (style) {
                AppStyle.BREATHE -> R.style.PickerSurface_Breathe
                AppStyle.PAPER -> R.style.PickerSurface_Paper
                AppStyle.FOCUS -> R.style.PickerSurface_Focus
            }, true)
        }
    }
    val previewBitmapCache = remember { PreviewBitmapCache() }
    val colors: ColorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = theme.accent,
            onPrimary = Color(0xFF20201E),
            primaryContainer = theme.primary.copy(alpha = 0.58f),
            onPrimaryContainer = Color(0xFFF4F3EF),
            secondary = theme.accent,
            onSecondary = Color(0xFF20201E),
            secondaryContainer = theme.secondary.copy(alpha = 0.18f),
            onSecondaryContainer = Color(0xFFF2F0EB),
            tertiary = Color(0xFFD3BCA4),
            onTertiary = Color(0xFF3D372E),
            tertiaryContainer = theme.secondary.copy(alpha = 0.24f),
            onTertiaryContainer = Color(0xFFF2EEE6),
            background = when (style) {
                AppStyle.BREATHE -> Color(0xFF121614)
                AppStyle.PAPER -> Color(0xFF191714)
                AppStyle.FOCUS -> Color(0xFF101312)
            },
            onBackground = Color(0xFFE8E9E4),
            surface = when (style) {
                AppStyle.BREATHE -> Color(0xFF1B211E)
                AppStyle.PAPER -> Color(0xFF242019)
                AppStyle.FOCUS -> Color(0xFF1A1E1C)
            },
            onSurface = Color(0xFFE8E9E4),
            surfaceVariant = when (style) {
                AppStyle.BREATHE -> Color(0xFF282D29)
                AppStyle.PAPER -> Color(0xFF302A22)
                AppStyle.FOCUS -> Color(0xFF252A27)
            },
            onSurfaceVariant = when (style) {
                AppStyle.BREATHE -> Color(0xFFB8BDB7)
                AppStyle.PAPER -> Color(0xFFC5BCAF)
                AppStyle.FOCUS -> Color(0xFFB7BDB9)
            },
            surfaceTint = theme.accent,
            surfaceDim = when (style) {
                AppStyle.BREATHE -> Color(0xFF121614)
                AppStyle.PAPER -> Color(0xFF191714)
                AppStyle.FOCUS -> Color(0xFF101312)
            },
            surfaceBright = when (style) {
                AppStyle.BREATHE -> Color(0xFF39423C)
                AppStyle.PAPER -> Color(0xFF413A31)
                AppStyle.FOCUS -> Color(0xFF363C38)
            },
            surfaceContainerLowest = when (style) {
                AppStyle.BREATHE -> Color(0xFF101512)
                AppStyle.PAPER -> Color(0xFF14120F)
                AppStyle.FOCUS -> Color(0xFF0D100F)
            },
            surfaceContainerLow = when (style) {
                AppStyle.BREATHE -> Color(0xFF1B211E)
                AppStyle.PAPER -> Color(0xFF211D18)
                AppStyle.FOCUS -> Color(0xFF171B19)
            },
            surfaceContainer = when (style) {
                AppStyle.BREATHE -> Color(0xFF202723)
                AppStyle.PAPER -> Color(0xFF28231C)
                AppStyle.FOCUS -> Color(0xFF1D221F)
            },
            surfaceContainerHigh = when (style) {
                AppStyle.BREATHE -> Color(0xFF272E29)
                AppStyle.PAPER -> Color(0xFF302A22)
                AppStyle.FOCUS -> Color(0xFF252B27)
            },
            surfaceContainerHighest = when (style) {
                AppStyle.BREATHE -> Color(0xFF313933)
                AppStyle.PAPER -> Color(0xFF3B342A)
                AppStyle.FOCUS -> Color(0xFF303631)
            },
            inverseSurface = Color(0xFFE8ECE8),
            inverseOnSurface = Color(0xFF28312B),
            inversePrimary = theme.primary,
            outline = Color(0xFF68706A),
            outlineVariant = Color(0xFF363C37),
        )
    } else {
        lightColorScheme(
            primary = theme.primary,
            onPrimary = Color.White,
            primaryContainer = theme.accent,
            onPrimaryContainer = Color(0xFF24231F),
            secondary = theme.primary,
            onSecondary = Color.White,
            secondaryContainer = theme.secondary,
            onSecondaryContainer = Color(0xFF302E2A),
            tertiary = Color(0xFF7A6755),
            onTertiary = Color.White,
            tertiaryContainer = theme.secondary,
            onTertiaryContainer = Color(0xFF3D372E),
            background = when (style) {
                AppStyle.BREATHE -> Color(0xFFF8F7F2)
                AppStyle.PAPER -> Color(0xFFF3EEE4)
                AppStyle.FOCUS -> Color(0xFFF3F6F4)
            },
            onBackground = Color(0xFF20231F),
            surface = when (style) {
                AppStyle.BREATHE -> Color.White
                AppStyle.PAPER -> Color(0xFFFFFCF5)
                AppStyle.FOCUS -> Color(0xFFFCFDFC)
            },
            onSurface = Color(0xFF20231F),
            surfaceVariant = when (style) {
                AppStyle.BREATHE -> Color(0xFFF0F4F0)
                AppStyle.PAPER -> Color(0xFFECE4D7)
                AppStyle.FOCUS -> Color(0xFFE7ECE9)
            },
            onSurfaceVariant = when (style) {
                AppStyle.BREATHE -> Color(0xFF63706A)
                AppStyle.PAPER -> Color(0xFF6B6359)
                AppStyle.FOCUS -> Color(0xFF5F6A64)
            },
            surfaceTint = theme.primary,
            surfaceDim = when (style) {
                AppStyle.BREATHE -> Color(0xFFE8EBE8)
                AppStyle.PAPER -> Color(0xFFE3DDD1)
                AppStyle.FOCUS -> Color(0xFFE5EAE7)
            },
            surfaceBright = when (style) {
                AppStyle.BREATHE -> Color.White
                AppStyle.PAPER -> Color(0xFFFFFDF8)
                AppStyle.FOCUS -> Color(0xFFFFFFFF)
            },
            surfaceContainerLowest = when (style) {
                AppStyle.BREATHE -> Color.White
                AppStyle.PAPER -> Color(0xFFFFFEFA)
                AppStyle.FOCUS -> Color(0xFFFFFFFF)
            },
            surfaceContainerLow = when (style) {
                AppStyle.BREATHE -> Color(0xFFF1F4EF)
                AppStyle.PAPER -> Color(0xFFF8F2E8)
                AppStyle.FOCUS -> Color(0xFFF0F4F1)
            },
            surfaceContainer = when (style) {
                AppStyle.BREATHE -> Color(0xFFF2F5F0)
                AppStyle.PAPER -> Color(0xFFF3ECE0)
                AppStyle.FOCUS -> Color(0xFFEBF0ED)
            },
            surfaceContainerHigh = when (style) {
                AppStyle.BREATHE -> Color(0xFFECF1EB)
                AppStyle.PAPER -> Color(0xFFEBE2D4)
                AppStyle.FOCUS -> Color(0xFFE3E9E5)
            },
            surfaceContainerHighest = when (style) {
                AppStyle.BREATHE -> Color(0xFFE4EBE4)
                AppStyle.PAPER -> Color(0xFFE1D7C8)
                AppStyle.FOCUS -> Color(0xFFDCE3DE)
            },
            inverseSurface = Color(0xFF2E3530),
            inverseOnSurface = Color(0xFFF2F5F2),
            inversePrimary = theme.accent,
            outline = Color(0xFF7C817B),
            outlineVariant = when (style) {
                AppStyle.BREATHE -> Color(0xFFDADDD6)
                AppStyle.PAPER -> Color(0xFFD2C8B9)
                AppStyle.FOCUS -> Color(0xFFD4DAD6)
            },
        )
    }
    CompositionLocalProvider(
        LocalXikeStyle provides styleSpec,
        LocalRecordedAtPickerContext provides pickerContext,
        LocalPreviewBitmapCache provides previewBitmapCache,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = typographyFor(style),
        ) {
            content()
            XikeNoticeHost()
        }
    }
}

@Composable
internal fun xikeButtonElevation(): ButtonElevation = ButtonDefaults.buttonElevation(
    defaultElevation = 0.dp,
    pressedElevation = 0.dp,
    focusedElevation = 0.dp,
    hoveredElevation = 0.dp,
    disabledElevation = 0.dp,
)

internal val XikeInlineActionGap = 6.dp

@Composable
internal fun xikeSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
)


internal val XikeCurrentStyle: AppStyle
    @Composable get() = LocalXikeStyle.current.style

internal val XikeNavigationTreatmentValue: XikeNavigationTreatment
    @Composable get() = LocalXikeStyle.current.navigationTreatment

internal val XikeNavigationCorner: Dp
    @Composable get() = LocalXikeStyle.current.navigationCorner

internal fun AppStyle.cardCorner(): Dp = spec().cardCorner
