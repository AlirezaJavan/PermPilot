package io.github.alirezajavan.permpilot

import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusAuthorized
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusDenied
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusNotDetermined
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusRestricted
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
import platform.Contacts.CNAuthorizationStatusAuthorized
import platform.Contacts.CNAuthorizationStatusDenied
import platform.Contacts.CNAuthorizationStatusNotDetermined
import platform.Contacts.CNAuthorizationStatusRestricted
import platform.CoreLocation.CLAccuracyAuthorization
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMAuthorizationStatusDenied
import platform.CoreMotion.CMAuthorizationStatusNotDetermined
import platform.CoreMotion.CMAuthorizationStatusRestricted
import platform.EventKit.EKAuthorizationStatusAuthorized
import platform.EventKit.EKAuthorizationStatusDenied
import platform.EventKit.EKAuthorizationStatusNotDetermined
import platform.EventKit.EKAuthorizationStatusRestricted
import platform.MediaPlayer.MPMediaLibraryAuthorizationStatusAuthorized
import platform.MediaPlayer.MPMediaLibraryAuthorizationStatusDenied
import platform.MediaPlayer.MPMediaLibraryAuthorizationStatusNotDetermined
import platform.MediaPlayer.MPMediaLibraryAuthorizationStatusRestricted
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusDenied
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHAuthorizationStatusRestricted
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers every named-constant branch of IosPermissionStatusMapping.kt's pure mapping functions.
 * These compile and type-check via compileTestKotlinIosArm64/iosSimulatorArm64 on any host, but
 * Kotlin/Native test binaries for iOS targets can only *execute* on a Mac (with an iOS simulator
 * or, for iosArm64, a real device) -- there is no way to run them from this Windows environment.
 * Compilation success is still meaningful: it's the same "real klib codegen" feedback loop
 * CLAUDE.md documents for the iOS actuals themselves (wrong cinterop symbol name/enum shape would
 * fail here exactly like it would in the production code).
 */
class IosPermissionStatusMappingTest {

    @Test
    fun `camera and microphone authorization map identically via mapAVAuthorizationStatus`() {
        assertEquals(PermissionState.Granted, mapAVAuthorizationStatus(AVAuthorizationStatusAuthorized))
        assertEquals(PermissionState.NotDetermined, mapAVAuthorizationStatus(AVAuthorizationStatusNotDetermined))
        assertEquals(PermissionState.PermanentlyDenied, mapAVAuthorizationStatus(AVAuthorizationStatusDenied))
        assertEquals(PermissionState.Restricted, mapAVAuthorizationStatus(AVAuthorizationStatusRestricted))
    }

    @Test
    fun `contacts authorization -- a one-shot prompt -- Denied always means PermanentlyDenied`() {
        assertEquals(PermissionState.Granted, mapContactsAuthorizationStatus(CNAuthorizationStatusAuthorized))
        assertEquals(PermissionState.NotDetermined, mapContactsAuthorizationStatus(CNAuthorizationStatusNotDetermined))
        assertEquals(PermissionState.PermanentlyDenied, mapContactsAuthorizationStatus(CNAuthorizationStatusDenied))
        assertEquals(PermissionState.Restricted, mapContactsAuthorizationStatus(CNAuthorizationStatusRestricted))
    }

    @Test
    fun `calendar authorization -- same shape as contacts for the four pre-iOS17 statuses`() {
        assertEquals(PermissionState.Granted, mapCalendarAuthorizationStatus(EKAuthorizationStatusAuthorized))
        assertEquals(PermissionState.NotDetermined, mapCalendarAuthorizationStatus(EKAuthorizationStatusNotDetermined))
        assertEquals(PermissionState.PermanentlyDenied, mapCalendarAuthorizationStatus(EKAuthorizationStatusDenied))
        assertEquals(PermissionState.Restricted, mapCalendarAuthorizationStatus(EKAuthorizationStatusRestricted))
    }

    @Test
    fun `photo library limited access maps to Limited PartialMediaAccess -- not Granted or Denied`() {
        assertEquals(PermissionState.Granted, mapPhotoLibraryStatus(PHAuthorizationStatusAuthorized))
        assertEquals(
            PermissionState.Limited(LimitedReason.PartialMediaAccess),
            mapPhotoLibraryStatus(PHAuthorizationStatusLimited)
        )
        assertEquals(PermissionState.NotDetermined, mapPhotoLibraryStatus(PHAuthorizationStatusNotDetermined))
        assertEquals(PermissionState.PermanentlyDenied, mapPhotoLibraryStatus(PHAuthorizationStatusDenied))
        assertEquals(PermissionState.Restricted, mapPhotoLibraryStatus(PHAuthorizationStatusRestricted))
    }

    // --- Location: authorization tier x accuracy tier, the exact matrix the coarse-location
    // audit bug lived in (PLAN.md §9.1's Android counterpart; this is the iOS side of that fix).

    @Test
    fun `when-in-use plus full accuracy not upgrading to Always is a plain Granted`() {
        val state = mapLocationStatus(
            kCLAuthorizationStatusAuthorizedWhenInUse,
            requestedAlways = false,
            CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy
        )
        assertEquals(PermissionState.Granted, state)
    }

    @Test
    fun `when-in-use plus reduced accuracy is Limited ApproximateLocationOnly -- not Granted`() {
        val state = mapLocationStatus(
            kCLAuthorizationStatusAuthorizedWhenInUse,
            requestedAlways = false,
            CLAccuracyAuthorization.CLAccuracyAuthorizationReducedAccuracy
        )
        assertEquals(PermissionState.Limited(LimitedReason.ApproximateLocationOnly), state)
    }

    @Test
    fun `when-in-use while requesting Always is a retryable Denied -- not PermanentlyDenied`() {
        // requestAlwaysAuthorization() is a silent no-op that leaves the user at when-in-use --
        // this must stay retryable (e.g. after more app usage), never look like a hard denial.
        val state = mapLocationStatus(
            kCLAuthorizationStatusAuthorizedWhenInUse,
            requestedAlways = true,
            CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy
        )
        assertEquals(PermissionState.Denied(canRequestAgain = true), state)
    }

    @Test
    fun `always authorization plus full accuracy is Granted`() {
        val state = mapLocationStatus(
            kCLAuthorizationStatusAuthorizedAlways,
            requestedAlways = true,
            CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy
        )
        assertEquals(PermissionState.Granted, state)
    }

    @Test
    fun `always authorization plus reduced accuracy is still Limited -- accuracy is independent of tier`() {
        val state = mapLocationStatus(
            kCLAuthorizationStatusAuthorizedAlways,
            requestedAlways = true,
            CLAccuracyAuthorization.CLAccuracyAuthorizationReducedAccuracy
        )
        assertEquals(PermissionState.Limited(LimitedReason.ApproximateLocationOnly), state)
    }

    @Test
    fun `location NotDetermined Restricted and Denied are unaffected by accuracy or the always flag`() {
        val fullAccuracy = CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy
        assertEquals(
            PermissionState.NotDetermined,
            mapLocationStatus(kCLAuthorizationStatusNotDetermined, requestedAlways = false, fullAccuracy)
        )
        assertEquals(
            PermissionState.Restricted,
            mapLocationStatus(kCLAuthorizationStatusRestricted, requestedAlways = true, fullAccuracy)
        )
        assertEquals(
            PermissionState.PermanentlyDenied,
            mapLocationStatus(kCLAuthorizationStatusDenied, requestedAlways = false, fullAccuracy)
        )
    }

    @Test
    fun `notifications -- provisional quiet non-interrupting authorization counts as Granted`() {
        assertEquals(PermissionState.Granted, mapNotificationStatus(UNAuthorizationStatusAuthorized))
        assertEquals(PermissionState.Granted, mapNotificationStatus(UNAuthorizationStatusProvisional))
        assertEquals(PermissionState.PermanentlyDenied, mapNotificationStatus(UNAuthorizationStatusDenied))
        assertEquals(PermissionState.NotDetermined, mapNotificationStatus(UNAuthorizationStatusNotDetermined))
    }

    @Test
    fun `bluetooth authorization maps its four states the standard way`() {
        assertEquals(PermissionState.Granted, mapBluetoothAuthorization(CBManagerAuthorizationAllowedAlways))
        assertEquals(PermissionState.PermanentlyDenied, mapBluetoothAuthorization(CBManagerAuthorizationDenied))
        assertEquals(PermissionState.Restricted, mapBluetoothAuthorization(CBManagerAuthorizationRestricted))
        assertEquals(PermissionState.NotDetermined, mapBluetoothAuthorization(CBManagerAuthorizationNotDetermined))
    }

    @Test
    fun `activity recognition Core Motion maps its four states the standard way`() {
        assertEquals(PermissionState.Granted, mapActivityRecognitionStatus(CMAuthorizationStatusAuthorized))
        assertEquals(PermissionState.NotDetermined, mapActivityRecognitionStatus(CMAuthorizationStatusNotDetermined))
        assertEquals(PermissionState.PermanentlyDenied, mapActivityRecognitionStatus(CMAuthorizationStatusDenied))
        assertEquals(PermissionState.Restricted, mapActivityRecognitionStatus(CMAuthorizationStatusRestricted))
    }

    @Test
    fun `audio files Apple Music library maps its four states the standard way`() {
        assertEquals(PermissionState.Granted, mapAudioFilesStatus(MPMediaLibraryAuthorizationStatusAuthorized))
        assertEquals(PermissionState.NotDetermined, mapAudioFilesStatus(MPMediaLibraryAuthorizationStatusNotDetermined))
        assertEquals(PermissionState.PermanentlyDenied, mapAudioFilesStatus(MPMediaLibraryAuthorizationStatusDenied))
        assertEquals(PermissionState.Restricted, mapAudioFilesStatus(MPMediaLibraryAuthorizationStatusRestricted))
    }

    @Test
    fun `speech recognition maps its four states through the nested enum binding`() {
        // SFSpeechRecognizerAuthorizationStatus binds as a real nested Kotlin enum (unlike the
        // flat top-level constants everything else here uses) -- verified by this compiling.
        assertEquals(
            PermissionState.Granted,
            mapSpeechRecognitionStatus(SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized)
        )
        assertEquals(
            PermissionState.NotDetermined,
            mapSpeechRecognitionStatus(SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusNotDetermined)
        )
        assertEquals(
            PermissionState.PermanentlyDenied,
            mapSpeechRecognitionStatus(SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusDenied)
        )
        assertEquals(
            PermissionState.Restricted,
            mapSpeechRecognitionStatus(SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusRestricted)
        )
    }

    @Test
    fun `app tracking transparency maps its four states the standard way`() {
        assertEquals(PermissionState.Granted, mapTrackingStatus(ATTrackingManagerAuthorizationStatusAuthorized))
        assertEquals(PermissionState.NotDetermined, mapTrackingStatus(ATTrackingManagerAuthorizationStatusNotDetermined))
        assertEquals(PermissionState.PermanentlyDenied, mapTrackingStatus(ATTrackingManagerAuthorizationStatusDenied))
        assertEquals(PermissionState.Restricted, mapTrackingStatus(ATTrackingManagerAuthorizationStatusRestricted))
    }
}
