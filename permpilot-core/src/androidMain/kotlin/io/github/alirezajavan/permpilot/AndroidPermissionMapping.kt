package io.github.alirezajavan.permpilot

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build

/**
 * Maps a [Permission.Runtime] to the manifest permission string(s) that must be requested
 * together for it, gated to the exact API level each one was introduced at (see PLAN.md §6).
 * Kept separate from [AndroidPermissionController] so the orchestration logic (staging,
 * persistence, coroutine bridging) doesn't get buried under platform-version trivia.
 */
@SuppressLint("InlinedApi")
internal fun Permission.Runtime.toManifestPermissions(): List<String> = when (this) {
    Permission.Camera -> listOf(Manifest.permission.CAMERA)
    Permission.Microphone -> listOf(Manifest.permission.RECORD_AUDIO)
    Permission.Contacts -> listOf(Manifest.permission.READ_CONTACTS)
    // Google Play policy (and the OS permission-rationale dialog since Android 12) expects fine
    // and coarse foreground location to be requested together so the user can pick either grade.
    Permission.LocationWhileInUse -> listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    // Actual sequencing (foreground-first, separate dialog) lives in requestBackgroundLocation();
    // this list is only consulted directly by checkLocationAlwaysState()'s manifest-permission plumbing.
    Permission.LocationAlways -> if (Build.VERSION.SDK_INT >= 29) {
        listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        emptyList()
    }
    // Android has no write-only calendar tier; CalendarAccess.WriteOnly is ignored and full
    // read+write access is always requested together.
    is Permission.Calendar -> listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    Permission.Notifications -> if (Build.VERSION.SDK_INT >= 33) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyList()
    }
    Permission.PhotoLibrary -> when {
        // API 34+: request full + partial-selection permissions together in one dialog so the
        // "Select photos" (partial access) option is offered, per the official Photo Picker docs.
        Build.VERSION.SDK_INT >= 34 -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT == 33 -> listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    Permission.BluetoothScan -> listOf(
        if (Build.VERSION.SDK_INT >= 31) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            // Pre-Android 12, BLE scan results require foreground location instead of a Bluetooth
            // runtime permission (BLUETOOTH/BLUETOOTH_ADMIN were normal, install-time permissions).
            Manifest.permission.ACCESS_FINE_LOCATION
        }
    )
    Permission.NearbyWifiDevices -> listOf(
        if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            // Wi-Fi scanning APIs required foreground location before NEARBY_WIFI_DEVICES existed.
            Manifest.permission.ACCESS_FINE_LOCATION
        }
    )
    Permission.BodySensors -> listOf(Manifest.permission.BODY_SENSORS)
    // Actual sequencing (foreground BodySensors first, separate dialog) lives in
    // requestBodySensorsBackground(); this list is only consulted by the manifest-permission
    // plumbing shared with checkBodySensorsBackgroundState().
    Permission.BodySensorsBackground -> if (Build.VERSION.SDK_INT >= 33) {
        listOf(Manifest.permission.BODY_SENSORS_BACKGROUND)
    } else {
        emptyList()
    }
    Permission.WriteContacts -> listOf(Manifest.permission.WRITE_CONTACTS)
    Permission.CallPhone -> listOf(Manifest.permission.CALL_PHONE)
    Permission.ReadPhoneState -> listOf(Manifest.permission.READ_PHONE_STATE)
    Permission.ReadPhoneNumbers -> listOf(Manifest.permission.READ_PHONE_NUMBERS)
    Permission.AnswerPhoneCalls -> listOf(Manifest.permission.ANSWER_PHONE_CALLS)
    Permission.ReadCallLog -> listOf(Manifest.permission.READ_CALL_LOG)
    Permission.WriteCallLog -> listOf(Manifest.permission.WRITE_CALL_LOG)
    Permission.SendSms -> listOf(Manifest.permission.SEND_SMS)
    Permission.ReadSms -> listOf(Manifest.permission.READ_SMS)
    Permission.ReceiveSms -> listOf(Manifest.permission.RECEIVE_SMS)
    Permission.ActivityRecognition -> if (Build.VERSION.SDK_INT >= 29) {
        listOf(Manifest.permission.ACTIVITY_RECOGNITION)
    } else {
        // Pre-API 29, step-counter/detector sensors were gated by BODY_SENSORS instead of a
        // dedicated permission.
        listOf(Manifest.permission.BODY_SENSORS)
    }
    Permission.AudioFiles -> if (Build.VERSION.SDK_INT >= 33) {
        listOf(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    // No Android equivalent to iOS's App Tracking Transparency, Speech Recognition, or Reminders
    // exists; all three are treated as always granted here.
    Permission.AppTrackingTransparency, Permission.SpeechRecognition, Permission.Reminders -> emptyList()
}

/**
 * The subset of [toManifestPermissions] that must actually be *declared* for this permission to
 * be usable at all. Usually identical, but two catalog entries have legitimate, Google-documented
 * partial-declaration setups that must not be flagged as integration mistakes:
 * - [Permission.LocationWhileInUse]: declaring only `ACCESS_COARSE_LOCATION` is the documented
 *   "approximate is enough" configuration -- the bundled FINE request is then auto-denied by the
 *   OS and the flow correctly resolves `Limited(ApproximateLocationOnly)`.
 * - [Permission.PhotoLibrary] on API 34+: `READ_MEDIA_VISUAL_USER_SELECTED` only enables the
 *   partial-selection tier; full grant/deny works without it.
 */
internal fun Permission.Runtime.requiredManifestDeclarations(): List<String> = when (this) {
    Permission.LocationWhileInUse -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    Permission.PhotoLibrary -> if (Build.VERSION.SDK_INT >= 34) {
        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        toManifestPermissions()
    }
    else -> toManifestPermissions()
}

/** Stable per-permission key for the persisted "has this ever been requested" flag store. */
internal fun Permission.persistenceKey(): String = this::class.simpleName ?: this.toString()

/**
 * Pure post-request/post-check grant-result interpretation, decoupled from any live Context or
 * Activity so it's directly unit-testable. [canShowRationale] and [sdkInt] are the two effectful
 * reads (`ActivityCompat.shouldShowRequestPermissionRationale`, `Build.VERSION.SDK_INT`) that
 * [AndroidPermissionController] computes for real and passes in -- kept separate for the same
 * reason as [Permission.Runtime.toManifestPermissions]: orchestration stays readable, and this
 * exact logic is what the two hard-won bugs in this library (the Accompanist Denied/
 * PermanentlyDenied ambiguity, and the coarse-location-misreported-as-Denied bug) both live in.
 */

// Disambiguating "never asked" from "permanently denied" needs shouldShowRequestPermissionRationale,
// which itself returns false in both cases -- the caller's own hadRequestedBefore flag is what lets
// them be told apart (this is the exact ambiguity Accompanist shipped a bug around: treating the
// first-ever denial, where the OS also reports no-rationale, as permanently denied).
internal fun resolveDeniedStateFrom(canShowRationale: Boolean, hadRequestedBefore: Boolean): PermissionState = when {
    canShowRationale -> PermissionState.Denied(canRequestAgain = true)
    !hadRequestedBefore -> PermissionState.Denied(canRequestAgain = true)
    else -> PermissionState.PermanentlyDenied
}

internal fun resolveGrantResult(
    manifestPermissions: List<String>,
    results: Map<String, Boolean>,
    hadRequestedBefore: Boolean,
    canShowRationale: Boolean,
): PermissionState {
    val allGranted = manifestPermissions.all { results[it] == true }
    return if (allGranted) {
        PermissionState.Granted
    } else {
        resolveDeniedStateFrom(canShowRationale, hadRequestedBefore)
    }
}

// Fine and coarse location are always requested together (see toManifestPermissions()), and the
// system dialog lets the user grant *just* coarse -- a legitimate, working grant, not a denial.
// Treating "not all granted" as Denied here would permanently misreport that choice as a denial
// on every subsequent state() check, since shouldShowRequestPermissionRationale stays true for
// the withheld FINE permission.
internal fun resolveForegroundLocationGrantResult(
    results: Map<String, Boolean>,
    hadRequestedBefore: Boolean,
    canShowRationale: Boolean,
): PermissionState {
    val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
    val coarseGranted = results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    return when {
        fineGranted -> PermissionState.Granted
        coarseGranted -> PermissionState.Limited(LimitedReason.ApproximateLocationOnly)
        else -> resolveDeniedStateFrom(canShowRationale, hadRequestedBefore)
    }
}

@SuppressLint("InlinedApi")
internal fun resolvePhotoLibraryGrantResult(
    sdkInt: Int,
    results: Map<String, Boolean>,
    hadRequestedBefore: Boolean,
    canShowRationale: Boolean,
): PermissionState = when {
    sdkInt >= 34 -> {
        val fullGranted = results[Manifest.permission.READ_MEDIA_IMAGES] == true &&
            results[Manifest.permission.READ_MEDIA_VIDEO] == true
        val partialGranted = results[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true
        when {
            fullGranted -> PermissionState.Granted
            partialGranted -> PermissionState.Limited(LimitedReason.PartialMediaAccess)
            else -> resolveDeniedStateFrom(canShowRationale, hadRequestedBefore)
        }
    }
    sdkInt == 33 -> {
        val granted = results[Manifest.permission.READ_MEDIA_IMAGES] == true &&
            results[Manifest.permission.READ_MEDIA_VIDEO] == true
        if (granted) {
            PermissionState.Granted
        } else {
            resolveDeniedStateFrom(canShowRationale, hadRequestedBefore)
        }
    }
    else -> {
        val granted = results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        if (granted) {
            PermissionState.Granted
        } else {
            resolveDeniedStateFrom(canShowRationale, hadRequestedBefore)
        }
    }
}
