package io.github.alirezajavan.permpilot

/**
 * Represents a discrete event in the permission lifecycle that can be observed for analytics,
 * logging, or debugging purposes.
 */
sealed interface PermissionEvent {
    /**
     * The permission this event concerns.
     */
    val permission: Permission

    /**
     * Emitted when a permission's state has changed, either via a direct [PermissionController.request]
     * or an external [PermissionController.refreshAll].
     */
    data class StateChanged(
        override val permission: Permission,
        val newState: PermissionState,
    ) : PermissionEvent

    /**
     * Emitted when a system permission dialog is about to be shown to the user.
     */
    data class RequestStarted(
        override val permission: Permission.Runtime,
    ) : PermissionEvent

    /**
     * Emitted when a system permission dialog has been dismissed and a result processed.
     */
    data class RequestResult(
        override val permission: Permission.Runtime,
        val result: PermissionState,
    ) : PermissionEvent
}
