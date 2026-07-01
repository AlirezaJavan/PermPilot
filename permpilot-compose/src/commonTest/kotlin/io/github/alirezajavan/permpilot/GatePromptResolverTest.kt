package io.github.alirezajavan.permpilot

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The complete PermissionState x dismissed-for decision matrix for [PermissionGate], testable
 * without a Compose runtime. Test names stay Obj-C-symbol-safe (no commas/parens) because this
 * source set also compiles for the iOS Kotlin/Native test targets.
 */
class GatePromptResolverTest {

    @Test
    fun `NotDetermined shows the rationale prompt`() {
        assertEquals(GatePrompt.Rationale, resolveGatePrompt(PermissionState.NotDetermined, dismissedFor = null))
    }

    @Test
    fun `Granted shows no prompt`() {
        assertEquals(GatePrompt.None, resolveGatePrompt(PermissionState.Granted, dismissedFor = null))
    }

    @Test
    fun `Limited is a working grant and shows no prompt`() {
        assertEquals(
            GatePrompt.None,
            resolveGatePrompt(PermissionState.Limited(LimitedReason.ApproximateLocationOnly), dismissedFor = null)
        )
        assertEquals(
            GatePrompt.None,
            resolveGatePrompt(PermissionState.Limited(LimitedReason.PartialMediaAccess), dismissedFor = null)
        )
    }

    @Test
    fun `Denied with canRequestAgain shows the rationale prompt again`() {
        assertEquals(
            GatePrompt.Rationale,
            resolveGatePrompt(PermissionState.Denied(canRequestAgain = true), dismissedFor = null)
        )
    }

    @Test
    fun `Denied without canRequestAgain falls back to the settings prompt`() {
        assertEquals(
            GatePrompt.Settings,
            resolveGatePrompt(PermissionState.Denied(canRequestAgain = false), dismissedFor = null)
        )
    }

    @Test
    fun `PermanentlyDenied shows the settings prompt`() {
        assertEquals(GatePrompt.Settings, resolveGatePrompt(PermissionState.PermanentlyDenied, dismissedFor = null))
    }

    @Test
    fun `Restricted is terminal and shows the restricted notice with no settings fallback`() {
        assertEquals(GatePrompt.Restricted, resolveGatePrompt(PermissionState.Restricted, dismissedFor = null))
    }

    @Test
    fun `ConfigurationError surfaces its reason through the dedicated notice`() {
        assertEquals(
            GatePrompt.ConfigurationError(ConfigurationErrorReason.NoHostActivity),
            resolveGatePrompt(
                PermissionState.ConfigurationError(ConfigurationErrorReason.NoHostActivity),
                dismissedFor = null
            )
        )
    }

    @Test
    fun `a prompt dismissed for the current state stays silent`() {
        val denied = PermissionState.Denied(canRequestAgain = true)
        assertEquals(GatePrompt.None, resolveGatePrompt(denied, dismissedFor = denied))
        assertEquals(
            GatePrompt.None,
            resolveGatePrompt(PermissionState.NotDetermined, dismissedFor = PermissionState.NotDetermined)
        )
    }

    @Test
    fun `a dismissed prompt reappears once the underlying state changes`() {
        // User dismissed the rationale while NotDetermined; the request then resolved Denied --
        // the gate must re-evaluate instead of staying silent on the stale dismissal.
        assertEquals(
            GatePrompt.Rationale,
            resolveGatePrompt(
                PermissionState.Denied(canRequestAgain = true),
                dismissedFor = PermissionState.NotDetermined
            )
        )
        // Dismissed while Denied, came back from Settings still PermanentlyDenied -> settings prompt.
        assertEquals(
            GatePrompt.Settings,
            resolveGatePrompt(
                PermissionState.PermanentlyDenied,
                dismissedFor = PermissionState.Denied(canRequestAgain = true)
            )
        )
    }

    @Test
    fun `dismissal comparison is by value for states carrying payloads`() {
        // Denied(true) dismissed, Denied(false) arrives -- different values, so the gate speaks up.
        assertEquals(
            GatePrompt.Settings,
            resolveGatePrompt(
                PermissionState.Denied(canRequestAgain = false),
                dismissedFor = PermissionState.Denied(canRequestAgain = true)
            )
        )
    }
}
