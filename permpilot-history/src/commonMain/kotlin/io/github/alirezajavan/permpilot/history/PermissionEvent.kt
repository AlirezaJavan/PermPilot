package io.github.alirezajavan.permpilot.history

/** What happened, as opposed to *what the current state is* -- that's [PermissionHistoryEntry.state]. */
enum class PermissionEventType {
    /** [io.github.alirezajavan.permpilot.PermissionController.request] or `requestAll` was called. */
    Requested,

    /** A request resolved to a [io.github.alirezajavan.permpilot.PermissionState]. */
    Resolved,

    /** [io.github.alirezajavan.permpilot.PermissionController.openAppSettings] was called. */
    SettingsOpened,
}

/**
 * One row of the audit log. [permissionKey] is the permission's stable class name (e.g. `"Camera"`,
 * `"Calendar"`) -- a snapshot for readability/querying, not a reference back to a live [io.github.alirezajavan.permpilot.Permission]
 * instance. [state] is likewise a human-readable snapshot (`PermissionState.toString()`, e.g.
 * `"Denied(canRequestAgain=true)"`), not re-parsed back into a typed value -- an audit log's job is
 * recording what happened, not round-tripping strongly-typed state.
 */
data class PermissionHistoryEntry(
    val id: Long,
    val permissionKey: String,
    val type: PermissionEventType,
    val state: String?,
    val timestampMillis: Long,
)
