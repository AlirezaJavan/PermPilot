package io.github.alirezajavan.permpilot

import platform.Foundation.NSBundle

/**
 * The `NS*UsageDescription` Info.plist key(s) each [Permission.Runtime] needs before its native
 * authorization API can be called safely. PermPilot can't inject these into a consumer's
 * Info.plist -- calling the native API without the key present terminates the process -- so
 * [hasRequiredUsageDescriptions] is checked first and [PermissionState.ConfigurationError] is
 * reported instead of ever making that call. Empty list means "no Info.plist key required."
 */
internal fun Permission.Runtime.requiredInfoPlistKeys(): List<String> =
    when (this) {
        Permission.Camera -> listOf("NSCameraUsageDescription")
        Permission.Microphone -> listOf("NSMicrophoneUsageDescription")
        Permission.Contacts -> listOf("NSContactsUsageDescription")
        is Permission.Calendar -> listOf("NSCalendarsUsageDescription")
        Permission.PhotoLibrary -> listOf("NSPhotoLibraryUsageDescription")
        Permission.LocationWhileInUse -> listOf("NSLocationWhenInUseUsageDescription")
        // iOS requires NSLocationWhenInUseUsageDescription regardless (the foreground step is always
        // requested first), plus NSLocationAlwaysAndWhenInUseUsageDescription for the Always upgrade.
        Permission.LocationAlways ->
            listOf(
                "NSLocationWhenInUseUsageDescription",
                "NSLocationAlwaysAndWhenInUseUsageDescription",
            )
        Permission.BluetoothScan,
        Permission.BluetoothConnect,
        Permission.BluetoothAdvertise,
        -> listOf("NSBluetoothAlwaysUsageDescription")
        Permission.AppTrackingTransparency -> listOf("NSUserTrackingUsageDescription")
        Permission.Notifications -> emptyList() // UNUserNotificationCenter needs no Info.plist key.
        Permission.WriteContacts -> listOf("NSContactsUsageDescription") // same CNContactStore prompt as Contacts
        Permission.ActivityRecognition -> listOf("NSMotionUsageDescription")
        Permission.AudioFiles -> listOf("NSAppleMusicUsageDescription")
        Permission.SpeechRecognition -> listOf("NSSpeechRecognitionUsageDescription")
        Permission.Reminders -> listOf("NSRemindersUsageDescription")
        Permission.MediaLocation -> emptyList() // iOS no-op Granted.
        is Permission.Health ->
            listOfNotNull(
                "NSHealthShareUsageDescription".takeIf { access != HealthAccess.Write },
                "NSHealthUpdateUsageDescription".takeIf { access != HealthAccess.Read },
            )
        // Android-only concepts; no iOS Info.plist requirement since the actuals never touch native APIs.
        Permission.NearbyWifiDevices, Permission.BodySensors, Permission.BodySensorsBackground,
        Permission.CallPhone, Permission.ReadPhoneState, Permission.ReadPhoneNumbers, Permission.AnswerPhoneCalls,
        Permission.ReadCallLog, Permission.WriteCallLog,
        Permission.SendSms, Permission.ReadSms, Permission.ReceiveSms,
        -> emptyList()
    }

internal fun hasRequiredUsageDescriptions(permission: Permission.Runtime): Boolean {
    val requiredKeys = permission.requiredInfoPlistKeys()
    if (requiredKeys.isEmpty()) return true
    val infoDictionary = NSBundle.mainBundle.infoDictionary ?: return false
    return requiredKeys.all { infoDictionary.containsKey(it) }
}
