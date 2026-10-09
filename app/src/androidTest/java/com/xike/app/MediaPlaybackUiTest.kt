package com.xike.app

import android.graphics.Bitmap
import android.media.MediaRecorder
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.content.Context
import android.net.Uri
import android.content.ContentValues
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Before
import org.junit.Test

/** Real MediaRecorder input and an optional video recorded by the system camera, on an isolated device. */
class MediaPlaybackUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun selectLanguage() {
        val language = if (InstrumentationRegistry.getArguments().getString("language") == "en") AppLanguage.ENGLISH else AppLanguage.CHINESE
        rule.runOnUiThread { AppLocale.select(rule.activity, language) }
    }

    @Test
    fun audioSwitchPauseSeekAndBackgroundUseRealEncryptedRecording() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("playbackIsolatedDevice") == "true")
        val context = rule.activity
        val store = JournalStore(context).also { it.initialize() }
        val recording = File.createTempFile("playback-ui-", ".m4a", context.cacheDir)
        @Suppress("DEPRECATION")
        val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        var audio: JournalAudio? = null
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val competingAttributes = playbackAudioAttributes(AudioAttributes.CONTENT_TYPE_SPEECH)
        val competingFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(competingAttributes)
            .setOnAudioFocusChangeListener { }
            .build()
        var competingPlayer: MediaPlayer? = null
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(recording.absolutePath)
            recorder.prepare()
            recorder.start()
            Thread.sleep(12_000)
            recorder.stop()
            val metadata = MediaMetadataRetriever()
            val duration = try {
                metadata.setDataSource(recording.absolutePath)
                requireNotNull(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
            } finally { metadata.release() }
            audio = store.importAudio(recording, duration)
            val actual = audio
            rule.setContent {
                XikeTheme(AppTheme.OCEAN, selectedStyle()) {
                    Surface {
                        PlaybackOutputGuard()
                        Column(Modifier.padding(16.dp)) {
                            VoicePlaybackCard(actual, store::openAudio, Modifier.testTag("first"))
                            VoicePlaybackCard(actual, store::openAudio, Modifier.testTag("second"))
                        }
                    }
                }
            }
            fun button(tag: String, label: String) = rule.onNode(
                hasContentDescription(localizedText(label)) and hasAnyAncestor(hasTestTag(tag)))
            button("first", "播放语音").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription(localizedText("暂停语音")).fetchSemanticsNodes().size == 1 }
            button("second", "播放语音").performClick()
            rule.waitUntil(5_000) {
                rule.onAllNodes(hasContentDescription(localizedText("暂停语音")) and hasAnyAncestor(hasTestTag("second")))
                    .fetchSemanticsNodes().size == 1
            }
            button("first", "播放语音").assertIsDisplayed()
            button("second", "暂停语音").performClick()
            val progress = rule.onNode(hasContentDescription(localizedText("语音播放进度")) and hasAnyAncestor(hasTestTag("second")))
            progress.performTouchInput { swipe(Offset(width * 0.1f, center.y), center) }
            val seeked = progress.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
            screenshot("audio-seek")
            assertTrue("Seek should reach the middle of the recording: $seeked / ${actual.durationMillis}; ${progress.fetchSemanticsNode().boundsInRoot}", seeked in actual.durationMillis * 0.4f..actual.durationMillis * 0.6f)
            rule.onNode(hasText(formatAudioDuration(seeked.toLong())) and hasAnyAncestor(hasTestTag("second"))).assertIsDisplayed()
            button("second", "播放语音").performClick()
            button("second", "暂停语音").assertIsDisplayed()
            screenshot("audio-playing")
            // A separate native focus client plays the real recording. No focus callbacks are injected.
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audioManager.requestAudioFocus(competingFocus))
            competingPlayer = MediaPlayer().apply {
                setAudioAttributes(competingAttributes)
                setDataSource(recording.absolutePath)
                prepare()
                start()
            }
            rule.waitUntil(5_000) {
                rule.onAllNodes(hasContentDescription(localizedText("播放语音")) and hasAnyAncestor(hasTestTag("second")))
                    .fetchSemanticsNodes().size == 1
            }
            assertTrue(requireNotNull(competingPlayer).isPlaying)
            competingPlayer.release()
            competingPlayer = null
            audioManager.abandonAudioFocusRequest(competingFocus)
            rule.mainClock.advanceTimeBy(1_000)
            button("second", "播放语音").assertIsDisplayed()
            button("second", "播放语音").performClick()
            button("second", "暂停语音").assertIsDisplayed()
            rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            button("second", "播放语音").assertIsDisplayed()
            screenshot("audio-paused")
        } finally {
            competingPlayer?.release()
            audioManager.abandonAudioFocusRequest(competingFocus)
            rule.runOnIdle { journalPlayback.pauseActive() }
            recorder.release()
            recording.delete()
            audio?.let { store.deleteUnreferencedAudio(it.fileName) }
        }
    }

    @Test
    fun cameraVideoAutoplaysHidesControlsSeeksAndPausesInBackground() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("playbackIsolatedDevice") == "true" &&
            (arguments.getString("playbackVideoUri") != null || arguments.getString("playbackUseDraftVideo") == "true"))
        val context = rule.activity
        val store = JournalStore(context).also { it.initialize() }
        val imported = arguments.getString("playbackVideoUri") != null
        val video = if (imported) store.importVideo(Uri.parse(arguments.getString("playbackVideoUri")))
            else requireNotNull(JournalDraftStore(context).load().video) { "Record or select a real video through the app first" }
        val videos = JournalVideoStore(context)
        val selectedVideo = mutableStateOf(video)
        var replacement: JournalVideo? = null
        var replacementUri: Uri? = null
        val audio = videos.open(video.fileName).use {
            // Use the actual camera video's audio track; MediaPlayer reads the MPEG-4 container.
            store.importAudio(it, video.durationMillis)
        }
        try {
            rule.setContent {
                XikeTheme(AppTheme.OCEAN, selectedStyle()) {
                    CompositionLocalProvider(LocalVideoServices provides VideoServices(source = videos::dataSource, cover = videos::open)) {
                        PlaybackOutputGuard()
                        Column(Modifier.padding(16.dp)) {
                            VoicePlaybackCard(audio, store::openAudio)
                            VideoCard(selectedVideo.value)
                        }
                    }
                }
            }
            rule.onNodeWithContentDescription(localizedText("播放语音")).performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription(localizedText("暂停语音")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription(tr("播放视频", "Play video")).performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription(tr("暂停视频", "Pause video")).fetchSemanticsNodes().isNotEmpty() }
            screenshot("video-playing")
            rule.mainClock.advanceTimeBy(4_000)
            rule.onNodeWithContentDescription(tr("暂停视频", "Pause video")).assertDoesNotExist()
            rule.onNodeWithContentDescription(tr("视频播放控制", "Video playback controls")).performClick()
            rule.onNodeWithContentDescription(tr("暂停视频", "Pause video")).performClick()
            val videoProgress = rule.onNodeWithContentDescription(tr("视频播放进度", "Video playback progress"))
            videoProgress.performTouchInput { swipe(Offset(width * 0.1f, center.y), center) }
            val seeked = videoProgress.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
            assertTrue("Video seek should reach the middle: $seeked / ${video.durationMillis}; ${videoProgress.fetchSemanticsNode().boundsInRoot}",
                seeked in video.durationMillis * 0.4f..video.durationMillis * 0.6f)
            screenshot("video-paused")
            val playButton = rule.onNode(hasContentDescription(tr("播放视频", "Play video")) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            playButton.performClick()
            rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            playButton.assertIsDisplayed()
            rule.onNodeWithContentDescription(localizedText("关闭")).performClick()
            rule.onNodeWithContentDescription(tr("视频播放控制", "Video playback controls")).assertDoesNotExist()
            rule.onNodeWithContentDescription(localizedText("播放语音")).assertIsDisplayed()

            // Import a second encrypted copy of the actual camera recording, as the editor does.
            val uri = requireNotNull(context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "xike-playback-replacement.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, video.mimeType)
                }))
            replacementUri = uri
            requireNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
                videos.open(video.fileName).use { it.copyTo(output) }
            }
            replacement = store.importVideo(uri)
            rule.onNodeWithContentDescription(tr("播放视频", "Play video")).performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription(tr("暂停视频", "Pause video")).fetchSemanticsNodes().isNotEmpty() }
            rule.runOnIdle { selectedVideo.value = requireNotNull(replacement) }
            rule.onNodeWithContentDescription(tr("视频播放控制", "Video playback controls")).assertDoesNotExist()
            // A replacement must start in a new session, with a live surface and freshly prepared player.
            rule.onNodeWithContentDescription(tr("播放视频", "Play video")).performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription(tr("暂停视频", "Pause video")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription(localizedText("关闭")).performClick()
        } finally {
            rule.runOnIdle { journalPlayback.pauseActive() }
            if (imported) store.deleteUnreferencedVideo(video)
            store.deleteUnreferencedAudio(audio.fileName)
            replacement?.let(store::deleteUnreferencedVideo)
            replacementUri?.let { context.contentResolver.delete(it, null, null) }
        }
    }

    private fun selectedStyle(): AppStyle = AppStyle.entries.firstOrNull {
        it.name == InstrumentationRegistry.getArguments().getString("style")
    } ?: AppStyle.PAPER

    private fun screenshot(name: String) {
        val context = rule.activity
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
