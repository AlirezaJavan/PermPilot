package io.github.alirezajavan.permpilot.history

import io.github.alirezajavan.permpilot.Permission
import io.github.alirezajavan.permpilot.PermissionController
import io.github.alirezajavan.permpilot.PermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Wraps any [PermissionController] -- the real Android/iOS one, or a [io.github.alirezajavan.permpilot.FakePermissionController]
 * in tests -- recording every request/resolution/settings-open into [store], then delegating the
 * actual work unchanged. Deliberately a decorator rather than a new method on [PermissionController]
 * itself: the core interface is binary-compat-locked (see PLAN.md's binary-compatibility-validator
 * entry) and most consumers don't want an audit log, so this stays fully opt-in in a separate module.
 *
 * [scope] is only used to fire-and-forget the [PermissionEventType.SettingsOpened] record, since
 * [PermissionController.openAppSettings] is not itself suspend (it's a fire-and-forget navigation
 * action) -- [request]/[requestAll] await their own recording directly since they're already suspend.
 */
class HistoryPermissionController(
    private val delegate: PermissionController,
    private val store: PermissionHistoryStore,
    private val scope: CoroutineScope,
) : PermissionController by delegate {

    override suspend fun request(permission: Permission.Runtime): PermissionState {
        store.record(permission, PermissionEventType.Requested)
        return delegate.request(permission).also { state ->
            // NonCancellable so a caller cancelled right as the request resolves can't leave the
            // log with a dangling Requested and no matching Resolved -- once an outcome exists,
            // recording it must complete.
            withContext(NonCancellable) {
                store.record(permission, PermissionEventType.Resolved, state)
            }
        }
    }

    override suspend fun requestAll(vararg permissions: Permission.Runtime): Map<Permission, PermissionState> {
        permissions.forEach { store.record(it, PermissionEventType.Requested) }
        return delegate.requestAll(*permissions).also { results ->
            withContext(NonCancellable) {
                results.forEach { (permission, state) -> store.record(permission, PermissionEventType.Resolved, state) }
            }
        }
    }

    override fun openAppSettings() {
        delegate.openAppSettings()
        scope.launch { store.record(permission = null, PermissionEventType.SettingsOpened) }
    }

    override fun openAppSettings(special: Permission.Special) {
        delegate.openAppSettings(special)
        scope.launch { store.record(special, PermissionEventType.SettingsOpened) }
    }
}
