package io.github.alirezajavan.permpilot

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.AVFoundation.AVAuthorizationStatus
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatus
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusAuthorized
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusDenied
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusNotDetermined
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusRestricted
import platform.CoreBluetooth.CBManagerAuthorization
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
import platform.Contacts.CNAuthorizationStatus
import platform.Contacts.CNAuthorizationStatusAuthorized
import platform.Contacts.CNAuthorizationStatusDenied
import platform.Contacts.CNAuthorizationStatusNotDetermined
import platform.Contacts.CNAuthorizationStatusRestricted
import platform.CoreLocation.CLAccuracyAuthorization
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreMotion.CMAuthorizationStatus
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMAuthorizationStatusDenied
import platform.CoreMotion.CMAuthorizationStatusNotDetermined
import platform.CoreMotion.CMAuthorizationStatusRestricted
import platform.EventKit.EKAuthorizationStatus
import platform.EventKit.EKAuthorizationStatusAuthorized
import platform.EventKit.EKAuthorizationStatusDenied
import platform.EventKit.EKAuthorizationStatusNotDetermined
import platform.EventKit.EKAuthorizationStatusRestricted
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.MediaPlayer.MPMediaLibraryAuthorizationStatus
import platform.MediaPlayer.MPMediaLibraryAuthorizationStatusAuthorized
import platform.MediaPlayer.MPMediaLibraryAuthorizationStatusDenied
import platform.MediaPlayer.MPMediaLibraryAuthorizationStatusNotDetermined
import platform.MediaPlayer.MPMediaLibraryAuthorizationStatusRestricted
import platform.Photos.PHAuthorizationStatus
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusDenied
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHAuthorizationStatusRestricted
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional

/**
 * Pure mappings from each Apple framework's native authorization enum to [PermissionState].
 * Kept separate from [IosPermissionController] so the orchestration logic (delegates,
 * continuations, staging) isn't buried under a wall of `when` blocks over platform enums.
 */

internal fun mapAVAuthorizationStatus(status: AVAuthorizationStatus): PermissionState = when (status) {
    AVAuthorizationStatusAuthorized -> PermissionState.Granted
    AVAuthorizationStatusNotDetermined -> PermissionState.NotDetermined
    AVAuthorizationStatusDenied -> PermissionState.PermanentlyDenied
    AVAuthorizationStatusRestricted -> PermissionState.Restricted
    else -> PermissionState.NotDetermined
}

internal fun mapContactsAuthorizationStatus(status: CNAuthorizationStatus): PermissionState = when (status) {
    CNAuthorizationStatusAuthorized -> PermissionState.Granted
    CNAuthorizationStatusNotDetermined -> PermissionState.NotDetermined
    CNAuthorizationStatusDenied -> PermissionState.PermanentlyDenied
    CNAuthorizationStatusRestricted -> PermissionState.Restricted
    else -> PermissionState.NotDetermined
}

internal fun mapCalendarAuthorizationStatus(status: EKAuthorizationStatus): PermissionState = when (status) {
    EKAuthorizationStatusAuthorized -> PermissionState.Granted
    EKAuthorizationStatusNotDetermined -> PermissionState.NotDetermined
    EKAuthorizationStatusDenied -> PermissionState.PermanentlyDenied
    EKAuthorizationStatusRestricted -> PermissionState.Restricted
    else -> PermissionState.Granted // iOS 17+ EKAuthorizationStatusFullAccess/WriteOnly land here
}

internal fun mapPhotoLibraryStatus(status: PHAuthorizationStatus): PermissionState = when (status) {
    PHAuthorizationStatusAuthorized -> PermissionState.Granted
    PHAuthorizationStatusLimited -> PermissionState.Limited(LimitedReason.PartialMediaAccess)
    PHAuthorizationStatusNotDetermined -> PermissionState.NotDetermined
    PHAuthorizationStatusDenied -> PermissionState.PermanentlyDenied
    PHAuthorizationStatusRestricted -> PermissionState.Restricted
    else -> PermissionState.NotDetermined
}

internal fun mapLocationStatus(
    status: CLAuthorizationStatus,
    requestedAlways: Boolean,
    accuracyAuthorization: CLAccuracyAuthorization,
): PermissionState = when (status) {
    kCLAuthorizationStatusNotDetermined -> PermissionState.NotDetermined
    kCLAuthorizationStatusRestricted -> PermissionState.Restricted
    kCLAuthorizationStatusDenied -> PermissionState.PermanentlyDenied
    kCLAuthorizationStatusAuthorizedAlways -> grantedLocationState(accuracyAuthorization)
    kCLAuthorizationStatusAuthorizedWhenInUse -> if (requestedAlways) {
        // Still only foreground access -- the always-upgrade prompt can be retried later
        // (e.g. after the user has used the app more), it isn't a hard permanent denial.
        PermissionState.Denied(canRequestAgain = true)
    } else {
        grantedLocationState(accuracyAuthorization)
    }
    else -> PermissionState.NotDetermined
}

// iOS 14+ lets the user grant location access at "Approximate" accuracy regardless of
// authorization tier (when-in-use or always) -- a legitimate, working grant, not a denial or a
// downgrade of the authorization tier itself, so it's modeled as Limited rather than Denied.
private fun grantedLocationState(accuracyAuthorization: CLAccuracyAuthorization): PermissionState =
    if (accuracyAuthorization == CLAccuracyAuthorization.CLAccuracyAuthorizationReducedAccuracy) {
        PermissionState.Limited(LimitedReason.ApproximateLocationOnly)
    } else {
        PermissionState.Granted
    }

internal fun mapNotificationStatus(status: UNAuthorizationStatus): PermissionState = when (status) {
    UNAuthorizationStatusAuthorized -> PermissionState.Granted
    UNAuthorizationStatusProvisional -> PermissionState.Granted
    UNAuthorizationStatusDenied -> PermissionState.PermanentlyDenied
    UNAuthorizationStatusNotDetermined -> PermissionState.NotDetermined
    else -> PermissionState.NotDetermined // e.g.ephemeral (App Clips)
}

internal fun mapBluetoothAuthorization(status: CBManagerAuthorization): PermissionState = when (status) {
    CBManagerAuthorizationAllowedAlways -> PermissionState.Granted
    CBManagerAuthorizationDenied -> PermissionState.PermanentlyDenied
    CBManagerAuthorizationRestricted -> PermissionState.Restricted
    CBManagerAuthorizationNotDetermined -> PermissionState.NotDetermined
    else -> PermissionState.NotDetermined
}

internal fun mapActivityRecognitionStatus(status: CMAuthorizationStatus): PermissionState = when (status) {
    CMAuthorizationStatusAuthorized -> PermissionState.Granted
    CMAuthorizationStatusNotDetermined -> PermissionState.NotDetermined
    CMAuthorizationStatusDenied -> PermissionState.PermanentlyDenied
    CMAuthorizationStatusRestricted -> PermissionState.Restricted
    else -> PermissionState.NotDetermined
}

internal fun mapAudioFilesStatus(status: MPMediaLibraryAuthorizationStatus): PermissionState = when (status) {
    MPMediaLibraryAuthorizationStatusAuthorized -> PermissionState.Granted
    MPMediaLibraryAuthorizationStatusNotDetermined -> PermissionState.NotDetermined
    MPMediaLibraryAuthorizationStatusDenied -> PermissionState.PermanentlyDenied
    MPMediaLibraryAuthorizationStatusRestricted -> PermissionState.Restricted
    else -> PermissionState.NotDetermined
}

internal fun mapSpeechRecognitionStatus(status: SFSpeechRecognizerAuthorizationStatus): PermissionState = when (status) {
    SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized -> PermissionState.Granted
    SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusNotDetermined -> PermissionState.NotDetermined
    SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusDenied -> PermissionState.PermanentlyDenied
    SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusRestricted -> PermissionState.Restricted
    else -> PermissionState.NotDetermined
}

internal fun mapTrackingStatus(status: ATTrackingManagerAuthorizationStatus): PermissionState = when (status) {
    ATTrackingManagerAuthorizationStatusAuthorized -> PermissionState.Granted
    ATTrackingManagerAuthorizationStatusNotDetermined -> PermissionState.NotDetermined
    ATTrackingManagerAuthorizationStatusDenied -> PermissionState.PermanentlyDenied
    ATTrackingManagerAuthorizationStatusRestricted -> PermissionState.Restricted
    else -> PermissionState.NotDetermined
}

@OptIn(ExperimentalForeignApi::class)
internal fun isAtLeastIOS(major: Int, minor: Int = 0): Boolean {
    val version = cValue<NSOperatingSystemVersion> {
        majorVersion = major.toLong()
        minorVersion = minor.toLong()
        patchVersion = 0
    }
    return NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(version)
}
