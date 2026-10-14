package com.xike.app.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xike.app.localizedText
import com.xike.app.tr
import com.xike.app.ui.theme.XikeEyebrowStyle
import com.xike.app.ui.theme.XikePageTitleStyle
import com.xike.app.ui.theme.XikeShapes
import com.xike.app.ui.theme.xikeButtonElevation

@Composable
fun AppLockDialog(
    authenticationAvailable: Boolean,
    onUnlock: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties =
            DialogProperties(
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
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 32.dp, vertical = 28.dp)
        ) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        Text(
                            tr("只属于你的空间", "A SPACE OF YOUR OWN"),
                            style = XikeEyebrowStyle,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(9.dp))
                        Text(
                            localizedText("息刻已锁定"),
                            style = XikePageTitleStyle.copy(fontSize = 30.sp, lineHeight = 38.sp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (authenticationAvailable) {
                                localizedText("验证身份后，回到只属于你的留白。")
                            } else {
                                localizedText("需要先在系统中设置屏幕锁，才能继续验证身份。")
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick =
                                if (authenticationAvailable) onUnlock else onOpenSecuritySettings,
                            shape = XikeShapes.button,
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
                            elevation = xikeButtonElevation(),
                        ) {
                            Text(
                                if (authenticationAvailable) localizedText("解锁息刻")
                                else localizedText("设置设备锁屏")
                            )
                        }
                    }
                }
            }

            Text(
                localizedText("身份信息仅由 Android 系统验证，息刻不会读取或保存。"),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
