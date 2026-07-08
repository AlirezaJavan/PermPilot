# Permission matrix

Every entry in PermPilot's `Permission` catalog, what it maps to on each platform, and any behavior worth
knowing before you use it. This is a consumer-facing reference generated from the current implementation
(`Permission.kt`, `AndroidPermissionMapping.kt`, `IosInfoPlistRequirements.kt`) — for the *why* behind each
design decision, see `CLAUDE.md` (the "Hard rules learned the hard way" and per-platform notes).

Legend: **Manifest permission(s)** is what PermPilot adds to the runtime-permission request on Android (you
still need to declare these in your own `AndroidManifest.xml` — PermPilot cannot inject manifest entries for
you). **Info.plist key(s)** is what you must add to your own `Info.plist` on iOS — a missing key surfaces as
`PermissionState.ConfigurationError(MissingUsageDescription)` instead of a crash, but you still need to add it.

---

## `Permission.Runtime` (31)

| Permission | Android manifest permission(s) | iOS Info.plist key(s) | Notes |
|---|---|---|---|
| `Camera` | `CAMERA` | `NSCameraUsageDescription` | |
| `Microphone` | `RECORD_AUDIO` | `NSMicrophoneUsageDescription` | |
| `LocationWhileInUse` | `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` (always requested together) | `NSLocationWhenInUseUsageDescription` | Coarse-only grant → `Limited(ApproximateLocationOnly)`, not `Denied`. iOS reduced-accuracy grant → the same `Limited(ApproximateLocationOnly)`. |
| `LocationAlways` | `ACCESS_BACKGROUND_LOCATION` (API 29+; no separate permission below that — implied once foreground is granted) | `NSLocationWhenInUseUsageDescription` + `NSLocationAlwaysAndWhenInUseUsageDescription` | Staged: foreground (`LocationWhileInUse`) is requested and confirmed granted first, then a second, separate request for background. Never bundle into `requestAll`. |
| `Notifications` | `POST_NOTIFICATIONS` (API 33+; empty list below that → auto-`Granted`) | *(none)* | iOS has no synchronous status getter — `state()` returns a `NotDetermined` placeholder immediately and the real value arrives asynchronously. |
| `Contacts` | `READ_CONTACTS` | `NSContactsUsageDescription` | |
| `WriteContacts` | `WRITE_CONTACTS` | `NSContactsUsageDescription` (same key as `Contacts`) | iOS delegates to the same `CNContactStore` authorization as `Contacts` — no read/write distinction on that platform. |
| `Calendar(access: CalendarAccess)` | `READ_CALENDAR` + `WRITE_CALENDAR` (always both — Android has no write-only tier, `CalendarAccess.WriteOnly` is ignored) | `NSCalendarsUsageDescription` | iOS 17+ splits `Full`/`WriteOnly` into distinct native calls; below 17, both fall back to the single legacy full-access API. |
| `PhotoLibrary` | API 34+: `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` + `READ_MEDIA_VISUAL_USER_SELECTED`; API 33: images+video only; pre-33: `READ_EXTERNAL_STORAGE` (`maxSdkVersion=32`) | `NSPhotoLibraryUsageDescription` | Partial/selected-photos grant → `Limited(PartialMediaAccess)` on both platforms (API 34+ on Android, iOS 14+'s `.limited`). |
| `MediaLocation` | `ACCESS_MEDIA_LOCATION` (API 29+) | *(none)* | Lets an app read GPS EXIF data from photos. Android-only; iOS no-op `Granted` (location is included with Photos access). Only meaningful once photo access is granted. |
| `BluetoothScan` | `BLUETOOTH_SCAN` (API 31+); falls back to `ACCESS_FINE_LOCATION` on API 24–30 | `NSBluetoothAlwaysUsageDescription` | iOS: no explicit request call — instantiating a `CBCentralManager` for the first time triggers the system prompt. |
| `BluetoothConnect` | `BLUETOOTH_CONNECT` (API 31+); empty list pre-31 → auto-`Granted` | `NSBluetoothAlwaysUsageDescription` | iOS: same `CBCentralManager` authorization as `BluetoothScan`. |
| `BluetoothAdvertise` | `BLUETOOTH_ADVERTISE` (API 31+); empty list pre-31 → auto-`Granted` | `NSBluetoothAlwaysUsageDescription` | iOS: same `CBCentralManager` authorization as `BluetoothScan`. |
| `NearbyWifiDevices` | `NEARBY_WIFI_DEVICES` (API 33+); falls back to `ACCESS_FINE_LOCATION` below that | *(none)* | Android-only concept; iOS actual is a no-op `Granted`. |
| `BodySensors` | `BODY_SENSORS` (unconditional, stable since API 23) | *(none)* | Android-only, deprecated by Google in favor of granular `android.permission.health.*`; iOS actual is a no-op `Granted`. |
| `BodySensorsBackground` | `BODY_SENSORS_BACKGROUND` (API 33+; implied granted below that once foreground is granted) | *(none)* | Staged the same way as `LocationAlways`: foreground `BodySensors` must already be granted. Android-only; iOS actual is a no-op `Granted`. |
| `ActivityRecognition` | `ACTIVITY_RECOGNITION` (API 29+); falls back to `BODY_SENSORS` below that | `NSMotionUsageDescription` | Paired with iOS Core Motion (`CMMotionActivityManager`) — a genuine cross-platform permission. |
| `AppTrackingTransparency` | *(none — no-op `Granted`)* | `NSUserTrackingUsageDescription` | iOS-only, but has a **real** one-shot native prompt (`ATTrackingManager`); gated to iOS 14+, no-op `Granted` below that. Deliberately `Runtime`, not `PlatformLimited` — see CLAUDE.md ("What NOT to reintroduce"). |
| `SpeechRecognition` | *(none — no-op `Granted`)* | `NSSpeechRecognitionUsageDescription` | iOS-only (`SFSpeechRecognizer`); Android's on-device `SpeechRecognizer` only needs `Microphone` — request that separately there. |
| `Reminders` | *(none — no-op `Granted`)* | `NSRemindersUsageDescription` | iOS-only (`EKEventStore` reminders — a distinct authorization from `Calendar`'s, despite sharing the same framework/enum). |
| `CallPhone` | `CALL_PHONE` | *(none — no-op `Granted`)* | Android-only; no iOS API surface exists at all for telephony/SMS/call-log (Apple only allows launching the system Phone/Messages apps via URL schemes, ungated by any permission). |
| `ReadPhoneState` | `READ_PHONE_STATE` | *(none — no-op `Granted`)* | Android-only, same reasoning as `CallPhone`. |
| `ReadPhoneNumbers` | `READ_PHONE_NUMBERS` (API 26+) | *(none — no-op `Granted`)* | Android-only. |
| `AnswerPhoneCalls` | `ANSWER_PHONE_CALLS` (API 26+) | *(none — no-op `Granted`)* | Android-only. |
| `ReadCallLog` | `READ_CALL_LOG` | *(none — no-op `Granted`)* | Android-only. |
| `WriteCallLog` | `WRITE_CALL_LOG` | *(none — no-op `Granted`)* | Android-only. |
| `SendSms` | `SEND_SMS` | *(none — no-op `Granted`)* | Android-only. |
| `ReadSms` | `READ_SMS` | *(none — no-op `Granted`)* | Android-only. |
| `ReceiveSms` | `RECEIVE_SMS` | *(none — no-op `Granted`)* | Android-only. |
| `AudioFiles` | `READ_MEDIA_AUDIO` (API 33+); falls back to `READ_EXTERNAL_STORAGE` below that | `NSAppleMusicUsageDescription` | Android `READ_MEDIA_AUDIO` paired with iOS's Apple Music library access (`MPMediaLibrary`). |
| `Health(dataTypes, access)` | Health Connect permissions (e.g. `READ_STEPS`); requires Health Connect app/SDK | `NSHealthShareUsageDescription` + `NSHealthUpdateUsageDescription` | Granular per data type. iOS read-only access may report `NotDetermined` even after a grant for privacy. Android reports `ConfigurationError` if Health Connect is unavailable. |

---

## `Permission.Special` (9) — Android Settings-redirect only, no request dialog

No manifest permission is *requested* for these (some still need a manifest **declaration** — see notes); `request()` doesn't apply to `Special` permissions at all, only `state()` (a live check) and `openAppSettings(special)` (a direct Settings redirect). All nine are no-op `Granted` on iOS, which has no equivalent concept — `openAppSettings(special)` there just calls the one generic `openAppSettings()` since iOS has no per-permission deep link.

| Permission | Android live check | Android Settings intent | Notes |
|---|---|---|---|
| `SystemAlertWindow` | `Settings.canDrawOverlays(context)` | `ACTION_MANAGE_OVERLAY_PERMISSION` + `package:` data URI | |
| `ExactAlarm` | `AlarmManager.canScheduleExactAlarms()` (API 31+; `Granted` below that) | `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` (API 31+) / `ACTION_APPLICATION_DETAILS_SETTINGS` (below) + `package:` data URI | |
| `FullScreenIntent` | `NotificationManager.canUseFullScreenIntent()` (API 34+; `Granted` below that) | `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` (API 34+) / `ACTION_APPLICATION_DETAILS_SETTINGS` (below) + `package:` data URI | |
| `IgnoreBatteryOptimizations` | `PowerManager.isIgnoringBatteryOptimizations(packageName)` | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + `package:` data URI | |
| `WriteSettings` | `Settings.System.canWrite(context)` | `ACTION_MANAGE_WRITE_SETTINGS` + `package:` data URI | |
| `ManageExternalStorage` | `Environment.isExternalStorageManager()` (API 30+; `Granted` below that) | `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` (API 30+) / `ACTION_APPLICATION_DETAILS_SETTINGS` (below) + `package:` data URI | |
| `DoNotDisturbAccess` | `NotificationManager.isNotificationPolicyAccessGranted` | `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` (no `package:` URI — opens a generic list of every app requesting this access) | |
| `UsageAccess` | `AppOpsManager` `OPSTR_GET_USAGE_STATS` via `checkOpNoThrow` | `ACTION_USAGE_ACCESS_SETTINGS` (no `package:` URI) | `<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions" />` is purely documentary — it's an AppOps-gated permission, not a runtime one, so declaring it isn't required for the check to work but is common practice for clarity. |
| `NotificationListenerAccess` | `NotificationManagerCompat.getEnabledListenerPackages(context)` contains this app's package | `ACTION_NOTIFICATION_LISTENER_SETTINGS` (no `package:` URI) | |

---

## `Permission.PlatformLimited` (1) — no request API on either platform

| Permission | Behavior |
|---|---|
| `LocalNetwork` | `state()` always reports `Granted` on both platforms — this is a documented limitation, not a bug. No `requestAccess`-style API exists on iOS at all (the system prompt fires implicitly the first time Network/Bonjour APIs are actually used); Android has no equivalent concept at all. |

---

## `PermissionState` reference

```kotlin
sealed interface PermissionState {
    data object NotDetermined : PermissionState
    data object Granted : PermissionState
    data class Denied(val canRequestAgain: Boolean) : PermissionState
    data object PermanentlyDenied : PermissionState
    data object Restricted : PermissionState
    data class Limited(val reason: LimitedReason) : PermissionState
    data class ConfigurationError(val reason: ConfigurationErrorReason) : PermissionState
}

enum class LimitedReason { PartialMediaAccess, SelectedContactsOnly, ApproximateLocationOnly }

enum class ConfigurationErrorReason { NoHostActivity, MissingUsageDescription, MissingManifestDeclaration }
```

- **`Restricted`** (MDM/parental controls) is terminal on both platforms — nothing in Settings can fix it, so `PermissionGate` never offers a settings prompt for it.
- **`ConfigurationError`** is not a user decision — it's a library-integration mistake (`NoHostActivity`: `request()` called before `rememberPermissionController()` was ever composed into an Activity-hosted screen; `MissingUsageDescription`: the required `Info.plist` key(s) above are missing; `MissingManifestDeclaration`: the required Android manifest `<uses-permission>` or service declaration is missing). Fix your integration, not your permission-handling logic.
- **`SelectedContactsOnly`** exists in the model for parity with the other partial-grant tiers — iOS 18 added a limited-contacts grant (`CNAuthorizationStatusLimited`) which PermPilot reports here. Android has no equivalent today.

---

*Generated from the implementation as of the `permpilot-core`/`permpilot-compose` publishing pipeline going live. If you add a new `Permission` catalog entry, update this file in the same change — see CLAUDE.md's "New permission checklist."*
