# iOS Info.plist usage-description checklist

Every `Permission.Runtime` that has a real iOS native prompt requires its `NS*UsageDescription`
key(s) present in the consuming app's `Info.plist` *before* calling `request()`/`state()` for it --
a missing key surfaces as `PermissionState.ConfigurationError(MissingUsageDescription)` at runtime
(see `PermissionController.request()`'s doc and `IosInfoPlistRequirements.kt`) rather than the
process crashing outright, but the key still has to actually be present to use the permission for
real. This is the definitive list, generated from `IosInfoPlistRequirements.kt`'s
`requiredInfoPlistKeys()` (the source of truth) -- if you add a new `Permission` catalog entry with
a real iOS prompt, update that function *and* this table in the same change (see CLAUDE.md's "New
permission checklist").

| `Permission` | Required `Info.plist` key(s) | Notes |
|---|---|---|
| `Camera` | `NSCameraUsageDescription` | |
| `Microphone` | `NSMicrophoneUsageDescription` | |
| `Contacts` | `NSContactsUsageDescription` | |
| `WriteContacts` | `NSContactsUsageDescription` | Same key as `Contacts` -- iOS doesn't distinguish read/write. |
| `Calendar(access)` | `NSCalendarsUsageDescription` | Same key regardless of `CalendarAccess.Full`/`WriteOnly`. |
| `PhotoLibrary` | `NSPhotoLibraryUsageDescription` | |
| `LocationWhileInUse` | `NSLocationWhenInUseUsageDescription` | |
| `LocationAlways` | `NSLocationWhenInUseUsageDescription` + `NSLocationAlwaysAndWhenInUseUsageDescription` | Both keys required -- the foreground step is always requested first regardless of which one the caller asked for. |
| `BluetoothScan` | `NSBluetoothAlwaysUsageDescription` | |
| `ActivityRecognition` | `NSMotionUsageDescription` | |
| `AppTrackingTransparency` | `NSUserTrackingUsageDescription` | |
| `SpeechRecognition` | `NSSpeechRecognitionUsageDescription` | |
| `Reminders` | `NSRemindersUsageDescription` | |
| `AudioFiles` | `NSAppleMusicUsageDescription` | |
| `Notifications` | *(none)* | `UNUserNotificationCenter` needs no `Info.plist` key at all. |
| Everything else (`NearbyWifiDevices`, `BodySensors`, `BodySensorsBackground`, telephony/SMS/call-log, all `Special`/`PlatformLimited`) | *(none)* | No iOS native prompt exists for these -- the iOS actual never touches a native API that could crash, so there's nothing to declare. |

`sample/iosApp/iosApp/Info.plist` already declares every key above with a short demo-appropriate
description, since the sample app's `demoPermissions` list (`sample/composeApp/src/commonMain/.../App.kt`)
exercises every `Permission.Runtime` that needs one. A real consumer app only needs to declare keys
for the permissions it actually uses.
