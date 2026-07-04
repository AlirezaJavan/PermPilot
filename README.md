# PermPilot

[![CI](https://github.com/AlirezaJavan/PermPilot/actions/workflows/ci.yml/badge.svg)](https://github.com/AlirezaJavan/PermPilot/actions/workflows/ci.yml)
[![Release](https://github.com/AlirezaJavan/PermPilot/actions/workflows/publish.yml/badge.svg)](https://github.com/AlirezaJavan/PermPilot/actions/workflows/publish.yml)
[![Maven Central (Core)](https://img.shields.io/maven-central/v/io.github.alirezajavan/permpilot-core?label=permpilot-core)](https://central.sonatype.com/artifact/io.github.alirezajavan/permpilot-core)
[![Maven Central (Compose)](https://img.shields.io/maven-central/v/io.github.alirezajavan/permpilot-compose?label=permpilot-compose)](https://central.sonatype.com/artifact/io.github.alirezajavan/permpilot-compose)
[![Maven Central (History)](https://img.shields.io/maven-central/v/io.github.alirezajavan/permpilot-history?label=permpilot-history)](https://central.sonatype.com/artifact/io.github.alirezajavan/permpilot-history)
[![minSdk](https://img.shields.io/badge/minSdk-24-brightgreen.svg)](https://android-arsenal.com/api?level=24)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**PermPilot** is a Kotlin Multiplatform (Android + iOS) permissions library for Compose Multiplatform apps. It collapses both platforms' permission systems -- runtime dialogs, Settings-gated special access, staged two-step requests, partial grants, MDM restrictions, and every SDK-version quirk in between -- into one ergonomic, exhaustively-typed API:

```kotlin
PermissionGate(permission = Permission.Camera) { state ->
    when (state) {
        PermissionState.Granted -> CameraPreview()
        else -> Text("Camera access needed to continue")
    }
}
```

You call `request()`; PermPilot decides whether that means one system dialog, two sequenced ones, a Settings redirect, or nothing at all -- and always answers with a `PermissionState` you can `when` over exhaustively.

## Why PermPilot

Permission handling is where cross-platform apps quietly accumulate platform-specific bugs:

- Android's `shouldShowRequestPermissionRationale()` returns `false` both *before the first request ever* and *after a permanent denial* -- conflating them misreports first-time users as permanently denied (a bug Accompanist famously shipped). PermPilot cross-checks a persisted has-requested flag to tell them apart.
- Background location and background body sensors **cannot** be bundled with other permissions: the OS requires their foreground counterpart granted first, then a *separate* second request. iOS's `requestAlwaysAuthorization()` is likewise a silent no-op unless when-in-use is already granted. PermPilot sequences both internally.
- Grouped requests can *partially* succeed: the user can grant approximate-only location or selected-photos-only access. Treating "not everything granted" as a denial permanently misreports a working grant -- PermPilot models these as `Limited(reason)`.
- Requesting a permission missing from `AndroidManifest.xml` doesn't fail loudly -- Android silently auto-denies it, which looks exactly like the user said no. PermPilot detects it and reports `ConfigurationError` instead.
- On iOS, calling an authorization API without its `Info.plist` usage-description key terminates the process. PermPilot checks first and reports `ConfigurationError` instead of ever making that call.

None of this leaks into your app code. There are no exceptions to catch, no `bind(lifecycle)` ceremony, and no SDK-version checks on the consumer side.

## Features

- **One API, both platforms** -- `request()` returns a sealed `PermissionState` covering every outcome either OS can produce: `NotDetermined`, `Granted`, `Denied(canRequestAgain)`, `PermanentlyDenied`, `Restricted`, `Limited(reason)`, `ConfigurationError(reason)`.
- **35-entry permission catalog** -- 27 `Runtime` permissions (camera, microphone, location tiers, photos/media, Bluetooth, telephony/SMS/call log, sensors, activity recognition, ATT, speech, reminders, ...) and 8 Android `Special` permissions (overlay, exact alarms, battery optimizations, write settings, all-files access, DND access, usage access, notification-listener access). See the [permission matrix](docs/permission-matrix.md).
- **Staged requests sequenced for you** -- background location / background body sensors on Android, when-in-use → always upgrade on iOS: one `request()` call, correct two-step choreography inside.
- **Partial grants modeled, not mangled** -- approximate-only location and selected-photos access surface as `Limited(ApproximateLocationOnly)` / `Limited(PartialMediaAccess)`, matching iOS's independent accuracy/selection axes.
- **`PermissionGate`** -- a Compose wrapper that always renders your content first, then layers the right prompt (rationale, "open Settings", restricted notice, configuration-error notice) on top of live state. Every prompt is replaceable; every default dialog's copy is parameterized.
- **Reactive state** -- `state(permission)` is a `StateFlow<PermissionState>`; grants made in the OS Settings app are picked up automatically on `ON_RESUME` and republished to every observer.
- **Fail-fast configuration errors** -- missing host `Activity`, missing `Info.plist` key, an undeclared `<uses-permission>` for a runtime permission, or a missing Special-permission prerequisite (`SYSTEM_ALERT_WINDOW`, `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `WRITE_SETTINGS`, `MANAGE_EXTERNAL_STORAGE`, `PACKAGE_USAGE_STATS`, `ACCESS_NOTIFICATION_POLICY`, or a declared `NotificationListenerService`) all report as `ConfigurationError` through the same exhaustive `when` -- never a crash, hang, or fake denial. Legitimate partial declarations (coarse-only location, photos without the API-34 partial-selection tier) are recognized and not flagged.
- **Crash-safe result delivery** -- request outcomes are published to the `StateFlow` from the OS callback itself, so even if the coroutine that launched the request is cancelled mid-dialog (e.g. its composable left composition), the UI still receives the real result.
- **`FakePermissionController`** -- an in-memory controller for your own tests: script `Denied → Granted` or any other flow with no Activity, no simulator, no OS dialog.
- **Optional audit log** -- `permpilot-history` decorates any controller and records every request/resolution/settings-open event into a queryable SQLDelight store.

## Installation

All artifacts are on Maven Central under `io.github.alirezajavan`:

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform UI layer (PermissionGate + default dialogs).
            // Brings permpilot-core in transitively via api().
            implementation("io.github.alirezajavan:permpilot-compose:0.1.0")

            // Or, without Compose (e.g. shared ViewModels only):
            // implementation("io.github.alirezajavan:permpilot-core:0.1.0")

            // Optional: SQLDelight-backed audit log (independently versioned)
            // implementation("io.github.alirezajavan:permpilot-history:0.1.0")
        }
    }
}
```

Then declare what your app actually uses:

- **Android**: the relevant `<uses-permission>` entries in `AndroidManifest.xml` (see the [permission matrix](docs/permission-matrix.md) for the exact, API-level-gated set per catalog entry).
- **iOS**: the relevant `NS*UsageDescription` keys in `Info.plist` (see the [Info.plist checklist](docs/ios-info-plist-checklist.md)).

PermPilot verifies both at runtime and reports `ConfigurationError` for anything missing, so integration mistakes surface on the first request instead of in production as phantom denials.

## Usage

### The 90% case: `PermissionGate`

```kotlin
@Composable
fun CameraScreen() {
    PermissionGate(permission = Permission.Camera) { state ->
        when (state) {
            PermissionState.Granted -> CameraPreview()
            else -> Text("Camera access needed to continue")
        }
    }
}
```

`PermissionGate` always calls your `content(state)` first -- your UI is the source of truth -- then decides which prompt, if any, to layer on top:

| Live state | Gate shows |
|---|---|
| `NotDetermined` | rationale dialog (priming before the one-shot native prompt) |
| `Denied(canRequestAgain = true)` | rationale dialog again |
| `Denied(canRequestAgain = false)` / `PermanentlyDenied` | "open Settings" dialog |
| `Granted` / `Limited` | nothing -- these are working grants |
| `Restricted` | terminal notice (MDM/parental controls; Settings can't fix it) |
| `ConfigurationError` | developer-facing error notice |

A dismissed prompt stays dismissed *for that exact state only* -- the moment the underlying state changes (a request resolves, the user returns from Settings), the gate re-evaluates from scratch. It never goes silent forever, and it never nags on an unchanged state.

### Customizing the UI

Two levels, use whichever fits:

**Text-level** -- keep the default Material3 dialogs, override any copy. Every default dialog (`PermissionRationaleDialog`, `PermissionSettingsDialog`, `PermissionRestrictedNotice`, `PermissionConfigurationErrorNotice`) takes `title` / `text` / button-label parameters:

```kotlin
PermissionGate(
    permission = Permission.Camera,
    rationale = { onRequest, onDismiss ->
        PermissionRationaleDialog(
            permission = Permission.Camera,
            onConfirm = onRequest,
            onDismiss = onDismiss,
            title = "Camera needed",
            text = "Scanning a document requires the camera.",
            confirmLabel = "Continue",
            dismissLabel = "Not now",
        )
    },
) { state -> /* ... */ }
```

**Slot-level** -- replace any prompt wholesale. Each of the gate's four prompts (`rationale`, `settingsPrompt`, `restrictedContent`, `configurationErrorContent`) is a plain composable lambda, so a bottom sheet, inline banner, or your own design-system component drops in without touching the gate's state machine:

```kotlin
PermissionGate(
    permission = Permission.LocationWhileInUse,
    rationale = { onRequest, onDismiss ->
        MyBottomSheet(onConfirm = onRequest, onDismiss = onDismiss)
    },
    settingsPrompt = { onOpenSettings, onDismiss ->
        MyBanner(onClick = onOpenSettings, onClose = onDismiss)
    },
) { state ->
    when (state) {
        PermissionState.Granted -> ShowMap()
        is PermissionState.Limited -> ShowMap(approximate = true)
        else -> Text("Location needed")
    }
}
```

**Upgrading a `Limited` grant**: `Limited` (partial photos, approximate location) is a *working* grant, so the gate deliberately shows no prompt for it. Offer your own upgrade affordance that calls `controller.request(permission)` directly -- the OS shows its own picker/upgrade dialog, no rationale needed:

```kotlin
if (state is PermissionState.Limited) {
    Button(onClick = { scope.launch { controller.request(Permission.PhotoLibrary) } }) {
        Text("Allow access to all photos")
    }
}
```

### Driving the controller directly (ViewModels, non-Compose logic)

`PermissionController` lives in `permpilot-core`, which has **zero** Compose dependency:

```kotlin
val controller: PermissionController = rememberPermissionController() // hand it to your ViewModel

scope.launch {
    when (val state = controller.request(Permission.Notifications)) {
        PermissionState.Granted -> scheduleReminder()
        is PermissionState.Denied -> showRationale()
        PermissionState.PermanentlyDenied -> controller.openAppSettings()
        else -> Unit
    }
}
```

`request()` / `requestAll()` **never throw** -- every platform failure mode (missing Activity, missing manifest declaration, missing Info.plist key, staged-request sequencing) is resolved internally and reported as data. `requestAll()` batches what the OS allows into one dialog and automatically pulls staged permissions out into their own sequenced flows.

### Special (Settings-only) permissions

Android's `Permission.Special` entries (`SystemAlertWindow`, `ExactAlarm`, `IgnoreBatteryOptimizations`, `WriteSettings`, `ManageExternalStorage`, `DoNotDisturbAccess`, `UsageAccess`, `NotificationListenerAccess`) have no request dialog -- only a live `state()` query and a per-permission deep-linked Settings redirect:

```kotlin
val state by controller.state(Permission.IgnoreBatteryOptimizations).collectAsState()
Button(onClick = { controller.openAppSettings(Permission.IgnoreBatteryOptimizations) }) {
    Text("Open Settings")
}
```

Each redirect carries the correct `package:` data URI where the platform supports one, so the user lands on *your app's* toggle, not a generic list. Two entries have manifest prerequisites without which the app never appears in the target Settings list -- PermPilot reports `ConfigurationError(MissingManifestDeclaration)` instead of letting the redirect dead-end:

- `DoNotDisturbAccess` requires `<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />`.
- `NotificationListenerAccess` requires a declared `NotificationListenerService` (bound with `BIND_NOTIFICATION_LISTENER_SERVICE`).

Changes made in Settings are picked up automatically: `rememberPermissionController()` re-checks every observed permission on every `ON_RESUME`.

### Audit log (`permpilot-history`)

Wrap any `PermissionController` to record every request / resolution / settings-open event into a queryable SQLDelight-backed log -- useful for support debugging or a privacy-dashboard screen:

```kotlin
val driver = PermissionHistoryDriverFactory(context).createDriver() // Android needs a Context; iOS needs none
val store = SqlDelightPermissionHistoryStore(driver)
val controller = HistoryPermissionController(rememberPermissionController(), store, scope)

// Drop-in replacement for the wrapped controller. Meanwhile:
store.events()                    // Flow<List<PermissionHistoryEntry>> -- everything
store.events(Permission.Camera)   // ...or filtered per permission
```

Recording is cancellation-safe: once a request resolves, its `Resolved` event is written even if the caller was cancelled at that exact moment -- the log never ends up with a dangling `Requested`. `permpilot-history` is versioned independently and ships on its own release cadence.

### Testing your own code

```kotlin
val controller = FakePermissionController()
controller.setState(Permission.Camera, PermissionState.Denied(canRequestAgain = true))

// Drive your screen/ViewModel against `controller` exactly like a real one --
// no Activity, no iOS runtime, no OS permission dialog required.
```

## Core API

```kotlin
sealed interface Permission {
    sealed interface Runtime : Permission          // has a real request on ≥1 platform
    sealed interface Special : Permission          // Android Settings redirect, no dialog
    sealed interface PlatformLimited : Permission  // no request API on either platform

    data object Camera : Runtime
    data object LocationWhileInUse : Runtime
    data object LocationAlways : Runtime           // staged: foreground first, then background
    data object PhotoLibrary : Runtime             // supports Limited(PartialMediaAccess)
    /* ...27 Runtime + 8 Special + LocalNetwork -- full list in docs/permission-matrix.md */
}

sealed interface PermissionState {
    data object NotDetermined : PermissionState
    data object Granted : PermissionState
    data class Denied(val canRequestAgain: Boolean) : PermissionState
    data object PermanentlyDenied : PermissionState
    data object Restricted : PermissionState
    data class Limited(val reason: LimitedReason) : PermissionState
    data class ConfigurationError(val reason: ConfigurationErrorReason) : PermissionState
}

interface PermissionController {
    fun state(permission: Permission): StateFlow<PermissionState>
    suspend fun request(permission: Permission.Runtime): PermissionState
    suspend fun requestAll(vararg permissions: Permission.Runtime): Map<Permission, PermissionState>
    fun openAppSettings()
    fun openAppSettings(special: Permission.Special)
    fun refreshAll()
}
```

## Architecture

### Modules

```
permpilot-core        Permission catalog + PermissionState model + expect/actual PermissionController.
                      Pure Kotlin in commonMain; zero Compose dependency. Usable from ViewModels.
        ▲
        │ api()
permpilot-compose     Compose Multiplatform layer: PermissionGate, default Material3 dialogs,
                      rememberPermissionController(), lifecycle re-check on resume.
        │
permpilot-history     Optional decorator: HistoryPermissionController + SQLDelight event store.
                      Depends only on permpilot-core; independently versioned.

sample/               QA harness exercising all 35 catalog entries against a real device
                      (OS permission dialogs can't be driven by instrumented tests).
```

### Design principles

- **One narrow platform boundary.** `PermissionController` is the *only* `expect`/`actual` surface. Everything else in `commonMain` -- the catalog, the state model, the gate's decision logic -- is platform-agnostic pure Kotlin.
- **Orchestration / mapping split.** Inside each platform `actual`, coroutine bridging, staged sequencing, and persistence live in one file (`*PermissionController.kt`), while pure `Permission ↔ platform type` translation lives in another (`AndroidPermissionMapping.kt` / `IosPermissionStatusMapping.kt`). Adding a permission touches the mapping, not the orchestration -- and the mapping functions are directly unit-testable with no device.
- **States, not exceptions.** Every outcome -- including integration mistakes -- flows through the same sealed `PermissionState`, so a single exhaustive `when` in consumer code covers everything and the compiler flags anything new.
- **UI decisions are pure functions.** `PermissionGate` delegates *which prompt to show* to a pure resolver (`state × dismissed-for → prompt`), unit-tested across the full matrix without a Compose runtime. The gate itself only wires slots.
- **Results outlive their callers.** Grant/deny outcomes are published to the `StateFlow` from inside the OS callback, and requests are serialized through a `Mutex` -- a cancelled caller or two overlapping requests can't strand the observable state or hang a coroutine.
- **Trust nothing implicit about Android.** `Denied` vs `PermanentlyDenied` is resolved by cross-checking `shouldShowRequestPermissionRationale` against a persisted has-requested flag; grouped requests check for legitimate partial-grant tiers before falling through to the all-or-nothing resolver; manifest declarations are verified before the OS gets a chance to silently auto-deny.

### How a request flows (Android)

```
PermissionGate ──▶ controller.request(permission)
                        │  Mutex: one system dialog at a time
                        │  1. already granted?          ──▶ Granted (no launcher round-trip)
                        │  2. declared in manifest?     ──▶ ConfigurationError if not
                        │  3. host Activity attached?   ──▶ ConfigurationError if not
                        │  4. staged permission?        ──▶ foreground request first, then a
                        │                                   separate background request
                        ▼
              MultiRequest ──▶ SharedFlow ──▶ ActivityResultContracts.RequestMultiplePermissions
                                                     │
                        state published from the launcher callback (survives caller cancellation),
                        resolved through pure functions: partial-grant tiers ▶ rationale flag ▶
                        persisted has-requested flag ▶ Denied / PermanentlyDenied / Limited / Granted
```

On iOS the same shape maps to per-framework authorization calls (`AVCaptureDevice`, `CLLocationManager`, `PHPhotoLibrary`, `ATTrackingManager`, ...), with every delegate/completion callback marshalled onto the main queue and `Info.plist` keys verified before any native call.

### Technology stack

| Concern | Technology |
|---|---|
| Language / targets | Kotlin Multiplatform 2.4 -- `android`, `iosArm64`, `iosSimulatorArm64` |
| UI layer | Compose Multiplatform 1.11 (Material3) |
| Concurrency & reactivity | kotlinx-coroutines -- `suspend` requests, `StateFlow` observation |
| Lifecycle | JetBrains AndroidX Lifecycle (KMP) -- shared `ON_RESUME` re-check on both platforms |
| Persistence (core) | `multiplatform-settings` -- the persisted has-requested flag behind Denied-vs-PermanentlyDenied |
| Persistence (history) | SQLDelight 2 -- typed, queryable event log with platform drivers |
| Android build | AGP `com.android.kotlin.multiplatform.library`, convention plugins in `build-logic/` |
| API stability | kotlinx binary-compatibility-validator -- klib ABI baselines checked on every build |
| Publishing | vanniktech maven-publish -- Maven Central, Dokka javadoc, GPG-signed |

### Testing strategy

Three test source sets in `permpilot-core`, each targeting what's actually testable without a device:

- **`commonTest`** -- pure model logic and `FakePermissionController`, `kotlin.test`.
- **`androidHostTest`** -- JVM-only, no emulator: pure resolve-state functions tested directly, plus the real `AndroidPermissionController` driven through **Robolectric shadows, not mocks** -- including regression tests for caller-cancellation mid-dialog, check-before-request, partial grants, and manifest-declaration detection.
- **`iosTest`** -- pure iOS status-mapping functions; compiles for both iOS targets on any host (execution needs a Mac/simulator).

`permpilot-compose` unit-tests the gate's full prompt matrix as a pure function; the sample app carries an end-to-end Robolectric test proving Settings-made grants are picked up on resume through real composition and a real Activity lifecycle.

## Documentation

- [Permission matrix](docs/permission-matrix.md) -- every catalog entry, its Android manifest permission(s) and API-level gating, iOS `Info.plist` key(s), and platform notes.
- [iOS Info.plist checklist](docs/ios-info-plist-checklist.md) -- the usage-description keys each permission requires.

## Contributing

Enable the pre-commit hook once per clone so local mistakes get caught before CI does:

```
git config core.hooksPath .githooks
```

Run the full gate the same way CI does before opening a PR:

```
./gradlew build
```

This runs all tests on every module, compiles both iOS targets, runs Android Lint, and verifies the public API against the committed binary-compatibility baselines (`<module>/api/*.klib.api` -- regenerate with `./gradlew apiDump` when you intentionally change the API surface).

## License

```
Copyright 2026 Alireza Javan

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
