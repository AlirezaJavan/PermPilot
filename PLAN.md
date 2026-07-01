# PermPilot — CMP Permission & System Settings Orchestrator

**Status:** Core library implemented and verified (Android + iOS actuals, Compose UI layer, full catalog — 27 `Runtime`, 8 `Special`, 1 `PlatformLimited`). Sample app wired, including `permpilot-history`. Unit tests (commonTest/androidHostTest/iosTest), the Maven Central publishing pipeline (CI + release workflows), binary-compatibility-validator, `docs/permission-matrix.md`, and the optional `permpilot-history` (SQLDelight audit log) module are all in place. The previously-known concurrent-`request()` hang (§9.1) is fixed on both platforms via a `Mutex`. `sample/iosApp` has every file that's safe to hand-author without Xcode (Compose entry point, `Info.plist` with all usage-description keys, Swift wrapper, setup doc) — the actual `.xcodeproj` still needs to be created and verified on a Mac following `sample/iosApp/README.md` (see §9.2), since hand-writing `project.pbxproj` blind risks an unopenable project.
**Target platforms:** Android (API 24+) · iOS (13+, primary support 15+)
**Stack:** Kotlin Multiplatform, Compose Multiplatform, Kotlin coroutines/Flow

---

## Status checklist

- [x] `build-logic` KMP convention plugins + version catalog, `permpilot-core`/`permpilot-compose` skeletons building on Android + iOS
- [x] `PermissionState` (6-case sealed model) and `Permission` catalog (full Runtime + Special + PlatformLimited set, §3)
- [x] `PermissionController` interface (state/request/requestAll/openAppSettings×2)
- [x] Android actual: `ActivityResultContracts` bridge, `multiplatform-settings` `hasRequested` flag store, Activity-aware rationale detection
- [x] Android: background-location two-step staging, photo-library API 33/34 tiers with `Limited`, Bluetooth/Nearby-Wi-Fi pre/post version fallback, Calendar full/write-only handling, all `Special` permission Settings-intents + real state checks
- [x] iOS actual: real native calls for every `Runtime` permission (AVFoundation, Contacts, EventKit, Photos, CoreLocation, UserNotifications, CoreBluetooth, AppTrackingTransparency), main-thread hop on every completion handler, OS-version gating via `NSProcessInfo`
- [x] iOS: foreground-then-Always location staging mirroring Android's two-step model
- [x] Architecture cleanup: mapping/status-translation code split out of both controllers into dedicated files (`AndroidPermissionMapping.kt`, `IosPermissionStatusMapping.kt`) so orchestration logic stays readable
- [x] `permpilot-compose`: `PermissionGate` with real prime-before-prompt flow, per-state dialog dismissal (not per-permission — a dismissed dialog reappears if the underlying state actually changes), default Material3 dialogs including `PermissionRestrictedNotice`
- [x] `sample`: every `Runtime` permission (27) and every `Special` permission (8) wired into the demo app (`composeApp/App.kt`), `androidApp` manifest declares one `<uses-permission>` per catalog entry plus `<uses-feature required="false">` for hardware-implied permissions
- [x] Full catalog expansion beyond the original draft: Phone/SMS/Call Log (Android-only), Activity Recognition ↔ Core Motion, Audio Files ↔ Apple Music library, Speech Recognition (iOS-only), Reminders (iOS-only), Write Contacts, staged Body-Sensors-Background (API 33+, mirrors the location two-step), plus 3 new `Special` permissions (Do Not Disturb access, Usage Access, Notification Listener access)
- [x] `PermissionState.ConfigurationError` — a sealed-class channel for genuine library-integration mistakes (missing Activity host on Android, missing Info.plist usage-description key on iOS) instead of a thrown exception, a silent hang, or a process crash. This is what actually delivers on "the client shouldn't have to think about platform mechanics": every failure mode reports through the same exhaustive `when`, including ones that used to be a native crash.
- [x] Lifecycle bridge (`ON_RESUME` → re-check state after returning from Settings), both platforms: `PermissionController.refreshAll()` re-checks and republishes every observed permission's `StateFlow`; `permpilot-compose`'s shared `ObserveLifecycleResume` (built on the JetBrains `org.jetbrains.androidx.lifecycle` KMP artifacts, `LocalLifecycleOwner`/`LifecycleEventObserver`) calls it from both `rememberPermissionController` actuals on `ON_RESUME`. Superseded the earlier assumption that Android got this "for free" via recomposition — that was only true for permissions with a live request-result callback; `Special` permissions have no request flow at all and never updated their `StateFlow` without this
- [x] Coarse/approximate-location bugfix, both platforms: grouped `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` requests where the user grants coarse-only were being permanently misreported as `Denied(canRequestAgain = true)` on every `state()` check thereafter (not just the first), since `shouldShowRequestPermissionRationale` stays true for the withheld `FINE` permission — added `LimitedReason.ApproximateLocationOnly` and a dedicated resolver (`resolveForegroundLocationResult`/`checkLocationWhileInUseState`) instead of falling through the generic "all-or-nothing" grouped-permission path. iOS's independent `CLAccuracyAuthorization` (reduced vs. full precision, iOS 14+) was mapped the same way for parity — previously ignored entirely, so a reduced-accuracy grant just read as plain `Granted`.
- [x] Unit tests: `FakePermissionController` (commonMain, in-memory `PermissionController` for consumers) + commonTest coverage; `AndroidPermissionMapping.kt`'s resolve-state functions extracted to pure, directly-testable functions (`resolveDeniedStateFrom`/`resolveGrantResult`/`resolvePhotoLibraryGrantResult`/`resolveForegroundLocationGrantResult`) with `androidHostTest` coverage; `AndroidPermissionController`'s real behavior (Special permission checks, `ConfigurationError`, staged/real request flow, Denied-vs-PermanentlyDenied end-to-end) covered via Robolectric, not mocks; `IosPermissionStatusMapping.kt`'s pure mapping functions covered via `iosTest` (compiles on any host via `compileTestKotlinIosArm64`/`iosSimulatorArm64`; execution needs a Mac/simulator, same limitation as the production iOS actuals). Writing these tests found and fixed a real bug: `updateState()` on both controllers was `states[permission]?.value = state` — a silent no-op if `state()` had never been called for that permission before the first `request()`/`refreshAll()` resolved, silently dropping results (most consequentially `ConfigurationError`, which `checkState()` can't re-derive after the fact). Fixed to `states.getOrPut(permission) { MutableStateFlow(state) }.value = state` on both platforms.
- [x] Publishing pipeline: `com.vanniktech.maven.publish` (`KotlinMultiplatform` variant, `androidVariantsToPublish = ["release"]`, Dokka javadoc, GPG signing) wired into `permpilot-core` and `permpilot-compose`; coordinates/POM metadata in root `gradle.properties` under `io.github.alirezajavan`. `.github/workflows/ci.yml` runs `./gradlew build` on every PR; `.github/workflows/publish.yml` runs on every push to `master`, skip-if-already-tagged, publishes both modules via `publishAndReleaseToMavenCentral`, then cuts a GitHub release. `.githooks/pre-commit` (opt-in via `git config core.hooksPath .githooks`) runs the fast local subset. Apache License 2.0 added at repo root.
- [x] binary-compatibility-validator (`org.jetbrains.kotlinx.binary-compatibility-validator` 0.18.1) wired into `permpilot-core` and `permpilot-compose` with `apiValidation { klib { enabled = true } }`; baseline `.klib.api` dumps checked in at `<module>/api/`. `apiCheck` is wired into `check`, so it already runs as part of `./gradlew build` (CI) without any extra step. **Known limitation:** only validates the `iosArm64`/`iosSimulatorArm64` klib ABI — the plugin doesn't generate a validation task for the Android target at all here, because this project's Android target comes from the newer `com.android.kotlin.multiplatform.library` AGP plugin rather than the classic `kotlin.android()` KGP DSL, and the validator doesn't recognize that target type as class-file-validate-able. Android's public API surface is therefore *not* binary-compat-checked — re-investigate if a future validator release adds support, don't assume it's silently already covered.
- [x] `docs/permission-matrix.md` — standalone consumer-facing reference generated from `Permission.kt`/`AndroidPermissionMapping.kt`/`IosInfoPlistRequirements.kt`: one row per catalog entry (Android manifest permission(s) + API-level gating, iOS Info.plist key(s), `Limited`/staging notes), plus `Special`/`PlatformLimited` tables and a `PermissionState` reference. Added to CLAUDE.md's "new permission checklist" so it doesn't drift from the code.
- [x] `permpilot-history` (SQLDelight, optional audit-log module): a decorator, `HistoryPermissionController : PermissionController by delegate`, wraps any real `PermissionController` (or `FakePermissionController`) and records every `Requested`/`Resolved`/`SettingsOpened` event into a SQLDelight-backed `PermissionHistoryStore`, queryable as a `Flow<List<PermissionHistoryEntry>>`. Deliberately a wrapper, not a new method on `PermissionController` itself — that interface is now binary-compat-locked, and most consumers don't want an audit log, so this stays fully opt-in in its own module. Independently versioned (`version = "0.1.0"` set directly in its `build.gradle.kts`, not the shared root `VERSION_NAME`) so it can ship on its own release cadence. `PermissionHistoryDriverFactory` is the standard SQLDelight KMP driver-factory shape (`expect class` with no declared constructor, so Android's actual can take a `Context` and iOS's can take nothing). Tested end-to-end against a real in-memory `AndroidSqliteDriver` via Robolectric (not a fake store) — found and fixed a real bug in the process: `SqlDelightPermissionHistoryStore`'s `init` block was redundantly calling `Schema.create(driver)`, which both `AndroidSqliteDriver` and `NativeSqliteDriver` already do internally when constructed with a schema, so the very first store instantiation on a real (non-test) driver threw `"table permissionEvent already exists"`. **Now wired into `sample/composeApp`**: a small Compose-only `expect/actual rememberPermissionHistoryStore()` (in the sample itself, not `permpilot-history` — that module deliberately stays Compose-free) obtains the platform driver (`LocalContext.current` on Android, no args on iOS), `App()` wraps the shared controller once with `HistoryPermissionController` and threads it to every demo row so all requests land in one feed, and a `HistoryCard` at the top of the list shows the last 10 events plus a "Clear history" button.
- [x] `SqlDelightPermissionHistoryStore` gained an injectable `dispatcher: CoroutineDispatcher = Dispatchers.Default` constructor parameter (found while chasing a real, reproducible test flake — see below), used consistently by `record()`/`clear()`/`events()` instead of a hardcoded `Dispatchers.Default`. Binary-compat baseline (`permpilot-history.klib.api`) updated via `apiDump` in the same change.
- [x] Fixed a genuine test-isolation bug in `HistoryPermissionControllerTest` (`permpilot-history`'s `androidHostTest`): `SqlDelightPermissionHistoryStore.record()`/`clear()`/`events()` hopped onto the real `Dispatchers.Default` thread pool internally, which `runTest`/`advanceUntilIdle()` has no visibility into — the DB write raced the test's post-`advanceUntilIdle()` assertions. Passed reliably in isolation (fast, uncontended) but flaked under a full `./gradlew build` (heavier CPU contention widened the race window). Fixed by injecting `UnconfinedTestDispatcher(testScope.testScheduler)` from the test via the new constructor parameter above, so `advanceUntilIdle()` actually drives the store's work. Verified with repeated `--rerun` runs, both standalone and inside a full `./gradlew build`.
- [x] Concurrency hardening (previously §9.1's known gap): overlapping `request()`/`requestAll()` calls could drop or leak the earlier call's `CancellableContinuation`, hanging that caller forever — iOS's `locationContinuation`/`bluetoothContinuation` instance fields got overwritten by a second in-flight request before the first's delegate callback fired; Android's `multiRequestFlow` (`extraBufferCapacity = 1`) could silently drop a `tryEmit` from a second overlapping call. Fixed on both platforms with a per-controller `Mutex` (`requestMutex`) wrapping `request()`/`requestAll()` — sufficient because neither platform can show more than one permission dialog at a time regardless, so serializing is free of any real concurrency loss. Both `requestAll()` implementations route through an internal `requestLocked()`/lock-free helper instead of calling the public `request()` recursively, since `Mutex` isn't reentrant. Verified via `compileIosMainKotlinMetadata`, `compileKotlinIosArm64`/`iosSimulatorArm64`, `compileAndroidMain`, and `testAndroidHostTest` — no public API changed, so no `apiDump` was needed for this fix.
- [~] `sample/iosApp` — every text file that's safe to hand-author without Xcode is done: `MainViewController.kt` (`ComposeUIViewController { App() }` entry point in `sample/composeApp/src/iosMain`), `iosApp/Info.plist` with all 13 `NS*UsageDescription` keys pre-filled, `iosApp/iOSApp.swift` + `ContentView.swift` (SwiftUI wrapper around the Kotlin/Native `composeApp` framework's `MainViewController()`), `docs/ios-info-plist-checklist.md` (the per-permission key reference, generated from `IosInfoPlistRequirements.kt`), and `sample/iosApp/README.md` (exact steps: create the Xcode project, swap in these three files, wire the framework via `embedAndSignAppleFrameworkForXcode` in a Run Script build phase — no CocoaPods). **Deliberately not done:** the actual `.xcodeproj`/`project.pbxproj` itself — hand-authoring that binary-ish XML format without Xcode to generate/validate it risks shipping an unopenable project with no useful error message. See §9.2.

---

## 1. Naming

The repository is already named **PermPilot** — keep it. Package root: `io.github.alirezajavan.permpilot`.

---

## 2. Problem framing (validated against current platform behavior)

Android and iOS have **fundamentally different permission state machines**, not just different APIs:

| | Android | iOS |
|---|---|---|
| Native prompt attempts | Unlimited, until user picks "Don't ask again" (Android 11+ often after 2 denials, OEM-dependent) | **Exactly one**, ever, per install |
| Can you query "never asked" vs "permanently denied"? | **No** — `shouldShowRequestPermissionRationale()` returns `false` for both. Library persists its own `hasRequested` flag. | Not ambiguous — status enum is authoritative at all times (except Local Network). |
| Best time to show your own rationale UI | **After** first system denial (you get another native shot) | **Before** the one-shot system prompt ("priming") — there's no second native attempt to react to |
| Settings redirect | Many permissions have a **dedicated deep-link intent** to their own settings screen | **One** generic `openSettingsURLString` — no sub-screen deep-linking is possible, full stop |
| Extra state | Partial media access (`READ_MEDIA_VISUAL_USER_SELECTED`) | `.limited` (Photos), `.restricted` (MDM/parental — **not fixable via Settings**), `.provisional`/`.ephemeral` (notifications) |

This asymmetry is why a naive `expect fun requestPermission(): Boolean` doesn't work — the two platforms need genuinely different orchestration logic behind one common state model. Confirmed during implementation: the two actuals ended up needing almost no shared logic beyond the state model itself, which is exactly why `PermissionController` is the *only* expect/actual surface and everything else in each actual is platform-private.

---

## 3. Module structure & file layout (as built)

```
PermPilot/
├── build-logic/convention/…                          # KMP convention plugins, unchanged from original plan
├── gradle/libs.versions.toml
├── permpilot-core/
│   ├── src/commonMain/kotlin/…/
│   │   ├── Permission.kt                              # full catalog: Runtime / Special / PlatformLimited
│   │   ├── PermissionState.kt                         # 6-case sealed state model
│   │   └── PermissionController.kt                    # the only expect/actual-shaped public surface
│   ├── src/androidMain/kotlin/…/
│   │   ├── AndroidPermissionController.kt              # orchestration: staging, coroutines, persisted flag store
│   │   └── AndroidPermissionMapping.kt                 # pure Permission -> manifest-permission-list mapping
│   └── src/iosMain/kotlin/…/
│       ├── IosPermissionController.kt                  # orchestration: delegates, continuations, staging
│       └── IosPermissionStatusMapping.kt                # pure native-enum -> PermissionState mapping
├── permpilot-compose/
│   └── src/commonMain/kotlin/…/
│       ├── PermissionControllerCompose.kt              # expect rememberPermissionController()
│       ├── PermissionGate.kt                            # orchestrator composable
│       └── DefaultDialogs.kt                            # Rationale / Settings / Restricted default dialogs
│   └── src/androidMain, src/iosMain/…/                 # rememberPermissionController() actuals
├── permpilot-history/                                  # optional SQLDelight audit log, independently versioned
│   ├── src/commonMain/kotlin/…/
│   │   ├── PermissionEvent.kt                          # PermissionEventType + PermissionHistoryEntry
│   │   ├── PermissionHistoryStore.kt                   # interface + SqlDelightPermissionHistoryStore
│   │   ├── PermissionHistoryDriverFactory.kt            # expect driver factory (SQLDelight KMP shape)
│   │   └── HistoryPermissionController.kt                # PermissionController decorator that records events
│   ├── src/commonMain/sqldelight/…/PermissionHistory.sq  # schema + queries
│   ├── src/androidMain, src/iosMain/…/                  # PermissionHistoryDriverFactory actuals
│   └── src/androidHostTest/…/                            # Robolectric, real in-memory AndroidSqliteDriver
├── sample/
│   ├── composeApp/                                     # demo UI wired to every Runtime permission + permpilot-history
│   ├── androidApp/                                     # manifest declares full catalog + uses-feature required=false
│   └── iosApp/                                          # Info.plist + Swift files + setup README; .xcodeproj itself needs a Mac (§9.2)
├── docs/
│   ├── permission-matrix.md                             # consumer-facing per-permission Android/iOS reference
│   └── ios-info-plist-checklist.md                      # per-permission NS*UsageDescription key reference
└── build.gradle.kts / settings.gradle.kts / gradle.properties
```

**Key structural decision made during implementation, beyond the original plan:** each platform actual is split into two files — an *orchestration* file (the class implementing `PermissionController`: coroutines, delegates, staging, persisted state) and a *mapping* file (pure functions translating between `Permission` and the platform's native permission/status types). This wasn't in the original plan but became necessary once the catalog grew past ~9 entries — without the split, `checkState()`'s `when` block and the manifest-permission `when` block made the controller file unreadable. Any new permission touches exactly one function in the mapping file plus (usually) nothing in the orchestration file, since `requestRuntimePermission()`/`checkRuntimePermissionState()` are generic over the mapping.

Deferred, unchanged from the original plan: no Hilt module (`rememberPermissionController()` factory function, no forced DI); `sample/` as the QA harness, not a product.

---

## 4. Public API design

### 4.1 State model

```kotlin
sealed interface PermissionState {
    data object NotDetermined : PermissionState
    data object Granted : PermissionState
    data class Denied(val canRequestAgain: Boolean) : PermissionState
    data object PermanentlyDenied : PermissionState
    data object Restricted : PermissionState
    data class Limited(val reason: LimitedReason) : PermissionState

    // Not a user decision -- a library integration mistake the app developer needs to fix.
    // Reported through the same exhaustive `when` instead of a thrown exception, a silent
    // hang, or (on iOS, absent this check) a process crash.
    data class ConfigurationError(val reason: ConfigurationErrorReason) : PermissionState
}

enum class LimitedReason { PartialMediaAccess, SelectedContactsOnly, ApproximateLocationOnly }

enum class ConfigurationErrorReason {
    NoHostActivity,         // Android: request() called with no Activity attached
    MissingUsageDescription // iOS: Info.plist is missing the required NS*UsageDescription key
}
```

**Added beyond the original plan:** `ConfigurationError` closes an ergonomics gap the state model didn't originally cover. The explicit design goal (per direct instruction) is that a client should be able to "just ask for a permission" without knowing anything about SDK versions, staging, or platform failure modes -- and the original 6-case model already achieved that for every *normal* outcome (a permission not needed on the current OS version silently resolves to `Granted`, staged permissions are invisible to the caller, etc.). What it didn't cover was the *abnormal* case: calling `request()` before an Activity is attached would previously hang forever waiting for a result that could never arrive, and calling an iOS native authorization API without its required Info.plist key terminates the whole process. Both are now caught proactively and reported as data, not exceptions -- consistent with the original "suspend, not exceptions" decision in §4.3/§7.

### 4.2 Permission catalog (full set, as implemented — 27 Runtime, 8 Special, 1 PlatformLimited)

```kotlin
sealed interface Permission {
    sealed interface Runtime : Permission          // has a real request() call on at least one platform
    sealed interface Special : Permission           // Android-only: Settings-redirect only, no runtime dialog
    sealed interface PlatformLimited : Permission   // no request API exists on EITHER platform

    data object Camera : Runtime
    data object Microphone : Runtime
    data object LocationWhileInUse : Runtime
    data object LocationAlways : Runtime
    data object Notifications : Runtime
    data object Contacts : Runtime
    data object WriteContacts : Runtime             // separate Android permission; same CNContactStore auth on iOS
    data class Calendar(val access: CalendarAccess = CalendarAccess.Full) : Runtime
    data object PhotoLibrary : Runtime
    data object AudioFiles : Runtime                // Android READ_MEDIA_AUDIO ↔ iOS MPMediaLibrary (Apple Music)
    data object BluetoothScan : Runtime
    data object NearbyWifiDevices : Runtime         // Android-only concept; iOS actual is a no-op Granted
    data object BodySensors : Runtime               // Android-only, deprecated by Google; iOS actual is a no-op Granted
    data object BodySensorsBackground : Runtime      // Android-only, API 33+, staged after BodySensors like LocationAlways
    data object ActivityRecognition : Runtime       // Android ACTIVITY_RECOGNITION ↔ iOS Core Motion
    data object AppTrackingTransparency : Runtime   // iOS-only, but has a REAL one-shot prompt — see note below
    data object SpeechRecognition : Runtime         // iOS-only (SFSpeechRecognizer); Android needs only Microphone
    data object Reminders : Runtime                 // iOS-only (EKEventStore reminders, distinct from Calendar)
    // Telephony/SMS/Call Log -- Android-only, no iOS API surface exists at all for any of these:
    data object CallPhone : Runtime
    data object ReadPhoneState : Runtime
    data object ReadPhoneNumbers : Runtime          // API 26+
    data object AnswerPhoneCalls : Runtime          // API 26+
    data object ReadCallLog : Runtime
    data object WriteCallLog : Runtime
    data object SendSms : Runtime
    data object ReadSms : Runtime
    data object ReceiveSms : Runtime

    data object SystemAlertWindow : Special
    data object ExactAlarm : Special
    data object IgnoreBatteryOptimizations : Special
    data object WriteSettings : Special
    data object ManageExternalStorage : Special
    data object DoNotDisturbAccess : Special         // NotificationManager.isNotificationPolicyAccessGranted
    data object UsageAccess : Special                // AppOpsManager OPSTR_GET_USAGE_STATS
    data object NotificationListenerAccess : Special // NotificationManagerCompat.getEnabledListenerPackages

    data object LocalNetwork : PlatformLimited      // no requestAccess-style API exists on iOS at all
}
```

**Correction made vs. the original plan:** `AppTrackingTransparency` was originally modeled as `PlatformLimited` alongside `LocalNetwork`. That's wrong — ATT has a genuine native prompt (`ATTrackingManager.requestTrackingAuthorizationWithCompletionHandler`), it's just that `PermissionController.request()` is typed to only accept `Permission.Runtime`. Leaving ATT as `PlatformLimited` made it **permanently unrequestable through the public API** — a real bug caught by trying to wire it up, not a stylistic nit. The distinction that actually matters: `Runtime` = "a request() call exists on at least one platform"; `PlatformLimited` = "no request API exists on either platform, state() can only guess." Only `LocalNetwork` qualifies for the latter now.

### 4.3 Controller (unchanged from original plan)

```kotlin
interface PermissionController {
    fun state(permission: Permission): StateFlow<PermissionState>
    suspend fun request(permission: Permission.Runtime): PermissionState
    suspend fun requestAll(vararg permissions: Permission.Runtime): Map<Permission, PermissionState>
    fun openAppSettings()
    fun openAppSettings(special: Permission.Special)
}

@Composable
fun rememberPermissionController(): PermissionController
```

- Suspend-returns-state, no exceptions — implemented as designed.
- Android: `rememberPermissionController()` tracks the current `Activity` (via `SideEffect` + `ContextWrapper` unwrapping) purely so `shouldShowRequestPermissionRationale` can be checked; the controller itself is held against `applicationContext` so it survives Activity recreation.
- `requestAll` batches natively on Android (one `RequestMultiplePermissions` call across all bundled manifest permissions) except `LocationAlways`, which is always pulled out and staged separately regardless of what else was requested. iOS serializes every permission in `requestAll` through repeated `request()` calls, since iOS can only show one system alert at a time.

### 4.4 Compose UI layer (`permpilot-compose`) — redesigned during implementation

The originally sketched `PermissionGate` had a real bug once actually written: the `NotDetermined` branch called `content(state)` directly with **no rationale shown at all**, contradicting the "always prime first" design goal from this same section. There was also dead state (`showRationale` was declared and reset but never read to gate anything) and no `PermissionRestrictedNotice` implementation.

The rewritten version:

```kotlin
@Composable
fun PermissionGate(
    permission: Permission.Runtime,
    controller: PermissionController = rememberPermissionController(),
    rationale: @Composable (onRequest: () -> Unit, onDismiss: () -> Unit) -> Unit = { onRequest, onDismiss ->
        PermissionRationaleDialog(permission, onConfirm = onRequest, onDismiss = onDismiss)
    },
    settingsPrompt: @Composable (onOpenSettings: () -> Unit, onDismiss: () -> Unit) -> Unit = { onOpenSettings, onDismiss ->
        PermissionSettingsDialog(permission, onConfirm = onOpenSettings, onDismiss = onDismiss)
    },
    restrictedContent: @Composable (onDismiss: () -> Unit) -> Unit = { onDismiss ->
        PermissionRestrictedNotice(permission, onDismiss)
    },
    content: @Composable (PermissionState) -> Unit,
)
```

Design points:
- `content(state)` is **always** called first — the consumer's UI is the single source of truth and can pattern-match on `PermissionState` itself (e.g. render a blurred placeholder while `Denied`). The gate's only job is deciding which dialog, if any, to layer on top.
- `NotDetermined` and `Denied(canRequestAgain = true)` both show `rationale` (the actual "prime before prompting" behavior the design called for).
- A single `dismissedFor: PermissionState?` tracks whether the *current* dialog was already dismissed for the *current* state — not a permanent per-permission flag. Once the state actually changes (a request resolves, or the user returns from Settings and the permission is now granted), the dismissal is naturally invalidated and the gate re-evaluates. This replaced three separate dead `showX` booleans.
- `Restricted` never falls through to `settingsPrompt` — it is terminal, matching §6.

Resolved: both platforms now force a re-check on `ON_RESUME` via `PermissionController.refreshAll()` + `permpilot-compose`'s shared `ObserveLifecycleResume` (see §9/checklist). The earlier assumption that Android didn't need this — `collectAsState()` re-reading on recomposition — only held for permissions with a live request-result callback; `Special` permissions (Settings-only, no request flow) never mutated their `StateFlow` without an explicit re-check, so a user granting `SystemAlertWindow`/`WriteSettings`/etc. in Settings and returning to the app previously left `PermissionGate` showing stale state.

---

## 5. Persistence (implemented as originally planned)

- **Android:** `com.russhwolf:multiplatform-settings`'s `SharedPreferencesSettings`, backing one boolean per permission (`requested_<PermissionSimpleName>`) used purely to disambiguate `NotDetermined` from `PermanentlyDenied` when `shouldShowRequestPermissionRationale` is unavailable or returns `false`. No database, no Room, no SQLDelight for this — confirmed to be the right call, the flag store ended up being exactly "one boolean per permission," nothing more.
- **iOS:** no persistence needed at all — every native authorization API is authoritative and synchronous (except Notifications, see §6), so there's nothing to disambiguate.
- **`permpilot-history`** (optional SQLDelight audit log) — built as a decorator (`HistoryPermissionController`) wrapping any `PermissionController`, not a new core interface method; see the status checklist entry above for the design rationale and the schema-double-create bug it caught.

---

## 6. Permission matrix & platform gotchas (as implemented, supersedes the original draft table)

### Android

| Permission(s) | Gate | Handling |
|---|---|---|
| `ACCESS_BACKGROUND_LOCATION` | API 29+ | `Permission.LocationAlways` always requests foreground (`ACCESS_FINE/COARSE_LOCATION`) first via the normal runtime path, confirms it's granted, *then* issues a second, separate request for background. Below API 29 it's implied granted once foreground is granted — there's no separate permission to request. |
| `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` | all | Always requested together in one dialog (Play policy since Android 12), never just fine alone. The system dialog lets the user grant *just* coarse — `Permission.LocationWhileInUse` reports that as `Limited(ApproximateLocationOnly)`, not `Denied`; treating "not all granted" as a denial here would misreport a legitimate working grant as blocked on every subsequent `state()` check, since `shouldShowRequestPermissionRationale` stays true for the withheld `FINE` permission (bug found and fixed post-launch, see §9). |
| `READ_MEDIA_IMAGES`/`VIDEO` + `READ_MEDIA_VISUAL_USER_SELECTED` | API 34+ requests all three together (enables `Limited(PartialMediaAccess)`); API 33 requests images+video only (no partial tier exists yet); pre-33 falls back to `READ_EXTERNAL_STORAGE` (`maxSdkVersion=32` in the sample manifest). |
| `POST_NOTIFICATIONS` | API 33+ | Below 33, `toManifestPermissions()` returns an empty list and the permission auto-resolves to `Granted` — no dialog. |
| `BLUETOOTH_SCAN` | API 31+, falls back to `ACCESS_FINE_LOCATION` on API 24–30 (BLE scan results required foreground location before `BLUETOOTH_SCAN` existed). `neverForLocation` manifest flag is a per-app declaration, not something the library sets — left to the consumer. |
| `NEARBY_WIFI_DEVICES` | Same pre/post-33 fallback pattern as Bluetooth, for the same historical reason (Wi-Fi scanning needed location before API 33). |
| `BODY_SENSORS` | Requested unconditionally (stable since API 23); flagged in the catalog doc comment as deprecated by Google in favor of granular `android.permission.health.*`, but still functional. |
| Calendar | Always requests `READ_CALENDAR` + `WRITE_CALENDAR` together — Android has no write-only tier, so `CalendarAccess.WriteOnly` is silently ignored on this platform (documented in code). |
| `Denied` vs `PermanentlyDenied` | Disambiguated via: (1) `shouldShowRequestPermissionRationale` if an `Activity` reference is available, else (2) the persisted `hasRequested` flag — **critically, a never-before-requested permission that reports no rationale is treated as `Denied(canRequestAgain = true)`, not `PermanentlyDenied`** (this is the exact bug Accompanist shipped and fixed later: `shouldShowRequestPermissionRationale` also returns `false` before the very first request, and conflating that with permanent denial makes a fresh install look permanently blocked). |
| `Special` permissions | All four (`SystemAlertWindow`, `ExactAlarm`, `IgnoreBatteryOptimizations`, `WriteSettings`, `ManageExternalStorage`) get real state checks (`Settings.canDrawOverlays`, `AlarmManager.canScheduleExactAlarms`, `PowerManager.isIgnoringBatteryOptimizations`, `Settings.System.canWrite`, `Environment.isExternalStorageManager`) — not hardcoded to `Granted` as in the first draft implementation. Their Settings intents all require a `package:<name>` data URI per the official docs (`ACTION_MANAGE_OVERLAY_PERMISSION`, `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` all need it — this was missing in the first draft and silently opened the wrong screen). |

### iOS

| Permission | Native API used | Notes |
|---|---|---|
| Camera / Microphone | `AVCaptureDevice.requestAccessForMediaType` | Genuine one-shot: a denial always resolves straight to `PermanentlyDenied`, never `Denied(canRequestAgain = true)`. |
| Contacts | `CNContactStore` | Same one-shot pattern. |
| Calendar | `EKEventStore` | iOS 17+ splits into `requestFullAccessToEventsWithCompletion`/`requestWriteOnlyAccessToEventsWithCompletion`; below 17, falls back to the deprecated single `requestAccessToEntityType`, gated by `NSProcessInfo.isOperatingSystemAtLeastVersion`. |
| Photo Library | `PHPhotoLibrary` | iOS 14+ uses `requestAuthorizationForAccessLevel(.readWrite)` (enables `.limited` → `Limited(PartialMediaAccess)`); below 14, the deprecated no-argument `requestAuthorization`. |
| Location (While-In-Use / Always) | `CLLocationManager` + delegate | Results arrive via `locationManagerDidChangeAuthorization`, not a completion handler, so the manager and an in-flight `CancellableContinuation` are held as instance state. `requestAlwaysAuthorization()` is confirmed to be a silent no-op unless when-in-use is already granted — the two calls are always two separate prompts and can never be merged. iOS 14+'s separate "Precise Location" toggle (`CLLocationManager.accuracyAuthorization`, reduced vs. full) is independent of the authorization tier — a reduced-accuracy grant maps to `Limited(ApproximateLocationOnly)` rather than `Granted`, mirroring Android's coarse-only case (bug found and fixed post-launch, see §9). The `Always`-upgrade request path explicitly treats a `Limited` foreground result the same as `Granted` when deciding whether to proceed — only a real denial short-circuits it. |
| Notifications | `UNUserNotificationCenter` | **No synchronous status getter exists** — `getNotificationSettingsWithCompletionHandler` is async-only, so `state()` returns a `NotDetermined` placeholder immediately and the real value arrives later via `updateState()` once the completion handler fires. This is a genuine platform limitation, not a shortcut. |
| Bluetooth | `CBCentralManager` | There is no explicit "request" call at all — **instantiating** a `CBCentralManager` for the first time is itself what triggers the system prompt; the result arrives via `centralManagerDidUpdateState`. |
| App Tracking Transparency | `ATTrackingManager` | Gated to iOS 14+ (no-op `Granted` below that); see the `Runtime` vs `PlatformLimited` correction in §4.2. |
| Write Contacts | `CNContactStore` | Same authorization as Contacts — iOS doesn't distinguish read vs. write access, so the actual just delegates to the same request/check calls. |
| Activity Recognition | `CMMotionActivityManager` | No explicit "request" call either — issuing a historical `queryActivityStartingFromDate` is what triggers the one-time prompt; `CMMotionActivityManager.authorizationStatus()` is a synchronous class method for `state()`. |
| Audio Files | `MPMediaLibrary` | `.requestAuthorization`/`.authorizationStatus()` — straightforward, both synchronous and completion-handler APIs exist. |
| Speech Recognition | `SFSpeechRecognizer` | Same shape as Audio Files. Its `SFSpeechRecognizerAuthorizationStatus` enum binds into Kotlin/Native as `SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized` (nested, redundantly-prefixed) rather than a flat top-level constant like `AVAuthorizationStatusAuthorized` — verified by compiling, not guessable from the Swift/ObjC docs alone. |
| Reminders | `EKEventStore` (`EKEntityTypeReminder`) | Same framework and `EKAuthorizationStatus` enum as Calendar, but a distinct authorization the OS tracks separately; iOS 17+ splits it into `requestFullAccessToRemindersWithCompletion` the same way Calendar does. |
| Local Network | *(none)* | No public API exists to request or query this at all — `state()` always reports `Granted` and this is a documented limitation, not a bug. This is the one permission that's genuinely `PlatformLimited`. |
| Special permissions (all 8) + Telephony/SMS/Call Log Runtime permissions (9) | *(none)* | No iOS equivalent for any of them — all no-op `Granted`. `openAppSettings(special)` just calls the one generic `openAppSettings()` since iOS has no per-permission deep link. |
| Threading | `dispatch_async(dispatch_get_main_queue()) { … }` | Every single completion handler/delegate callback in the iOS actual is wrapped in this before touching `states` or resuming a continuation — verified necessary because Apple's docs only guarantee "may be called on an arbitrary queue" for several of these APIs. |
| Verifying iOS code without a Mac | `./gradlew :permpilot-core:compileIosMainKotlinMetadata` and, further, `:permpilot-core:compileKotlinIosArm64`/`iosSimulatorArm64` | These do real frontend type-checking (and, for the latter two, actual klib codegen) against Kotlin/Native's bundled Apple interop stubs — enough to catch wrong cinterop symbol names, wrong nullability, and missing `@OptIn(ExperimentalForeignApi::class)` annotations without ever opening Xcode. This is how every iOS change in this project was actually verified; a real device/simulator run is still needed before shipping. |

### Cross-cutting build/tooling gotchas hit during implementation (worth keeping so they aren't rediscovered)

- A KMP library module's `commonMain` dependency on another of the library's own modules (e.g. `permpilot-compose` → `permpilot-core`) must be declared `api(...)`, not `implementation(...)`, if the depending module's own public API (like `PermissionGate(permission: Permission.Runtime, …)`) exposes the upstream module's types — otherwise consumers (the `sample` app, in this case) get "unresolved reference" on types that are clearly right there in the dependency graph.
- Android manifest XML comments cannot contain a literal `--` — `ManifestMerger2` fails to parse the file rather than just ignoring it, and the error message is generic ("Error parsing …") with no line number.

---

## 7. What NOT to copy from prior art (unchanged from original plan)

- moko-permissions' exception-based API and mandatory `bind(lifecycle)` step.
- Accompanist's `shouldShowRationale` default-false-before-any-request bug — see §6, avoided via the persisted `hasRequested` flag.
- A single generic `openSettings()` for everything.

---

## 8. Testing & release

- [ ] **Unit tests (`permpilot-core`):** a fake `PermissionController` (in-memory `MutableStateFlow` per permission, no OS mocking) for consumers' own tests, plus tests around `AndroidPermissionController`'s resolve-state logic (the `hadRequestedBefore` disambiguation is exactly the kind of logic that regresses silently without a test).
- [ ] **Instrumented/manual QA:** the `sample` app now exercises every `Runtime` permission (`sample/composeApp/App.kt`); still need to actually run the checklist matrix across real API levels (24/29/33/34/36 emulators, iOS 15/17/18 simulators + one physical iOS device for Local Network/ATT) once a Mac is available.
- [x] **API compatibility:** `binary-compatibility-validator` wired into both publishable modules (klib ABI only — see §9 checklist for the Android-target caveat), `apiCheck` runs as part of `./gradlew build`.
- [ ] **Publishing:** vanniktech maven-publish + Dokka not yet set up.

---

## 9. Remaining build order

1. ~~Lifecycle bridge for automatic re-check after returning from Settings~~ — done, both platforms (see §4.4).
2. ~~Coarse/approximate-location misreported as `Denied`~~ — done, both platforms (see §6).
3. ~~Unit tests (fake controller + Android/iOS resolve-state logic) and the Maven Central publishing pipeline~~ — done (see status checklist).
4. ~~binary-compatibility-validator~~ — done, iOS klib ABI only (see status checklist for the Android-target limitation).
5. ~~`docs/permission-matrix.md`~~ — done (see status checklist).
6. ~~`permpilot-history` (SQLDelight) as an explicitly optional, separately-versioned add-on~~ — done (see status checklist), including wiring it into `sample/`.
7. ~~Concurrency hardening for overlapping `request()`/`requestAll()` calls~~ — done, both platforms via `Mutex` (see status checklist and §9.1).
8. ~~A per-permission Info.plist usage-description checklist doc~~ — done, `docs/ios-info-plist-checklist.md` (see status checklist).
9. `sample/iosApp`'s actual `.xcodeproj` — needs a Mac; everything else is staged, see §9.2.
10. Pick items from §10 below.

### 9.1 Fixed: concurrent `request()` calls could hang a caller

Found during a codebase audit (2026-07-02), fixed the same day — keeping the mechanism written down since the fix (a `Mutex`) only prevents the race, it doesn't make the underlying platform quirks disappear if someone re-derives this from scratch:

- **iOS, `CLLocationManager`/`CBCentralManager`-backed permissions** (`LocationWhileInUse`, `LocationAlways`, `BluetoothScan`): `locationContinuation`/`bluetoothContinuation` are single instance fields. Without serialization, a second `request()` call for the same permission before the first call's delegate callback fired would overwrite the field via `awaitLocationAuthorizationChange`/the Bluetooth request, leaving the first `suspendCancellableCoroutine` never resumed — that caller would hang forever (silent, not even a `ConfigurationError`).
- **Android, `AndroidPermissionController.multiRequestFlow`**: a `MutableSharedFlow` with `extraBufferCapacity = 1`. Two `request()`/`requestAll()` calls close enough together could both call `tryEmit`; if the buffer was already full, `tryEmit` would silently return `false` and drop the second `MultiRequest`, leaving that caller's continuation never resumed either.
- Neither case was exercised by the `sample` app before this fix (one `PermissionGate` per row, one request in flight at a time by construction), which is why it wasn't caught earlier.
- **Fix:** a `requestMutex: Mutex` on each controller now wraps `request()`/`requestAll()`. Both platforms route the actual work through an internal `requestLocked()` helper (not the public `request()`) so `requestAll()` can drive several permissions under one lock acquisition without a reentrant-lock deadlock (`Mutex` isn't reentrant). This is sufficient rather than a partial fix because neither Android nor iOS can show more than one permission dialog at a time regardless — full serialization costs nothing real. No public API changed (the `Mutex` and `requestLocked()` are both private), so no `apiDump` was required.

### 9.2 `sample/iosApp`: what's done vs. what still needs a Mac

Everything that's just text and doesn't require Xcode to generate/validate is now checked in:

- `sample/composeApp/src/iosMain/.../MainViewController.kt` — `fun MainViewController(): UIViewController = ComposeUIViewController { App() }`, the actual entry point any iOS host needs. Compiles for real via `compileKotlinIosArm64`/`compileKotlinIosSimulatorArm64` (klib codegen, not just metadata type-checking), so this part is verified even without a Mac.
- `sample/iosApp/iosApp/Info.plist` — all 13 `NS*UsageDescription` keys the sample's `demoPermissions` list needs, pre-filled with demo-appropriate descriptions.
- `sample/iosApp/iosApp/iOSApp.swift` + `ContentView.swift` — a minimal SwiftUI `App` wrapping `MainViewControllerKt.MainViewController()` in a `UIViewControllerRepresentable`.
- `docs/ios-info-plist-checklist.md` — the per-permission `Info.plist` key reference (generated from `IosInfoPlistRequirements.kt`, the source of truth), for consumers building their own app instead of running the sample as-is.
- `sample/iosApp/README.md` — the exact recipe to turn the above into a real Xcode project: create a new SwiftUI App project at `sample/iosApp/`, swap in the three files above in place of Xcode's generated ones, set the deployment target to iOS 15, and wire the `composeApp` Kotlin/Native framework via a Run Script build phase calling `./gradlew :sample:composeApp:embedAndSignAppleFrameworkForXcode` (confirmed this task exists via `:sample:composeApp:tasks --all`) instead of CocoaPods.

**Deliberately not done:** the `.xcodeproj`/`project.pbxproj` file itself. Unlike everything above, that file's format isn't meant to be hand-written — it's Xcode's own serialization of project state, and a single malformed entry (a stray GUID reference, a missing build-phase link) makes the whole project fail to open with no line-numbered error, similar in spirit to the Android manifest `--`-in-comments gotcha in §6 but with much higher blast radius and no way to verify the fix without the tool that generates the format. This is genuinely a "needs a Mac" gap, not a "ran out of time" one — the README above is the handoff artifact so creating the actual project on a Mac is a ~10-minute mechanical step, not a from-scratch design task.

---

## 10. Future features & ergonomics ideas

Beyond the base request/state/settings surface, these are the additions most likely to matter to real consumers, roughly ordered by expected value-per-effort. None are started yet.

1. **`PermissionsGate` (plural) for a whole screen's worth of permissions at once** — today `PermissionGate` wraps one `Permission.Runtime`. A composable that takes a `List<Permission.Runtime>` and drives them through `requestAll` (batching correctly on Android, serializing correctly on iOS — both already handled by the controller) with a single combined rationale/settings flow would remove the boilerplate of nesting several `PermissionGate`s for the common "camera app needs Camera + Microphone + PhotoLibrary" case.
2. **A non-Compose entry point.** `rememberPermissionController()` is Compose-only; a ViewModel-friendly `PermissionController.create(activityProvider: () -> Activity?)` factory (Android) / `PermissionController.create()` (iOS, no Activity concept needed) would let consumers drive permission flows from outside Compose entirely — useful for pure-Kotlin shared-logic layers that only emit intents/effects for the UI layer to act on.
3. **Localization for the default dialogs.** `PermissionRationaleDialog`/`PermissionSettingsDialog`/etc. currently hardcode English strings. Moving them to Compose Multiplatform's `composeResources` (`Res.string.*`) with a handful of shipped translations would make the defaults usable as-is for non-English apps instead of forcing every consumer to override the dialog slots just to translate three words.
4. ~~**A `FakePermissionController` shipped from `permpilot-core`.**~~ Done — see the Testing status checklist entry; `FakePermissionController` is a public commonMain class with `setState`/`openAppSettingsCalls`/`requestCalls` for scripting and asserting against in consumer tests.
5. **A privacy-dashboard composable** — a single screen listing every permission the app has ever requested with its current state and a settings-link button (essentially `demoSpecialPermissions`/`demoPermissions` from the sample app, productized as a real library component). Several apps build this by hand today; PermPilot already has all the state data needed to generate it for free.
6. **Analytics/observability hook.** An optional `PermissionController.events: SharedFlow<PermissionEvent>` (or a constructor-injected listener) emitting structured events (`Requested`, `Resolved(state)`, `SettingsOpened`) would let consumers wire permission funnels into their existing analytics without scattering logging calls through their own call sites.
7. **Pluggable persistence backend.** The Android `hasRequested` flag store is hardcoded to `SharedPreferencesSettings`. Accepting an injected `com.russhwolf.settings.Settings` in the `AndroidPermissionController` constructor (defaulting to the current behavior) would let consumers point it at an encrypted store or share it with their own DataStore/Settings instance, and makes the controller trivially unit-testable without hitting real `SharedPreferences`.
8. **Compile-time manifest/Info.plist auditing.** A small Gradle task (or a Detekt/lint rule) that scans a consumer's source for `Permission.X` usages and cross-references them against the app's `AndroidManifest.xml`/`Info.plist`, failing the build if a used permission's manifest entry or usage-description key is missing. This would catch the exact class of mistake `ConfigurationError` reports *at runtime* one step earlier, at build time — the ideal end state, since `ConfigurationError` is a runtime safety net, not a substitute for compile-time verification.
9. **Compose Multiplatform desktop/wasm actuals.** Out of scope for "permissions" in the traditional OS-prompt sense on desktop, but a no-op `Granted`-everywhere actual (matching how each platform already handles the other's platform-only permissions) would let one `PermissionGate`-based screen compile and run unchanged across all CMP targets, which matters for consumers building truly universal UI.
