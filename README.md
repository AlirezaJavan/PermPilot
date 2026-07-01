# PermPilot

[![CI](https://github.com/AlirezaJavan/PermPilot/actions/workflows/ci.yml/badge.svg)](https://github.com/AlirezaJavan/PermPilot/actions/workflows/ci.yml)
[![Release](https://github.com/AlirezaJavan/PermPilot/actions/workflows/publish.yml/badge.svg)](https://github.com/AlirezaJavan/PermPilot/actions/workflows/publish.yml)
[![Maven Central (Core)](https://img.shields.io/maven-central/v/io.github.alirezajavan/permpilot-core?label=permpilot-core)](https://central.sonatype.com/artifact/io.github.alirezajavan/permpilot-core)
[![Maven Central (Compose)](https://img.shields.io/maven-central/v/io.github.alirezajavan/permpilot-compose?label=permpilot-compose)](https://central.sonatype.com/artifact/io.github.alirezajavan/permpilot-compose)
[![Maven Central (History)](https://img.shields.io/maven-central/v/io.github.alirezajavan/permpilot-history?label=permpilot-history)](https://central.sonatype.com/artifact/io.github.alirezajavan/permpilot-history)
[![minSdk](https://img.shields.io/badge/minSdk-24-brightgreen.svg)](https://android-arsenal.com/api?level=24)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A Kotlin Multiplatform (Android + iOS) library that gives Compose Multiplatform apps a single, ergonomic API for OS runtime permissions and Settings-gated special permissions. Call `request()` on a permission and get back an exhaustive `PermissionState` -- no SDK-version checks, no staged-request bookkeeping, no platform-specific failure modes leaking into your app code.

## Project Structure

This project is split into three publishable modules, plus a demo app:

- **`permpilot-core`**: The `expect`/`actual` `PermissionController` and the full `Permission`/`PermissionState` model. **Zero Compose dependency** -- usable from any Android/iOS Kotlin code, including ViewModels.
- **`permpilot-compose`**: The Compose Multiplatform UI layer -- `PermissionGate` and the default Material3 rationale/settings/restricted/configuration-error dialogs. Depends on `permpilot-core`.
- **`permpilot-history`** *(optional, independently versioned)*: `HistoryPermissionController` wraps any `PermissionController` and records every request/resolution/settings-open event into a SQLDelight-backed, queryable audit log. Depends on `permpilot-core`; zero Compose dependency.
- **`sample/`**: A QA harness exercising every catalog entry (27 `Runtime` + 8 `Special` permissions) against a real device -- OS permission dialogs can't be driven by instrumented tests, so this is for manual verification, not a product to polish.

## Features

- **One API, both platforms**: `Permission.Runtime.request()` returns a `PermissionState` -- `NotDetermined`, `Granted`, `Denied(canRequestAgain)`, `PermanentlyDenied`, `Restricted`, `Limited(reason)`, or `ConfigurationError(reason)` -- covering every outcome either OS can produce, including partial grants (approximate location, selected-photos-only).
- **Staged permissions handled for you**: background location and background body sensors on Android, and the when-in-use-then-always upgrade on iOS, are two-step system requirements PermPilot sequences internally -- you just call `request()` once.
- **`PermissionGate`**: a Compose wrapper that always renders your content first, then layers on the right prompt (rationale, "open Settings," restricted notice, or configuration-error notice) based on live state -- every dialog is overridable via composable-lambda parameters.
- **Automatic re-check on resume**: returning from a Settings redirect (Android or iOS) re-checks and republishes every observed permission's state via a shared lifecycle-`ON_RESUME` bridge -- no manual refresh needed.
- **`ConfigurationError` instead of a crash or a silent hang**: requesting before a host `Activity` is attached (Android), without the required `Info.plist` usage-description key (iOS), or with a missing `AndroidManifest` prerequisite -- an undeclared `<uses-permission>` (which the OS would otherwise silently auto-deny), no `ACCESS_NOTIFICATION_POLICY` entry for `DoNotDisturbAccess`, or no declared `NotificationListenerService` for `NotificationListenerAccess` -- reports through the same exhaustive `when` as every other state, instead of hanging forever, crashing, or masquerading as a user denial.
- **`FakePermissionController`**: an in-memory `PermissionController` for your own tests -- script a permission through `Denied → Granted` or any other flow without touching a real Activity or iOS runtime.

## Installation

```kotlin
// build.gradle.kts (commonMain or androidMain/iosMain)
dependencies {
    implementation("io.github.alirezajavan:permpilot-core:<version>")

    // Compose Multiplatform UI layer (PermissionGate, default dialogs)
    implementation("io.github.alirezajavan:permpilot-compose:<version>")

    // Optional: SQLDelight-backed audit log of every permission event
    implementation("io.github.alirezajavan:permpilot-history:<version>")
}
```

## Usage (Compose)

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

That's it for the common case: `PermissionGate` always renders your `content(state)` first, then decides whether to layer a rationale dialog, a "open Settings" prompt, a restricted notice, or a configuration-error notice on top -- all overridable:

```kotlin
PermissionGate(
    permission = Permission.LocationWhileInUse,
    rationale = { onRequest, onDismiss ->
        MyCustomRationaleDialog(onConfirm = onRequest, onDismiss = onDismiss)
    },
) { state ->
    when (state) {
        PermissionState.Granted -> ShowMap()
        is PermissionState.Limited -> ShowMap(approximate = true) // e.g. ApproximateLocationOnly
        else -> Text("Location needed")
    }
}
```

Customization works at two levels:

- **Text-level** -- keep the default Material3 dialogs but override any copy: every default dialog (`PermissionRationaleDialog`, `PermissionSettingsDialog`, `PermissionRestrictedNotice`, `PermissionConfigurationErrorNotice`) takes `title`/`text`/button-label parameters with sensible defaults, so localizing or rewording never means re-implementing the dialog:

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

- **Slot-level** -- replace any dialog wholesale: each of the gate's four prompts (`rationale`, `settingsPrompt`, `restrictedContent`, `configurationErrorContent`) is a composable lambda, so a bottom sheet, inline banner, or your own design system component drops in without touching the gate's state machine. `content(state)` is always rendered first and always receives the live state, so the surrounding UI is fully yours too.

`Limited` states (partial photo access, approximate-only location) are working grants, so the gate deliberately shows no prompt for them -- offer your own "upgrade" affordance that calls `controller.request(permission)` directly; the OS shows its own picker/upgrade dialog with no rationale needed.

### Driving a controller directly (outside Compose, e.g. a ViewModel)

```kotlin
val controller: PermissionController = rememberPermissionController()

scope.launch {
    when (val state = controller.request(Permission.Notifications)) {
        PermissionState.Granted -> scheduleReminder()
        is PermissionState.Denied -> showRationale()
        PermissionState.PermanentlyDenied -> controller.openAppSettings()
        else -> Unit
    }
}
```

### Special (Settings-only) permissions

`Permission.Special` permissions (e.g. `SystemAlertWindow`, `ExactAlarm`, `IgnoreBatteryOptimizations`) have no native request dialog -- only a live `state()` query and a direct Settings redirect:

```kotlin
val state by controller.state(Permission.IgnoreBatteryOptimizations).collectAsState()
Button(onClick = { controller.openAppSettings(Permission.IgnoreBatteryOptimizations) }) {
    Text("Open Settings")
}
```

Two of them have manifest prerequisites without which the app never appears in the target Settings list -- PermPilot reports `ConfigurationError(MissingManifestDeclaration)` instead of letting the redirect dead-end:

- `DoNotDisturbAccess` requires `<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />`.
- `NotificationListenerAccess` requires a declared `NotificationListenerService` (bound with `BIND_NOTIFICATION_LISTENER_SERVICE`).

The state a Settings redirect changed is picked up automatically when the user returns to the app: `rememberPermissionController()` re-checks every observed permission on every `ON_RESUME`.

## Core API

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

interface PermissionController {
    fun state(permission: Permission): StateFlow<PermissionState>
    suspend fun request(permission: Permission.Runtime): PermissionState
    suspend fun requestAll(vararg permissions: Permission.Runtime): Map<Permission, PermissionState>
    fun openAppSettings()
    fun openAppSettings(special: Permission.Special)
    fun refreshAll()
}
```

`request()`/`requestAll()` never throw -- every platform failure mode (missing Activity, missing Info.plist key, staged-request sequencing) is resolved internally and reported as `PermissionState` data.

## Optional: audit log (`permpilot-history`)

Wrap any `PermissionController` to record every request/resolution/settings-open event into a
queryable SQLDelight-backed log -- useful for support debugging or a privacy-dashboard screen:

```kotlin
val driver = PermissionHistoryDriverFactory(context).createDriver() // Android needs a Context; iOS needs none
val store = SqlDelightPermissionHistoryStore(driver)
val controller = HistoryPermissionController(rememberPermissionController(), store, rememberCoroutineScope())

// controller behaves exactly like the wrapped one -- use it as a drop-in replacement -- while
// store.events() / store.events(Permission.Camera) expose a Flow<List<PermissionHistoryEntry>>
// for building a history/audit screen.
```

`permpilot-history` is versioned independently of `permpilot-core`/`permpilot-compose` and ships on its own release cadence.

## Testing your own code

```kotlin
val controller = FakePermissionController()
controller.setState(Permission.Camera, PermissionState.Denied(canRequestAgain = true))

// drive your screen/ViewModel against `controller` exactly like a real one --
// no Activity, no iOS runtime, no OS permission dialog required.
```

See `permpilot-core`'s test sources for the full pattern this library uses on itself: pure resolve-state functions tested directly with `kotlin.test`, `AndroidPermissionController`'s real behavior tested against Robolectric (not mocks), and iOS's status-mapping functions compiled (though not executable without a Mac/simulator) via `iosTest`.

## Permission matrix

See [`docs/permission-matrix.md`](docs/permission-matrix.md) for a full table of every catalog entry, its Android manifest permission(s)/API-level gating, iOS `Info.plist` key(s), and platform-specific notes.

## Contributing

Enable the pre-commit hook once per clone so local mistakes get caught before CI does:

```
git config core.hooksPath .githooks
```

Run the full gate the same way CI does before opening a PR:

```
./gradlew build
```

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
