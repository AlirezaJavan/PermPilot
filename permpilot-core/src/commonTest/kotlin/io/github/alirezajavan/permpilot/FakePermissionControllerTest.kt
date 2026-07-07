package io.github.alirezajavan.permpilot

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [FakePermissionController] the way a consumer's own tests would -- driving a
 * permission through the realistic multi-step flows PermPilot is meant to make trivial, not
 * trivially asserting getters equal their setters.
 */
class FakePermissionControllerTest {
    @Test
    fun `a permission never touched reports NotDetermined by default`() {
        val controller = FakePermissionController()
        assertEquals(PermissionState.NotDetermined, controller.state(Permission.Camera).value)
    }

    @Test
    fun `deny-then-retry-and-grant is a realistic two-request flow`() =
        runTest {
            // Simulates a user who denies Camera once (but can still be asked again), then a second
            // screen visit re-requests it and this time they grant -- the exact flow PermissionGate's
            // rationale-dialog retry button drives in a real app.
            val controller = FakePermissionController()

            controller.setState(Permission.Camera, PermissionState.Denied(canRequestAgain = true))
            val firstAttempt = controller.request(Permission.Camera)
            assertEquals(PermissionState.Denied(canRequestAgain = true), firstAttempt)
            assertEquals(firstAttempt, controller.state(Permission.Camera).value)

            controller.setState(Permission.Camera, PermissionState.Granted)
            val secondAttempt = controller.request(Permission.Camera)
            assertEquals(PermissionState.Granted, secondAttempt)

            assertEquals(listOf(Permission.Camera, Permission.Camera), controller.requestCalls)
        }

    @Test
    fun `permanently denied never resolves without an explicit setState -- mirrors real Settings-only recovery`() =
        runTest {
            val controller = FakePermissionController()
            controller.setState(Permission.Microphone, PermissionState.PermanentlyDenied)

            repeat(3) {
                assertEquals(PermissionState.PermanentlyDenied, controller.request(Permission.Microphone))
            }
            // Only a real state() change (the fake's stand-in for "user granted it in Settings")
            // moves it off PermanentlyDenied -- calling request() again, like the real controllers,
            // never re-prompts a permanently-denied permission on its own.
            controller.setState(Permission.Microphone, PermissionState.Granted)
            assertEquals(PermissionState.Granted, controller.request(Permission.Microphone))
        }

    @Test
    fun `requestAll resolves each permission independently and records every call`() =
        runTest {
            val controller = FakePermissionController()
            controller.setState(Permission.Camera, PermissionState.Granted)
            controller.setState(Permission.Microphone, PermissionState.Denied(canRequestAgain = true))
            controller.setState(Permission.Contacts, PermissionState.Restricted)

            val results: Map<Permission, PermissionState> =
                controller.requestAll(Permission.Camera, Permission.Microphone, Permission.Contacts)

            val expected: Map<Permission, PermissionState> =
                mapOf(
                    Permission.Camera to PermissionState.Granted,
                    Permission.Microphone to PermissionState.Denied(canRequestAgain = true),
                    Permission.Contacts to PermissionState.Restricted,
                )
            assertEquals(expected, results)
            assertEquals(listOf(Permission.Camera, Permission.Microphone, Permission.Contacts), controller.requestCalls)
        }

    @Test
    fun `distinct Calendar access tiers are tracked as independent permissions`() {
        // Permission.Calendar is a data class -- Full and WriteOnly must not collide on the same
        // fake state slot even though they share a class, mirroring the real controllers' maps.
        val controller = FakePermissionController()
        controller.setState(Permission.Calendar(CalendarAccess.Full), PermissionState.Granted)
        controller.setState(Permission.Calendar(CalendarAccess.WriteOnly), PermissionState.Denied(canRequestAgain = true))

        assertEquals(PermissionState.Granted, controller.state(Permission.Calendar(CalendarAccess.Full)).value)
        assertEquals(
            PermissionState.Denied(canRequestAgain = true),
            controller.state(Permission.Calendar(CalendarAccess.WriteOnly)).value,
        )
    }

    @Test
    fun `openAppSettings calls are recorded in order -- distinguishing special from generic`() {
        val controller = FakePermissionController()

        controller.openAppSettings(Permission.SystemAlertWindow)
        controller.openAppSettings() // e.g. a PermanentlyDenied Runtime permission's settings prompt
        controller.openAppSettings(Permission.ExactAlarm)

        assertEquals(
            listOf<Permission?>(Permission.SystemAlertWindow, null, Permission.ExactAlarm),
            controller.openAppSettingsCalls,
        )
        assertNull(controller.openAppSettingsCalls[1])
    }

    @Test
    fun `refreshAll is a no-op -- a fake's state only ever moves via setState`() {
        val controller = FakePermissionController()
        controller.setState(Permission.LocationWhileInUse, PermissionState.Limited(LimitedReason.ApproximateLocationOnly))

        controller.refreshAll()

        assertEquals(
            PermissionState.Limited(LimitedReason.ApproximateLocationOnly),
            controller.state(Permission.LocationWhileInUse).value,
        )
    }

    @Test
    fun `custom initialState seeds every never-touched permission -- not just the first one queried`() {
        val controller = FakePermissionController(initialState = PermissionState.Restricted)

        assertTrue(controller.state(Permission.Camera).value is PermissionState.Restricted)
        assertTrue(controller.state(Permission.Microphone).value is PermissionState.Restricted)
    }
}
