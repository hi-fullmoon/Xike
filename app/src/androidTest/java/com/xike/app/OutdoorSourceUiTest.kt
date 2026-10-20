package com.xike.app

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.data.model.LocationSource
import com.xike.app.outdoor.OutdoorContextRepository
import com.xike.app.ui.components.journal.OutdoorContextCard
import com.xike.app.ui.theme.XikeContentCardPadding
import com.xike.app.ui.theme.XikeTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Production card with real public-city weather; no fabricated SDK result or journal state. */
class OutdoorSourceUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test
    fun manuallySelectedCityShowsItsOwnSourceAndTheUnchangedWeatherProvider() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("isolatedOutdoorSource") == "true"
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appearance = AppearancePreferences(context).current()
        try {
            for (language in listOf(AppLanguage.CHINESE, AppLanguage.ENGLISH)) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    AppLocale.select(context, language)
                }
                // Use the service's canonical city query in both languages; this test checks
                // the real card and provider metadata, not cross-language city aliases.
                val weather = runBlocking { OutdoorContextRepository(context).city("Hangzhou") }
                assertEquals(LocationSource.MANUAL, weather.locationSource)
                assertEquals("Open-Meteo", weather.source)
                ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        activity.setContent {
                            XikeTheme(appearance.theme, appearance.style) {
                                Column(
                                    Modifier.fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp)
                                ) {
                                    Box(Modifier.testTag("outdoor-source-preview")) {
                                        OutdoorContextCard(
                                            weather,
                                            false,
                                            false,
                                            null,
                                            true,
                                            {},
                                            {},
                                            {},
                                            contentPadding = XikeContentCardPadding,
                                            minContentHeight = 42.dp,
                                            showLocationIconBackground = true,
                                            placeTextStyle = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    val source = tr("地点来源：手动选择", "Location source: Manually selected")
                    rule.onNodeWithText(source).performScrollTo().assertIsDisplayed()
                    val prefix = tr("天气来源：", "Weather source:")
                    val weatherLine =
                        rule.onNode(
                            hasText(prefix, substring = true) and
                                hasText("Open-Meteo", substring = true)
                        )
                    weatherLine.performScrollTo().assertIsDisplayed()
                    val layouts = mutableListOf<TextLayoutResult>()
                    weatherLine.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                        it(layouts)
                    }
                    assertTrue(layouts.isNotEmpty() && layouts.all { !it.hasVisualOverflow })
                    val sourceLayouts = mutableListOf<TextLayoutResult>()
                    val sourceLine = rule.onNodeWithText(source)
                    sourceLine.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                        it(sourceLayouts)
                    }
                    assertEquals(
                        sourceLayouts.single().layoutInput.style,
                        layouts.single().layoutInput.style,
                    )
                    val gap =
                        weatherLine.fetchSemanticsNode().boundsInRoot.top -
                            sourceLine.fetchSemanticsNode().boundsInRoot.bottom
                    val density = layouts.single().layoutInput.density.density
                    assertEquals(2f * density, gap, 1f)
                    if (
                        InstrumentationRegistry.getArguments()
                            .getString("outdoorSourceScreenshots") == "true"
                    ) {
                        val folder = requireNotNull(context.getExternalFilesDir("verification"))
                        File(folder, "outdoor-source-${language.tag}.png").outputStream().use {
                            output ->
                            assertTrue(
                                rule
                                    .onNodeWithTag("outdoor-source-preview")
                                    .captureToImage()
                                    .asAndroidBitmap()
                                    .compress(Bitmap.CompressFormat.PNG, 100, output)
                            )
                        }
                    }
                }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                AppLocale.initialize(context)
            }
        }
    }
}
