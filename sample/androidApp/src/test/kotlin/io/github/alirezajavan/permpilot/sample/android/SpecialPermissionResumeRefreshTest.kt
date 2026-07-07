package io.github.alirezajavan.permpilot.sample.android

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.alirezajavan.permpilot.Permission
import io.github.alirezajavan.permpilot.PermissionController
import io.github.alirezajavan.permpilot.PermissionState
import io.github.alirezajavan.permpilot.rememberPermissionController
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import kotlin.test.assertEquals

/**
 * End-to-end regression test for the exact reported bug: a Special permission (Settings-redirect
 * only, no request callback) granted while the user is away must be picked up when they return.
 * The ON_RESUME -> refreshAll() wiring inside rememberPermissionController() is the *only* update
 * path for these permissions, so this drives it through a real Activity lifecycle + composition
 * instead of trusting the CompositionLocal plumbing (which a dependency bump can silently break).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpecialPermissionResumeRefreshTest {
    @Test
    fun `SystemAlertWindow granted in Settings while away is Granted after returning to the app`() {
        ShadowSettings.setCanDrawOverlays(false)
        val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()

        lateinit var controller: PermissionController
        activityController.get().setContent {
            controller = rememberPermissionController()
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            PermissionState.Denied(canRequestAgain = true),
            controller.state(Permission.SystemAlertWindow).value,
        )

        // User leaves for the "Display over other apps" Settings screen, grants there, comes back.
        ShadowSettings.setCanDrawOverlays(true)
        activityController.pause().resume()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(PermissionState.Granted, controller.state(Permission.SystemAlertWindow).value)
    }
}
