package io.github.alirezajavan.permpilot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An in-memory [PermissionController] for consumers to use in their own tests -- ViewModel tests,
 * Compose UI tests of screens built on [PermissionGate]/`rememberPermissionController`, etc. --
 * so they never have to stand up a real Activity or iOS runtime just to drive a permission flow
 * through its states.
 *
 * [request] resolves to whatever [state] currently holds for that permission (defaulting to
 * [initialState], [PermissionState.NotDetermined] unless overridden); call [setState] beforehand
 * to script the specific outcome a test scenario needs -- e.g. set [PermissionState.Denied] with
 * `canRequestAgain = true` to simulate "user denied once, then retries and grants." [request]
 * always publishes its result back through [state] first, exactly like the real controllers, so
 * a `PermissionGate` observing this fake re-evaluates the same way it would against a device.
 */
class FakePermissionController(
    private val initialState: PermissionState = PermissionState.NotDetermined,
) : PermissionController {
    private val states = mutableMapOf<Permission, MutableStateFlow<PermissionState>>()

    /** Every [openAppSettings] invocation in call order; a plain (non-Special) call records `null`. */
    val openAppSettingsCalls: List<Permission?> get() = _openAppSettingsCalls
    private val _openAppSettingsCalls = mutableListOf<Permission?>()

    /** Every [request] invocation in call order, including repeats. */
    val requestCalls: List<Permission.Runtime> get() = _requestCalls
    private val _requestCalls = mutableListOf<Permission.Runtime>()

    fun setState(
        permission: Permission,
        state: PermissionState,
    ) {
        stateFlowFor(permission).value = state
    }

    override fun state(permission: Permission): StateFlow<PermissionState> = stateFlowFor(permission).asStateFlow()

    override suspend fun request(permission: Permission.Runtime): PermissionState {
        _requestCalls += permission
        return stateFlowFor(permission).value
    }

    override suspend fun requestAll(vararg permissions: Permission.Runtime): Map<Permission, PermissionState> =
        permissions.associate { (it as Permission) to request(it) }

    override fun openAppSettings() {
        _openAppSettingsCalls += null
    }

    override fun openAppSettings(special: Permission.Special) {
        _openAppSettingsCalls += special
    }

    // A fake's state only ever changes via setState(), which already publishes immediately --
    // there is no external OS to have drifted out from under it while backgrounded.
    override fun refreshAll() = Unit

    private fun stateFlowFor(permission: Permission): MutableStateFlow<PermissionState> =
        states.getOrPut(permission) { MutableStateFlow(initialState) }
}
