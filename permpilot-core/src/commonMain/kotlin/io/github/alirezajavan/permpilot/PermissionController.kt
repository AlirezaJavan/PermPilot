package io.github.alirezajavan.permpilot

import kotlinx.coroutines.flow.StateFlow

interface PermissionController {
    fun state(permission: Permission): StateFlow<PermissionState>

    suspend fun request(permission: Permission.Runtime): PermissionState

    suspend fun requestAll(vararg permissions: Permission.Runtime): Map<Permission, PermissionState>

    fun openAppSettings()

    fun openAppSettings(special: Permission.Special)

    /**
     * Re-checks and republishes the state of every [Permission] currently being observed via
     * [state]. There is no OS callback for "the user changed a permission in Settings and came
     * back" -- the host app must trigger this itself when it becomes foreground again (e.g. from
     * a lifecycle `ON_RESUME` event). [Permission.Special] permissions in particular have no
     * request flow at all, so this is the only way their [state] ever updates after the first check.
     */
    fun refreshAll()
}
