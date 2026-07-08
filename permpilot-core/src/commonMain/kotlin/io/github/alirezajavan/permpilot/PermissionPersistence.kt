package io.github.alirezajavan.permpilot

/**
 * Pluggable backend for the "has this permission been requested before" flag, used on Android
 * to disambiguate the initial [PermissionState.NotDetermined] from [PermissionState.PermanentlyDenied].
 */
interface PermissionPersistence {
    /**
     * Returns true if the given permission has been requested from the user before.
     */
    fun isRequested(permission: Permission): Boolean

    /**
     * Marks the given permission as having been requested.
     */
    fun markRequested(permission: Permission)
}

/**
 * A simple in-memory [PermissionPersistence] for testing or transient sessions.
 */
class InMemoryPermissionPersistence : PermissionPersistence {
    private val requested = mutableSetOf<String>()

    override fun isRequested(permission: Permission): Boolean = requested.contains(permission.persistenceKey())

    override fun markRequested(permission: Permission) {
        requested.add(permission.persistenceKey())
    }
}

internal fun Permission.persistenceKey(): String = this.toString()
