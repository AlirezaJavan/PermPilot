package io.github.alirezajavan.permpilot

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowSettings
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Drives [AndroidPermissionController] the way a real host app would: through Robolectric's
 * simulated framework rather than mocked internals, so these tests fail exactly the way a real
 * device regression would -- not just "the code called the method I expected."
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidPermissionControllerRobolectricTest {
    private fun context(): Context = ApplicationProvider.getApplicationContext()

    // --- Special permission live checks (state() with zero setup, i.e. cold start) -----------

    @Test
    fun `SystemAlertWindow reflects the real Settings canDrawOverlays flag`() {
        val controller = AndroidPermissionController(context())

        ShadowSettings.setCanDrawOverlays(false)
        assertEquals(PermissionState.Denied(canRequestAgain = true), controller.state(Permission.SystemAlertWindow).value)

        ShadowSettings.setCanDrawOverlays(true)
        controller.refreshAll()
        assertEquals(PermissionState.Granted, controller.state(Permission.SystemAlertWindow).value)
    }

    @Test
    fun `ExactAlarm on API 31+ reflects AlarmManager canScheduleExactAlarms, not a hardcoded Granted`() {
        val controller = AndroidPermissionController(context())
        val alarmManager = context().getSystemService(AlarmManager::class.java)

        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertEquals(PermissionState.Denied(canRequestAgain = true), controller.state(Permission.ExactAlarm).value)

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        controller.refreshAll()
        assertEquals(PermissionState.Granted, controller.state(Permission.ExactAlarm).value)
        assertTrue(alarmManager!!.canScheduleExactAlarms())
    }

    @Test
    fun `IgnoreBatteryOptimizations reflects PowerManager isIgnoringBatteryOptimizations for this package`() {
        val controller = AndroidPermissionController(context())
        val powerManager = context().getSystemService(PowerManager::class.java)!!

        assertEquals(PermissionState.Denied(canRequestAgain = true), controller.state(Permission.IgnoreBatteryOptimizations).value)

        shadowOf(powerManager).setIgnoringBatteryOptimizations(context().packageName, true)
        controller.refreshAll()
        assertEquals(PermissionState.Granted, controller.state(Permission.IgnoreBatteryOptimizations).value)
    }

    @Test
    fun `DoNotDisturbAccess reflects NotificationManager isNotificationPolicyAccessGranted`() {
        val controller = AndroidPermissionController(context())
        val notificationManager = context().getSystemService(NotificationManager::class.java)!!

        assertEquals(PermissionState.Denied(canRequestAgain = true), controller.state(Permission.DoNotDisturbAccess).value)

        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        controller.refreshAll()
        assertEquals(PermissionState.Granted, controller.state(Permission.DoNotDisturbAccess).value)
    }

    // --- ConfigurationError: the "genuine integration mistake" path, not a user decision -----

    @Test
    fun `request() without a host Activity reports ConfigurationError instead of hanging`() =
        runTest {
            // No updateActivity() call at all -- simulates rememberPermissionController() never having
            // been composed into an Activity-hosted screen, PermPilot's documented failure mode.
            val controller = AndroidPermissionController(context())

            val state = controller.request(Permission.Camera)

            assertIs<PermissionState.ConfigurationError>(state)
            assertEquals(ConfigurationErrorReason.NoHostActivity, state.reason)
            // The error must also be what a subsequent state() query reports -- not just the one-shot
            // suspend return value -- so a PermissionGate observing this controller sees it too.
            assertEquals(state, controller.state(Permission.Camera).value)
        }

    @Test
    fun `requestAll without a host Activity reports ConfigurationError for every applicable permission`() =
        runTest {
            val controller = AndroidPermissionController(context())

            val results: Map<Permission, PermissionState> = controller.requestAll(Permission.Camera, Permission.Microphone)

            val expectedError = PermissionState.ConfigurationError(ConfigurationErrorReason.NoHostActivity)
            val expected: Map<Permission, PermissionState> =
                mapOf(Permission.Camera to expectedError, Permission.Microphone to expectedError)
            assertEquals(expected, results)
        }

    // --- Denied vs PermanentlyDenied end-to-end: hard rule #1, driven through the real class,
    // not just the extracted pure resolver -- this is the exact bug Accompanist shipped.

    @Test
    fun `first-ever denial of a real request resolves Denied, not PermanentlyDenied`() =
        runTest {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val controller = AndroidPermissionController(context())
            controller.updateActivity(activity)

            // Deny Camera before this Activity has ever seen a rationale-eligible request -- Robolectric's
            // default shouldShowRequestPermissionRationale is false here, mirroring "user's very first
            // ever prompt for this permission," which must NOT be read as a permanent denial.
            val requestDeferred = async { controller.request(Permission.Camera) }
            val emitted = controller.multiRequestFlow.first() as PermissionRequest.Runtime
            emitted.onResult(mapOf(Manifest.permission.CAMERA to false))

            assertEquals(PermissionState.Denied(canRequestAgain = true), requestDeferred.await())
        }

    @Test
    fun `a second real denial after the first request resolves PermanentlyDenied`() =
        runTest {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val controller = AndroidPermissionController(context())
            controller.updateActivity(activity)

            val first = async { controller.request(Permission.Camera) }
            (controller.multiRequestFlow.first() as PermissionRequest.Runtime).onResult(mapOf(Manifest.permission.CAMERA to false))
            assertEquals(PermissionState.Denied(canRequestAgain = true), first.await())

            // Same Activity, same permission, requested a second time -- this is the exact scenario
            // Accompanist's bug conflated with "never asked" (both report shouldShowRationale = false
            // on a bare Robolectric Activity); the persisted hasRequested flag is what disambiguates them.
            val second = async { controller.request(Permission.Camera) }
            (controller.multiRequestFlow.first() as PermissionRequest.Runtime).onResult(mapOf(Manifest.permission.CAMERA to false))
            assertEquals(PermissionState.PermanentlyDenied, second.await())
        }

    @Test
    fun `granting on the real request path publishes Granted through both the return value and state()`() =
        runTest {
            // A distinct permission from the two denial tests above -- real Android SharedPreferences
            // (which Robolectric faithfully simulates) statically caches loaded prefs by file path
            // *per process*, not per test method, so the persisted "requested_Camera" flag from those
            // tests would otherwise leak into this one and change which branch gets exercised here.
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val controller = AndroidPermissionController(context())
            controller.updateActivity(activity)

            val requestDeferred = async { controller.request(Permission.Microphone) }
            (controller.multiRequestFlow.first() as PermissionRequest.Runtime).onResult(mapOf(Manifest.permission.RECORD_AUDIO to true))

            assertEquals(PermissionState.Granted, requestDeferred.await())
            assertEquals(PermissionState.Granted, controller.state(Permission.Microphone).value)
        }

    // --- Caller cancellation must never strand the observable state ---------------------------
    //
    // The exact production bug: a Compose caller (PermissionGate's rememberCoroutineScope) left
    // composition while the OS dialog was still up, its request coroutine got cancelled, and the
    // user's grant resumed a dead continuation -- the StateFlow stayed on the stale value forever,
    // so the UI kept showing "Grant" for a permission that was actually granted.

    @Test
    fun `a caller cancelled while the OS dialog is up still gets the grant published through state()`() =
        runTest {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val controller = AndroidPermissionController(context())
            controller.updateActivity(activity)

            // Distinct permission (Contacts) -- Robolectric's static SharedPreferences cache leaks the
            // persisted requested_* flags across test methods in this class (see the Granted test above).
            val requestJob = launch { controller.request(Permission.Contacts) }
            val emitted = controller.multiRequestFlow.first() as PermissionRequest.Runtime
            requestJob.cancel()
            requestJob.join()

            // The user grants in the (still-visible) system dialog after the caller is already gone.
            emitted.onResult(mapOf(Manifest.permission.READ_CONTACTS to true))

            assertEquals(PermissionState.Granted, controller.state(Permission.Contacts).value)
        }

    @Test
    fun `a caller cancelled while the OS dialog is up still gets a denial published through state()`() =
        runTest {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val controller = AndroidPermissionController(context())
            controller.updateActivity(activity)

            val requestJob = launch { controller.request(Permission.SendSms) }
            val emitted = controller.multiRequestFlow.first() as PermissionRequest.Runtime
            requestJob.cancel()
            requestJob.join()

            emitted.onResult(mapOf(Manifest.permission.SEND_SMS to false))

            assertEquals(
                PermissionState.Denied(canRequestAgain = true),
                controller.state(Permission.SendSms).value,
            )
        }

    // --- Check-then-request (Google's documented workflow) ------------------------------------

    @Test
    fun `an already-granted permission resolves Granted without a launcher round-trip or an Activity`() =
        runTest {
            val application = context() as Application
            shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

            // Deliberately no updateActivity(): before the check-then-request fast path, this exact
            // call misreported an already-granted permission as ConfigurationError(NoHostActivity).
            val controller = AndroidPermissionController(context())

            val state = controller.request(Permission.Calendar(CalendarAccess.Full))

            assertEquals(PermissionState.Granted, state)
            assertEquals(PermissionState.Granted, controller.state(Permission.Calendar(CalendarAccess.Full)).value)
        }

    @Test
    fun `requestAll with every permission already granted needs neither a launcher nor an Activity`() =
        runTest {
            val application = context() as Application
            shadowOf(application).grantPermissions(
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
            )
            val controller = AndroidPermissionController(context())

            val results = controller.requestAll(Permission.ReadCallLog, Permission.WriteCallLog)

            val expected: Map<Permission, PermissionState> =
                mapOf(
                    Permission.ReadCallLog to PermissionState.Granted,
                    Permission.WriteCallLog to PermissionState.Granted,
                )
            assertEquals(expected, results)
        }

    @Test
    fun `LocationAlways with foreground and background already granted resolves without any dialog`() =
        runTest {
            val application = context() as Application
            shadowOf(application).grantPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
            // No Activity attached: both stages of the staged flow must short-circuit on their own
            // already-granted checks instead of reporting ConfigurationError or emitting a request.
            val controller = AndroidPermissionController(context())

            assertEquals(PermissionState.Granted, controller.request(Permission.LocationAlways))
            assertEquals(PermissionState.Granted, controller.state(Permission.LocationAlways).value)
        }

    // --- MissingManifestDeclaration detection --------------------------------------------------
    //
    // Requesting an undeclared permission never fails loudly on Android: the OS auto-denies with
    // no dialog, which the resolver would misread as a real user denial. The controller detects
    // it up front -- but only when the package's declared list is actually known (non-null);
    // bare test apps with no declarations at all stay on the normal path.

    private fun declareManifestPermissions(vararg permissions: String) {
        val packageInfo =
            android.content.pm.PackageInfo().apply {
                packageName = context().packageName
                requestedPermissions = arrayOf(*permissions)
            }
        shadowOf(context().packageManager).installPackage(packageInfo)
    }

    @Test
    fun `requesting a permission missing from the manifest reports ConfigurationError instead of a fake denial`() =
        runTest {
            // The app declares *some* permissions -- just not RECORD_AUDIO.
            declareManifestPermissions(Manifest.permission.CAMERA)
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val controller = AndroidPermissionController(context())
            controller.updateActivity(activity)

            val state = controller.request(Permission.Microphone)

            assertIs<PermissionState.ConfigurationError>(state)
            assertEquals(ConfigurationErrorReason.MissingManifestDeclaration, state.reason)
            assertEquals(state, controller.state(Permission.Microphone).value)
        }

    @Test
    fun `requestAll answers undeclared entries locally and still requests the declared rest`() =
        runTest {
            declareManifestPermissions(Manifest.permission.CAMERA)
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val controller = AndroidPermissionController(context())
            controller.updateActivity(activity)

            val resultsDeferred = async { controller.requestAll(Permission.Camera, Permission.Microphone) }
            // Only the declared Camera reaches the native launcher.
            val emitted = controller.multiRequestFlow.first() as PermissionRequest.Runtime
            assertEquals(listOf(Manifest.permission.CAMERA), emitted.permissions.toList())
            emitted.onResult(mapOf(Manifest.permission.CAMERA to true))

            val results = resultsDeferred.await()
            assertEquals(PermissionState.Granted, results[Permission.Camera])
            assertEquals(
                PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration),
                results[Permission.Microphone],
            )
        }

    @Test
    fun `DoNotDisturbAccess without ACCESS_NOTIFICATION_POLICY declared reports ConfigurationError`() {
        declareManifestPermissions(Manifest.permission.CAMERA)
        val controller = AndroidPermissionController(context())

        assertEquals(
            PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration),
            controller.state(Permission.DoNotDisturbAccess).value,
        )
    }

    @Test
    fun `DoNotDisturbAccess with ACCESS_NOTIFICATION_POLICY declared follows the normal grant check`() {
        declareManifestPermissions(Manifest.permission.ACCESS_NOTIFICATION_POLICY)
        val controller = AndroidPermissionController(context())
        val notificationManager = context().getSystemService(NotificationManager::class.java)!!

        assertEquals(PermissionState.Denied(canRequestAgain = true), controller.state(Permission.DoNotDisturbAccess).value)

        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        controller.refreshAll()
        assertEquals(PermissionState.Granted, controller.state(Permission.DoNotDisturbAccess).value)
    }

    @Test
    fun `NotificationListenerAccess without a declared listener service reports ConfigurationError`() {
        val controller = AndroidPermissionController(context())

        assertEquals(
            PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration),
            controller.state(Permission.NotificationListenerAccess).value,
        )
    }

    @Test
    fun `NotificationListenerAccess with a declared listener service follows the normal enabled check`() {
        val component = android.content.ComponentName(context().packageName, "com.example.DemoListenerService")
        val shadowPm = shadowOf(context().packageManager)
        shadowPm.addServiceIfNotPresent(component)
        shadowPm.addIntentFilterForService(
            component,
            android.content.IntentFilter("android.service.notification.NotificationListenerService"),
        )
        val controller = AndroidPermissionController(context())

        // Declared but not yet enabled by the user in Settings -> a normal, actionable denial.
        assertEquals(
            PermissionState.Denied(canRequestAgain = true),
            controller.state(Permission.NotificationListenerAccess).value,
        )
    }

    @Test
    fun `state() surfaces a missing runtime declaration immediately without any request`() {
        declareManifestPermissions(Manifest.permission.CAMERA)
        val controller = AndroidPermissionController(context())

        // No request() call at all -- the very first state() read reports the integration mistake.
        assertEquals(
            PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration),
            controller.state(Permission.ReadPhoneState).value,
        )
    }

    @Test
    fun `declaring only coarse location is the documented approximate-only setup, not a configuration error`() {
        declareManifestPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val controller = AndroidPermissionController(context())

        val state = controller.state(Permission.LocationWhileInUse).value
        assertTrue(state !is PermissionState.ConfigurationError, "coarse-only declaration must not be flagged, was $state")
    }

    @Test
    fun `SystemAlertWindow without its declaration reports ConfigurationError instead of a dead-end Settings redirect`() {
        ShadowSettings.setCanDrawOverlays(false)
        declareManifestPermissions(Manifest.permission.CAMERA)
        val controller = AndroidPermissionController(context())

        assertEquals(
            PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration),
            controller.state(Permission.SystemAlertWindow).value,
        )
    }

    @Test
    fun `ExactAlarm accepts USE_EXACT_ALARM as an alternative declaration`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        declareManifestPermissions("android.permission.USE_EXACT_ALARM")
        val controller = AndroidPermissionController(context())

        assertEquals(
            PermissionState.Denied(canRequestAgain = true),
            controller.state(Permission.ExactAlarm).value,
        )
    }

    @Test
    fun `UsageAccess without PACKAGE_USAGE_STATS declared reports ConfigurationError`() {
        // Robolectric's AppOps defaults to MODE_ALLOWED; force the realistic not-yet-granted mode
        // so the declaration check (which only matters pre-grant) is actually reached.
        val appOps = context().getSystemService(android.app.AppOpsManager::class.java)!!
        shadowOf(appOps).setMode(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context().packageName,
            android.app.AppOpsManager.MODE_IGNORED,
        )
        declareManifestPermissions(Manifest.permission.CAMERA)
        val controller = AndroidPermissionController(context())

        assertEquals(
            PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration),
            controller.state(Permission.UsageAccess).value,
        )
    }

    // --- Partial grants through the real request path (hard rule #10) -------------------------

    @Test
    fun `granting only coarse location resolves Limited ApproximateLocationOnly, not Denied`() =
        runTest {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val controller = AndroidPermissionController(context())
            controller.updateActivity(activity)

            val requestDeferred = async { controller.request(Permission.LocationWhileInUse) }
            (controller.multiRequestFlow.first() as PermissionRequest.Runtime).onResult(
                mapOf(
                    Manifest.permission.ACCESS_FINE_LOCATION to false,
                    Manifest.permission.ACCESS_COARSE_LOCATION to true,
                ),
            )

            val expected = PermissionState.Limited(LimitedReason.ApproximateLocationOnly)
            assertEquals(expected, requestDeferred.await())
            assertEquals(expected, controller.state(Permission.LocationWhileInUse).value)
        }
}
