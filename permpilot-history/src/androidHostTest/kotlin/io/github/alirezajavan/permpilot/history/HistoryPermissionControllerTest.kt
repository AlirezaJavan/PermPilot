package io.github.alirezajavan.permpilot.history

import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.alirezajavan.permpilot.FakePermissionController
import io.github.alirezajavan.permpilot.Permission
import io.github.alirezajavan.permpilot.PermissionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives [HistoryPermissionController] against a real (in-memory) [SqlDelightPermissionHistoryStore]
 * -- the same "real behavior, not mocks" approach permpilot-core's own Robolectric tests use -- to
 * verify the audit log actually records what a consumer would see, not just that a method was called.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryPermissionControllerTest {

    // The store's own DB work normally runs on Dispatchers.Default (a real background thread) --
    // tying it to this test's TestScope scheduler instead is what makes advanceUntilIdle() actually
    // wait for it. Without this, `record`/`clear`/`events` race the assertions that follow: it
    // passed when run in isolation (fast, uncontended) but flaked under a full `./gradlew build`
    // (heavier CPU contention widened the window where the real thread hadn't finished yet).
    private fun newStore(testScope: TestScope): SqlDelightPermissionHistoryStore {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // name = null creates an in-memory database -- no state leaks between tests, unlike the real
        // SharedPreferences-backed persistence in AndroidPermissionController's own tests.
        val driver = AndroidSqliteDriver(PermPilotHistoryDatabase.Schema, context, name = null)
        return SqlDelightPermissionHistoryStore(driver, UnconfinedTestDispatcher(testScope.testScheduler))
    }

    @Test
    fun `a granted request records Requested then Resolved with the real state`() = runTest {
        val store = newStore(this)
        val fake = FakePermissionController().apply { setState(Permission.Camera, PermissionState.Granted) }
        val controller = HistoryPermissionController(fake, store, TestScope(testScheduler))

        controller.request(Permission.Camera)
        advanceUntilIdle()

        val entries = store.events(Permission.Camera).first()
        // selectForPermission orders newest-first, so entries[1] is the earlier Requested event.
        assertEquals(2, entries.size)
        assertEquals(PermissionEventType.Resolved, entries[0].type)
        assertEquals("Granted", entries[0].state)
        assertEquals(PermissionEventType.Requested, entries[1].type)
        assertNull(entries[1].state)
    }

    @Test
    fun `requestAll records one Requested and one Resolved event per permission`() = runTest {
        val store = newStore(this)
        val fake = FakePermissionController().apply {
            setState(Permission.Camera, PermissionState.Granted)
            setState(Permission.Microphone, PermissionState.Denied(canRequestAgain = true))
        }
        val controller = HistoryPermissionController(fake, store, TestScope(testScheduler))

        controller.requestAll(Permission.Camera, Permission.Microphone)
        advanceUntilIdle()

        assertEquals(2, store.events(Permission.Camera).first().size)
        assertEquals(2, store.events(Permission.Microphone).first().size)
    }

    @Test
    fun `openAppSettings for a Special permission records a SettingsOpened event without blocking the call`() = runTest {
        val store = newStore(this)
        val fake = FakePermissionController()
        val controller = HistoryPermissionController(fake, store, TestScope(testScheduler))

        // openAppSettings() is not suspend -- the recording happens fire-and-forget on `scope`.
        controller.openAppSettings(Permission.SystemAlertWindow)
        advanceUntilIdle()

        val entries = store.events(Permission.SystemAlertWindow).first()
        assertEquals(1, entries.size)
        assertEquals(PermissionEventType.SettingsOpened, entries[0].type)
        assertTrue(fake.openAppSettingsCalls.contains(Permission.SystemAlertWindow))
    }

    @Test
    fun `plain openAppSettings with no permission records under the generic key, not a crash`() = runTest {
        val store = newStore(this)
        val controller = HistoryPermissionController(FakePermissionController(), store, TestScope(testScheduler))

        controller.openAppSettings()
        advanceUntilIdle()

        val all = store.events().first()
        assertEquals(1, all.size)
        assertEquals(GENERIC_PERMISSION_KEY, all[0].permissionKey)
    }

    @Test
    fun `clear removes every recorded event`() = runTest {
        val store = newStore(this)
        val controller = HistoryPermissionController(
            FakePermissionController().apply { setState(Permission.Camera, PermissionState.Granted) },
            store,
            TestScope(testScheduler),
        )

        controller.request(Permission.Camera)
        advanceUntilIdle()
        assertTrue(store.events().first().isNotEmpty())

        store.clear()
        assertTrue(store.events().first().isEmpty())
    }
}
