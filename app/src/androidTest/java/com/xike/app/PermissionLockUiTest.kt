package com.xike.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xike.app.feature.lock.AppLockSessionState
import com.xike.app.feature.lock.AppLockTimeout
import com.xike.app.outdoor.hasForegroundLocationPermission
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Run only on a dedicated device. Exercises the real launcher, permission controller and lock
 * session with an already-unlocked empty session; never changes app-lock preferences, supplies a
 * fake SDK result, opens journals, or claims to exercise biometric authentication.
 */
class PermissionLockUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val launches = AtomicInteger()
    private val deliveries = AtomicInteger()
    private val results = AtomicInteger()
    private val events = CopyOnWriteArrayList<String>()
    private var granted = false
    private lateinit var session: AppLockSessionState
    private lateinit var host: ComponentActivity

    @Before
    fun requireIsolatedDevice() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("isolatedPermissionLock") == "true"
        )
    }

    @Test
    fun alreadyGrantedSingleAndMultipleRequestsDoNotLock() {
        assertTrue(hasForegroundLocationPermission(context))
        for (single in listOf(true, false)) {
            launches.set(0)
            deliveries.set(0)
            harness(single).use {
                rule.onNodeWithText("Request location").performClick()
                rule.waitUntil(10_000) { deliveries.get() == 1 }
                assertTrue(granted)
                assertEquals(0, launches.get())
                assertFalse(session.isAppLocked)
                assertFalse(session.systemActivityInProgress)
            }
        }
    }

    @Test
    fun coarsePermissionIsEnoughForTheDirectLocationTestPath() {
        assertEquals(
            android.content.pm.PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION),
        )
        assertEquals(
            android.content.pm.PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
        assertTrue(hasForegroundLocationPermission(context))
    }

    @Test
    fun normalGrantDoesNotLockAnImmediateSession() {
        assertFalse(hasForegroundLocationPermission(context))
        harness().use {
            rule.onNodeWithText("Request location").performClick()
            nativePermissionButton("permission_allow_foreground_only_button")
                .performAction(AccessibilityNodeInfo.ACTION_CLICK)
            rule.waitUntil(10_000) { deliveries.get() == 1 }
            assertTrue(granted)
            assertEquals(0, launches.get())
            assertFalse(session.isAppLocked)
            assertNull(session.backgroundedAtMillis)
        }
    }

    @Test
    fun normalDenialDoesNotLockAnImmediateSession() {
        assertFalse(hasForegroundLocationPermission(context))
        harness().use {
            rule.onNodeWithText("Request location").performClick()
            nativePermissionButton("permission_deny_button")
                .performAction(AccessibilityNodeInfo.ACTION_CLICK)
            rule.waitUntil(10_000) { deliveries.get() == 1 }
            assertFalse(granted)
            assertEquals(0, launches.get())
            assertFalse(session.isAppLocked)
        }
    }

    @Test
    fun homeDuringPermissionRequestStillRecordsRealBackgrounding() {
        assertFalse(hasForegroundLocationPermission(context))
        harness().use {
            rule.onNodeWithText("Request location").performClick()
            nativePermissionButton("permission_deny_button")
            assertTrue(
                instrumentation.uiAutomation.performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_HOME
                )
            )
            await { session.backgroundedAtMillis != null }
            assertTrue(
                session.shouldLockOnForeground(
                    SystemClock.elapsedRealtime(),
                    AppLockTimeout.IMMEDIATELY,
                )
            )
            assertEquals(0, launches.get())
        }
    }

    @Test
    fun realStoppedActivityStillLocksOnReturn() {
        harness().use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            assertNotNull(session.backgroundedAtMillis)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue(session.isAppLocked)
        }
    }

    @Test
    fun screenOffDuringPermissionRequestStillLocks() {
        assertFalse(hasForegroundLocationPermission(context))
        val scenario = harness()
        try {
            rule.onNodeWithText("Request location").performClick()
            nativePermissionButton("permission_deny_button")
            shell("input keyevent 223")
            await { session.isAppLocked }
            assertTrue(
                session.shouldLockOnForeground(
                    SystemClock.elapsedRealtime(),
                    AppLockTimeout.THIRTY_MINUTES,
                )
            )
        } finally {
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            // Finish the actual owner after waking. ActivityScenario can lose its current
            // Activity during a permission-controller/screen-off transition; wait for the
            // owner's real destruction before closing its scenario, rather than hiding errors.
            instrumentation.runOnMainSync { host.finish() }
            await {
                var destroyed = false
                instrumentation.runOnMainSync { destroyed = host.isDestroyed }
                destroyed
            }
            instrumentation.waitForIdleSync()
            scenario.close()
        }
    }

    private fun harness(single: Boolean = false): ActivityScenario<ComponentActivity> {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            host = activity
            session = ViewModelProvider(activity)[AppLockSessionState::class.java]
            session.isAppLocked = false
            session.observeDeviceLock(context, activity.taskId, true)
            activity.lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    events.add(event.name)
                    when (event) {
                        Lifecycle.Event.ON_STOP ->
                            if (!activity.isChangingConfigurations)
                                session.recordBackgrounded(SystemClock.elapsedRealtime())
                        Lifecycle.Event.ON_START ->
                            if (
                                session.shouldLockOnForeground(
                                    SystemClock.elapsedRealtime(),
                                    AppLockTimeout.IMMEDIATELY,
                                )
                            )
                                session.isAppLocked = true
                        else -> Unit
                    }
                }
            )
            activity.setContent {
                val callbacks =
                    SystemActivityCallbacks(
                        onLaunch = { key, sameTask ->
                            launches.incrementAndGet()
                            session.beginSystemActivity(
                                watchTaskVisibility = sameTask && Build.VERSION.SDK_INT >= 32,
                                operationId = key,
                                startedAtMillis = SystemClock.elapsedRealtime(),
                            )
                        },
                        onResult = { key ->
                            results.incrementAndGet()
                            if (!deviceUnlocked()) session.recordDeviceLocked()
                            session.finishSystemActivity(
                                operationId = key,
                                nowMillis = SystemClock.elapsedRealtime(),
                                timeout = AppLockTimeout.IMMEDIATELY,
                            )
                        },
                        onLaunchFailed = { key -> session.finishSystemActivity(false, key) },
                        isLocked = session.isAppLocked,
                        canHandleResult = { !session.isAppLocked && deviceUnlocked() },
                    )
                CompositionLocalProvider(LocalSystemActivityCallbacks provides callbacks) {
                    if (single) {
                        val launcher =
                            rememberSystemActivityResultLauncher(
                                ActivityResultContracts.RequestPermission(),
                                "lock-single",
                            ) {
                                granted = it
                                deliveries.incrementAndGet()
                            }
                        TextButton(
                            modifier = Modifier.padding(32.dp),
                            onClick = {
                                launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            },
                        ) {
                            Text("Request location")
                        }
                    } else {
                        val launcher =
                            rememberSystemActivityResultLauncher(
                                ActivityResultContracts.RequestMultiplePermissions(),
                                "lock-multiple",
                            ) {
                                granted =
                                    it[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                                        it[Manifest.permission.ACCESS_FINE_LOCATION] == true
                                deliveries.incrementAndGet()
                            }
                        TextButton(
                            modifier = Modifier.padding(32.dp),
                            onClick = {
                                launcher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                    )
                                )
                            },
                        ) {
                            Text("Request location")
                        }
                    }
                }
            }
        }
        return scenario
    }

    private fun deviceUnlocked(): Boolean =
        context.getSystemService(PowerManager::class.java).isInteractive &&
            !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun nativePermissionButton(id: String): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        await {
            fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (node.viewIdResourceName?.endsWith(":id/$id") == true) return node
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    find(child)?.let {
                        return it
                    }
                }
                return null
            }
            found = instrumentation.uiAutomation.rootInActiveWindow?.let(::find)
            found != null
        }
        return requireNotNull(found)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000L
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) {
                "Permission/lock state did not arrive: launches=${launches.get()}, results=${results.get()}, deliveries=${deliveries.get()}, locked=${if (::session.isInitialized) session.isAppLocked else null}, backgrounded=${if (::session.isInitialized) session.backgroundedAtMillis != null else null}, deviceUnlocked=${deviceUnlocked()}, lifecycle=$events"
            }
            Thread.sleep(50L)
        }
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
}
