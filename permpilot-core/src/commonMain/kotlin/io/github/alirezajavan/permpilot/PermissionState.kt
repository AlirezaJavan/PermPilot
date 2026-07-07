package io.github.alirezajavan.permpilot

sealed interface PermissionState {
    data object NotDetermined : PermissionState

    data object Granted : PermissionState

    data class Denied(
        val canRequestAgain: Boolean,
    ) : PermissionState

    data object PermanentlyDenied : PermissionState

    data object Restricted : PermissionState

    data class Limited(
        val reason: LimitedReason,
    ) : PermissionState

    // Not a user permission decision -- a library integration mistake the app developer needs to
    // fix (a missing Info.plist usage-description key, or requesting before any Activity/window is
    // attached). Reported through the same exhaustive `when` as every other state instead of a
    // thrown exception or a silent hang/crash, so callers never need their own try/catch.
    data class ConfigurationError(
        val reason: ConfigurationErrorReason,
    ) : PermissionState
}

enum class LimitedReason {
    PartialMediaAccess,
    SelectedContactsOnly,

    /**
     * The user granted coarse/approximate location only when fine+coarse (or, on iOS, full
     * accuracy) was requested together -- a legitimate, working grant, not a denial. Android
     * surfaces this as `ACCESS_COARSE_LOCATION` granted while `ACCESS_FINE_LOCATION` is not; iOS
     * surfaces it as `CLAccuracyAuthorization` being `reducedAccuracy` while the authorization
     * status itself is still authorized (when-in-use or always).
     */
    ApproximateLocationOnly,
}

enum class ConfigurationErrorReason {
    /**
     * Android: [PermissionController.request] was called but no Activity is currently attached
     * (e.g. [rememberPermissionController] was never composed into an Activity-hosted tree).
     * Without one, the system dialog has nowhere to be shown, so PermPilot fails fast here instead
     * of suspending forever waiting for a result that will never arrive.
     */
    NoHostActivity,

    /**
     * iOS: the app's Info.plist is missing the `NS*UsageDescription` key this permission requires.
     * Calling the native authorization API without it terminates the process, so PermPilot checks
     * first and reports this instead of ever making that call.
     */
    MissingUsageDescription,

    /**
     * Android: the app's manifest is missing what this permission needs to be requestable -- a
     * `<uses-permission>` entry for runtime permissions (the OS instantly auto-denies requests
     * for undeclared permissions, which would otherwise be misreported as a user denial), the
     * `ACCESS_NOTIFICATION_POLICY` declaration [Permission.DoNotDisturbAccess] needs to appear in
     * its Settings list at all, or a declared `NotificationListenerService` for
     * [Permission.NotificationListenerAccess] (its Settings list only shows apps that have one).
     */
    MissingManifestDeclaration,
}
