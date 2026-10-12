package com.xike.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A single launch animation; no journal data or authentication is opened here. */
@Composable
internal fun WelcomeScreen(onFinished: () -> Unit) {
    val opacity = remember { Animatable(0f) }
    val textOpacity = remember { Animatable(0f) }
    val breath = remember { Animatable(1f) }
    val finished = rememberUpdatedState(onFinished)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            coroutineScope {
                launch { opacity.animateTo(1f, tween(180)) }
                launch { textOpacity.animateTo(1f, tween(180)) }
                breath.animateTo(1.025f, tween(420, easing = FastOutSlowInEasing))
                breath.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
            }
            opacity.animateTo(0f, tween(200))
            finished.value()
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize().testTag("welcome-screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val viewportHeight = with(LocalDensity.current) { maxHeight.roundToPx() }
            SubcomposeLayout(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).alpha(opacity.value),
            ) { constraints ->
                val inset = 24.dp.roundToPx()
                val edge = 12.dp.roundToPx()
                val gap = 16.dp.roundToPx()
                val contentConstraints = Constraints(maxWidth = (constraints.maxWidth - 2 * inset).coerceAtLeast(0))
                val footer = subcompose("footer") {
                    Text(
                        stringResource(R.string.welcome_footer),
                        modifier = Modifier.alpha(textOpacity.value),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }.single().measure(contentConstraints)
                val stackedBrand = subcompose("stacked-brand") {
                    // This unplaced composition only measures the preferred layout; accessibility
                    // must expose only the final, placed brand below.
                    Box(Modifier.clearAndSetSemantics {}) {
                        WelcomeBrand(false, textOpacity.value, breath.value)
                    }
                }.single().measure(contentConstraints)
                val compact = stackedBrand.height + footer.height + gap + 2 * edge > viewportHeight
                val brand = subcompose("brand") {
                    WelcomeBrand(compact, textOpacity.value, breath.value)
                }.single().measure(contentConstraints)
                val bottomInset = if (compact) edge else 40.dp.roundToPx()
                // Extremely short windows can scroll rather than clip or overlap text.
                val height = maxOf(viewportHeight, edge + brand.height + gap + footer.height + bottomInset)
                val footerTop = height - bottomInset - footer.height
                val latestBrandTop = footerTop - gap - brand.height
                val preferredBrandTop = if (compact) {
                    (edge + latestBrandTop) / 2
                } else {
                    (height * 0.4f - 105.dp.toPx()).roundToInt()
                }
                val brandTop = preferredBrandTop.coerceIn(edge, latestBrandTop)
                layout(constraints.maxWidth, height) {
                    brand.placeRelative((constraints.maxWidth - brand.width) / 2, brandTop)
                    footer.placeRelative((constraints.maxWidth - footer.width) / 2, footerTop)
                }
            }
        }
    }
}

@Composable
private fun WelcomeBrand(compact: Boolean, textOpacity: Float, breath: Float) {
    Box(contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier.size(190.dp).background(
                Brush.radialGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f), Color.Transparent),
                ),
            ),
        )
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WelcomeLogo(breath)
                Spacer(Modifier.width(24.dp))
                WelcomeBrandText(textOpacity)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WelcomeLogo(breath)
                Spacer(Modifier.height(25.dp))
                WelcomeBrandText(textOpacity)
            }
        }
    }
}

@Composable
private fun WelcomeLogo(breath: Float) {
    Box(
        Modifier.size(82.dp).graphicsLayer {
            scaleX = breath
            scaleY = breath
        }.background(colorResource(R.color.launcher_background), RoundedCornerShape(25.dp)),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun WelcomeBrandText(opacity: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(opacity)) {
        Text(
            stringResource(R.string.app_name),
            style = XikePageTitleStyle.copy(
                fontSize = 30.sp, lineHeight = 38.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium, letterSpacing = 4.sp,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.welcome_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
