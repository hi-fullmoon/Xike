package com.xike.app.feature.ai

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.xike.app.ui.components.PaperCard
import com.xike.app.ui.theme.XikeContentGap
import com.xike.app.ui.theme.XikeContentMaxWidth
import com.xike.app.ui.theme.XikeDialogCompactGap
import com.xike.app.ui.theme.XikeScreenHorizontalPadding
import com.xike.app.ui.theme.XikeScreenVerticalPadding
import com.xike.app.ui.theme.XikeShapes
import kotlin.math.roundToInt

/**
 * [dismissOnOutsideTap] should be false while a request is running, a result is shown, or input is
 * unsaved; the close button and system Back still dismiss.
 */
@Composable
internal fun AiPanel(
    onDismiss: () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    dismissOnOutsideTap: Boolean = true,
    header: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dismiss by rememberUpdatedState(onDismiss)
    val outsideTapDismisses by rememberUpdatedState(dismissOnOutsideTap)
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                securePolicy = SecureFlagPolicy.SecureOn,
            ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    detectTapGestures { if (outsideTapDismisses) dismiss() }
                }
            )
            Box(
                Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier =
                        modifier
                            .widthIn(max = XikeContentMaxWidth)
                            .fillMaxWidth()
                            .heightIn(max = 720.dp)
                            .fillMaxHeight(),
                    shape = XikeShapes.dialog,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        Box(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = XikeScreenHorizontalPadding, vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            header()
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(
                            Modifier.weight(1f)
                                .clipToBounds()
                                .verticalScroll(scrollState)
                                .then(contentModifier)
                                .fillMaxWidth()
                                .padding(
                                    horizontal = XikeScreenHorizontalPadding,
                                    vertical = XikeScreenVerticalPadding,
                                ),
                            verticalArrangement = Arrangement.spacedBy(XikeContentGap),
                            content = content,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = XikeScreenHorizontalPadding, vertical = 8.dp)
                        ) {
                            actions()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AiSection(
    title: String,
    titleModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    PaperCard {
        Column(verticalArrangement = Arrangement.spacedBy(XikeDialogCompactGap)) {
            Text(
                title,
                modifier = titleModifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            content()
        }
    }
}

@Composable
internal fun AiCheckboxRow(
    label: String,
    checked: Boolean,
    tag: String,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    var firstLineCenterPx by remember { mutableIntStateOf(0) }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            Modifier.testTag(tag)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Checkbox,
                    interactionSource = interactionSource,
                    indication = null,
                    onValueChange = onCheckedChange,
                )
                .wrapContentHeight(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
            modifier =
                Modifier.testTag("$tag-checkbox")
                    .size(20.dp)
                    .indication(interactionSource, ripple(bounded = false, radius = 20.dp))
                    .alignBy { it.measuredHeight / 2 },
        )
        Text(
            label,
            modifier = Modifier.testTag("$tag-text").alignBy { firstLineCenterPx },
            style = MaterialTheme.typography.bodyMedium,
            onTextLayout = { layout ->
                firstLineCenterPx =
                    ((layout.getLineTop(0) + layout.getLineBottom(0)) / 2).roundToInt()
            },
        )
    }
}
