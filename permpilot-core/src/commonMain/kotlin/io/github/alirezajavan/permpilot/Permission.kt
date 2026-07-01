package io.github.alirezajavan.permpilot

sealed interface Permission {
    sealed interface Runtime : Permission
    sealed interface Special : Permission
    sealed interface PlatformLimited : Permission

    data object Camera : Runtime
    data object Microphone : Runtime
    data object LocationWhileInUse : Runtime
    data object LocationAlways : Runtime
    data object Notifications : Runtime
    data object Contacts : Runtime
    data class Calendar(val access: CalendarAccess = CalendarAccess.Full) : Runtime
    data object PhotoLibrary : Runtime
    data object BluetoothScan : Runtime
    // Android-only (Wi-Fi Aware / Nearby Connections); the iOS actual is a no-op Granted.
    data object NearbyWifiDevices : Runtime
    // Deprecated by Google in favor of granular android.permission.health.* permissions, but still
    // the only option for apps below that API surface; the iOS actual is a no-op Granted since
    // there is no equivalent to this specific Android permission (HealthKit is a different model).
    data object BodySensors : Runtime
    // iOS-only, but it belongs in Runtime rather than PlatformLimited: ATTrackingManager has a
    // real one-shot system prompt (requestTrackingAuthorizationWithCompletionHandler), it's just
    // that there's no Android equivalent, so the Android actual is a no-op Granted.
    data object AppTrackingTransparency : Runtime

    // Contacts already covers read access; write is a separate Android permission but the same
    // CNContactStore authorization on iOS, so the iOS actual just delegates to Contacts' request.
    data object WriteContacts : Runtime

    // Telephony/SMS/Call Log: Android-only concepts with no iOS API surface at all (Apple only
    // allows launching the system Messages/Phone apps via URL schemes, ungated by any permission),
    // so all six of these are no-op Granted on the iOS actual.
    data object CallPhone : Runtime
    data object ReadPhoneState : Runtime
    data object ReadPhoneNumbers : Runtime // API 26+
    data object AnswerPhoneCalls : Runtime // API 26+
    data object ReadCallLog : Runtime
    data object WriteCallLog : Runtime
    data object SendSms : Runtime
    data object ReadSms : Runtime
    data object ReceiveSms : Runtime

    // Paired with iOS Core Motion (CMMotionActivityManager) -- a genuine cross-platform permission.
    data object ActivityRecognition : Runtime // API 29+

    // Android's READ_MEDIA_AUDIO paired with iOS's Apple Music library access (MPMediaLibrary).
    data object AudioFiles : Runtime // API 33+, pre-33 falls back like PhotoLibrary

    // API 33+ two-step permission: BODY_SENSORS_BACKGROUND cannot be requested (or even granted)
    // until foreground BodySensors access is already granted, mirroring LocationAlways' staging.
    // Android-only; the iOS actual is a no-op Granted (Core Motion has no foreground/background
    // sensor-access split).
    data object BodySensorsBackground : Runtime

    // iOS-only (SFSpeechRecognizer); Android's on-device SpeechRecognizer needs only Microphone,
    // so the Android actual is a no-op Granted -- request Microphone separately there.
    data object SpeechRecognition : Runtime

    // iOS-only (EKEventStore reminders -- a distinct authorization from Calendar's); Android has
    // no separate reminders concept, so the Android actual is a no-op Granted.
    data object Reminders : Runtime

    data object SystemAlertWindow : Special
    data object ExactAlarm : Special
    data object IgnoreBatteryOptimizations : Special
    // Android-only Settings-redirect permissions; the iOS actual is a no-op Granted for all five.
    data object WriteSettings : Special
    data object ManageExternalStorage : Special
    data object DoNotDisturbAccess : Special
    data object UsageAccess : Special
    data object NotificationListenerAccess : Special

    // PlatformLimited is reserved for permissions with genuinely no request API on either
    // platform to call -- LocalNetwork has no requestAccess-style function on iOS at all (the
    // prompt fires implicitly the first time Network/Bonjour APIs are used), so state() can only
    // ever report a best-effort guess and request() has nothing to invoke.
    data object LocalNetwork : PlatformLimited
}

enum class CalendarAccess {
    Full,
    WriteOnly
}
