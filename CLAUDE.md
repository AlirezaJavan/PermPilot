# PermPilot — CLAUDE.md

Project instructions for Claude Code when working in this repository. See `PLAN.md` for full architecture rationale, status checklist, and the permission matrix — this file is the condensed, operational version for day-to-day work.

## What this is

A Kotlin Multiplatform (Android + iOS) library that gives Compose Multiplatform apps a single ergonomic API for OS runtime permissions and special/Settings-gated permissions. The core design goal: **the consumer never needs to know about SDK versions, staged permission requests, rationale timing, or platform-specific failure modes** — they call `request()` on a `Permission.Runtime` and get back an exhaustive `PermissionState`.

Package root: `io.github.alirezajavan.permpilot`.

## Module map

```
permpilot-core/        expect/actual controller + full Permission/PermissionState model (no Compose dependency)
permpilot-compose/     Compose UI layer: PermissionGate, default Material3 dialogs (api-depends on permpilot-core)
permpilot-history/     optional SQLDelight audit-log module; HistoryPermissionController decorator (api-depends on permpilot-core, zero Compose dependency, independently versioned)
sample/composeApp/     demo UI exercising every catalog entry (permpilot-history not yet wired in)
sample/androidApp/     manifest + Android host for the sample
build-logic/convention/ KMP Gradle convention plugins
```

Within `permpilot-core`, each platform's `actual` is split into two files — **keep this split when adding permissions**:
- `*PermissionController.kt` — orchestration only (coroutines, delegates/continuations, staging, persisted flags)
- `*PermissionMapping.kt` (Android) / `*StatusMapping.kt` (iOS) — pure `Permission ↔ platform type` translation, no side effects

Adding a new permission should touch the mapping file (and `Permission.kt` + `IosInfoPlistRequirements.kt`/manifest), rarely the orchestration file — `requestRuntimePermission()`/`checkRuntimePermissionState()` are generic over the mapping.

## Core types (do not casually change shape)

- `Permission` — sealed: `Runtime` (has a real request on ≥1 platform), `Special` (Android-only Settings redirect, no dialog), `PlatformLimited` (no request API on either platform — only `LocalNetwork` qualifies).
- `PermissionState` — sealed: `NotDetermined`, `Granted`, `Denied(canRequestAgain)`, `PermanentlyDenied`, `Restricted`, `Limited(reason)`, `ConfigurationError(reason)`.
- `PermissionController` — the *only* public expect/actual-shaped surface. `state()`/`request()`/`requestAll()`/`openAppSettings()`×2. Suspend-returns-state, **never throws**.

`ConfigurationError` exists specifically so genuine integration mistakes (no host Activity on Android, missing `NS*UsageDescription` key on iOS) report through the same exhaustive `when` instead of hanging or crashing. Any new platform call that can hang without an Activity, or crash without an Info.plist key, must check for that condition first and return `ConfigurationError`, not attempt the call.

## Hard rules learned the hard way

1. **`Denied` vs `PermanentlyDenied` on Android**: never trust `shouldShowRequestPermissionRationale() == false` alone — it's also `false` before the very first request ever. Always cross-check against the persisted `hasRequested` flag (`multiplatform-settings`). This is the exact bug Accompanist shipped and later fixed.
2. **Staged permissions**: background location (`ACCESS_BACKGROUND_LOCATION`, API 29+) and background body sensors (`BODY_SENSORS_BACKGROUND`, API 33+) must request their foreground counterpart first, confirm it's granted, *then* issue a separate second request. Never bundle them into `requestAll`'s native multi-permission call. iOS `requestAlwaysAuthorization()` is a silent no-op unless when-in-use is already granted — same two-step shape.
3. **`PermissionGate` always calls `content(state)` first**, unconditionally — the consumer's UI is the source of truth; the gate only decides which dialog (if any) layers on top. `Restricted` is terminal and never falls through to a settings prompt.
4. **Special permission Settings intents**: `SystemAlertWindow`, `ExactAlarm`, `IgnoreBatteryOptimizations`, `WriteSettings`, `ManageExternalStorage` all require a `package:<name>` data URI on their intent — omitting it silently opens the wrong screen. `DoNotDisturbAccess`, `UsageAccess`, `NotificationListenerAccess` open a generic list screen with no data URI.
5. **iOS threading**: every completion handler / delegate callback that touches shared state or resumes a `CancellableContinuation` must be wrapped in `dispatch_async(dispatch_get_main_queue()) { ... }` — several Apple APIs only guarantee "arbitrary queue" for the callback.
6. **`kotlin.coroutines.resume`, not `kotlinx.coroutines.resume`** — the latter resolves to an internal `DispatchedTask.resume` and produces a confusing "missing onCancellation parameter" error on `suspendCancellableCoroutine`.
7. **Gradle `api` vs `implementation`**: if a module's public API exposes another module's types (e.g. `permpilot-compose`'s `PermissionGate(permission: Permission.Runtime)` exposes `permpilot-core` types), the dependency must be declared `api(...)`. `implementation(...)` causes "unresolved reference" for consumers even though the type is right there in the graph.
8. **Android manifest XML comments cannot contain a literal `--`** — `ManifestMerger2` fails with a generic, line-less parse error. Rephrase instead.
9. **New permission checklist**: add to `Permission.kt` → Android manifest mapping (`toManifestPermissions()`) + real `check*State()` if `Special` → iOS native call + status mapping + `IosInfoPlistRequirements.kt` entry → sample app wiring → sample `AndroidManifest.xml` `<uses-permission>` → PLAN.md matrix row → `docs/permission-matrix.md` row → `./gradlew :permpilot-core:apiDump` (public API surface changed).
10. **Grouped permission requests can partially grant** — don't assume "not all granted" means `Denied`. `LocationWhileInUse` requests `FINE`+`COARSE` together and the OS lets the user grant coarse-only (`Limited(ApproximateLocationOnly)`, matching iOS's independent `CLAccuracyAuthorization` reduced/full axis); `PhotoLibrary` has the same shape for `Limited(PartialMediaAccess)`. Falling through to the generic all-or-nothing resolver for a grouped permission silently misreports a working partial grant as a permanent denial — always check whether a `Permission` has a legitimate partial-grant tier before writing its resolver.
11. **Any new `checkState()`/`refreshAll()` branch must stay side-effect-safe when called repeatedly** — iOS's `Permission.Notifications` has no synchronous status getter, so its `checkState()` branch returns a `NotDetermined` placeholder and refreshes the real value asynchronously as a side effect. Calling that branch from a loop (e.g. `refreshAll()`) will flash an already-`Granted` state back to `NotDetermined` on every call; special-case it to call the async refresh directly instead of going through the generic placeholder-returning path.

## Verifying iOS code without a Mac

No Mac is available in this environment. Use these as the correctness feedback loop instead of assuming code is right:

```
./gradlew :permpilot-core:compileIosMainKotlinMetadata      # fast frontend type-check against Apple K/N stubs
./gradlew :permpilot-core:compileKotlinIosArm64              # real klib codegen, device target
./gradlew :permpilot-core:compileKotlinIosSimulatorArm64     # real klib codegen, simulator target
```

These catch wrong cinterop symbol names, wrong nullability, missing `@OptIn(ExperimentalForeignApi::class)`, and enum-binding shape mistakes (e.g. some Apple enums bind flat as `AVAuthorizationStatusAuthorized`, others bind nested as `SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized` — verify by compiling, don't guess from Swift docs). A real device/simulator run via Xcode is still required before shipping, but these three commands must pass before considering any iOS change done.

Run a full `./gradlew build` (all modules, both platforms, Android Lint included) before considering any cross-cutting change complete.

## Compose Multiplatform conventions for this repo

- `permpilot-compose` has zero business logic beyond dialog display and dismissal-state tracking — all platform logic lives in `permpilot-core`, which has no Compose dependency at all. Don't leak platform `when` blocks into composables.
- `PermissionGate`'s dismissal tracking is `dismissedFor: PermissionState?`, not a per-permission boolean — a dismissed dialog must reappear once the underlying state actually changes (e.g. user returns from Settings and it's still denied). Don't regress this to a sticky per-permission flag.
- Default dialogs (`PermissionRationaleDialog`, `PermissionSettingsDialog`, `PermissionRestrictedNotice`, `PermissionConfigurationErrorNotice`) are all overridable via composable-lambda parameters, never hardcoded inside `PermissionGate` itself — every new state-handling branch needs a corresponding slot, not an inline `AlertDialog`.
- No DI framework requirement: `rememberPermissionController()` is a plain factory `@Composable` function. Don't introduce Hilt/Koin modules into the library itself.
- `sample/` is a QA harness (exercises every catalog entry manually, since OS permission dialogs can't be driven by instrumented tests), not a product to polish.
- Prefer `expect`/`actual` only at the `PermissionController` boundary. Everything else in `commonMain` should be platform-agnostic pure Kotlin (the state model, the catalog) so it never needs an `actual`.

## What NOT to reintroduce

- Exception-based permission APIs or a mandatory `bind(lifecycle)` step (moko-permissions' approach).
- A single generic `openSettings()` covering every Special permission (loses the per-permission deep link on Android).
- `AppTrackingTransparency` as `PlatformLimited` — it has a genuine one-shot native prompt (`ATTrackingManager`) and must stay `Runtime`, or it becomes permanently unrequestable through the typed API.

## Lifecycle re-check on resume

`PermissionController.refreshAll()` re-checks and republishes every currently-observed permission's `StateFlow`. `permpilot-compose`'s shared `ObserveLifecycleResume` (in `LifecycleResumeObserver.kt`, built on the JetBrains `org.jetbrains.androidx.lifecycle` KMP artifacts) calls it from both platforms' `rememberPermissionController()` on `ON_RESUME` — this is what makes `Special` permissions (Settings-redirect only, no request-callback) and `PermanentlyDenied` runtime permissions pick up a Settings-made change when the user returns to the app. Keep this shared in `commonMain`; don't duplicate an Android-only or iOS-only version of it.

## Testing

- `permpilot-core` has three test source sets, each targeting what's actually testable in it without a device: `commonTest` (`kotlin.test` — pure model logic, `FakePermissionController`), `androidHostTest` (JVM-only, no emulator — pure resolve-state functions in `AndroidPermissionMapping.kt` directly, plus real `AndroidPermissionController` behavior driven through Robolectric shadows, not mocks), and `iosTest` (pure functions in `IosPermissionStatusMapping.kt` — compiles via `compileTestKotlinIosArm64`/`compileTestKotlinIosSimulatorArm64` on any host, but Kotlin/Native test binaries for iOS targets can only *execute* on a Mac/simulator).
- If a class's real behavior can't be exercised without heavy mocking, that's a signal to pull the pure decision logic out into a testable function first (see `AndroidPermissionMapping.kt`'s `resolveDeniedStateFrom`/`resolveGrantResult`/etc.) — mirrors the existing orchestration/mapping file split, don't bolt a parallel abstraction on top of it.
- Kotlin/Native test function names must be valid Obj-C symbol names — no `,` or `()` inside backtick-quoted `` `fun \`...\`` `` test names in anything that's part of `commonTest` or `iosTest` (JVM-only source sets like `androidHostTest` don't have this restriction).
- Robolectric shares its underlying SharedPreferences cache across test methods within a run, faithfully matching real Android's static per-file-path cache — don't rely on tests being isolated from each other's persisted state; use a distinct `Permission` per test instead when a test touches PermPilot's persisted `hasRequested` flag.

## Publishing

- `permpilot-core`, `permpilot-compose`, and `permpilot-history` all publish via `com.vanniktech.maven.publish` (`KotlinMultiplatform` variant, Dokka javadoc, GPG signing) to Maven Central under `io.github.alirezajavan`; coordinates and POM metadata live in root `gradle.properties`, except `permpilot-history`'s `version`, which it sets explicitly in its own `build.gradle.kts` (independently versioned from the other two, on purpose — see its checklist entry in PLAN.md). `sample/*` is never published.
- `.github/workflows/ci.yml` runs `./gradlew build` on every PR; `.github/workflows/publish.yml` runs on every push to `master`, skips if the `gradle.properties` `VERSION_NAME` was already tagged, and otherwise publishes both modules and cuts a GitHub release. Bump `VERSION_NAME` as part of the PR that should trigger a release, not after merging. (`permpilot-history`'s independent version isn't currently wired into this release workflow's tag-skip check — bump its own `version` line manually when it needs a release.)
- Enable `.githooks/pre-commit` locally with `git config core.hooksPath .githooks` — it's the fast subset (`permpilot-core` JVM tests + iOS frontend type-check), not the full `./gradlew build` CI runs.
- **`apiCheck` (binary-compatibility-validator) runs as part of `./gradlew build`** and fails the build if any of the three modules' public API surface changed without updating its baseline. After any change to a public type/function signature, run `./gradlew :permpilot-core:apiDump :permpilot-compose:apiDump :permpilot-history:apiDump` and commit the updated `<module>/api/*.klib.api` files in the same change — don't leave `apiCheck` red for CI to catch. Only validates the iOS klib ABI (`iosArm64`/`iosSimulatorArm64`); the Android target from `com.android.kotlin.multiplatform.library` isn't recognized by this plugin version, so Android's public API isn't binary-compat-checked at all — don't assume it is.
- **SQLDelight driver construction already creates the schema** — `AndroidSqliteDriver`/`NativeSqliteDriver` both run `Schema.create()` internally when constructed with a schema argument. Don't also call `Schema.create(driver)` yourself in a store's `init` block; doing so throws `"table X already exists"` on the very first real (non-mocked) instantiation — found and fixed in `SqlDelightPermissionHistoryStore`.

## Keep the README current

Whenever you implement a feature, fix a bug that changes documented behavior, or change the public API surface, update `README.md` in the same change — don't leave it to a follow-up. This includes the feature list, usage examples, and the Core API snippet if `PermissionController`/`PermissionState`/`Permission` shapes change.

## Current gaps (see PLAN.md §9 for order)

`sample/iosApp`'s actual `.xcodeproj` (needs a Mac to create/verify — everything else it needs is staged, see PLAN.md §9.2). `permpilot-history`, `docs/permission-matrix.md`, and binary-compatibility-validator are all done — don't assume they're still gaps.

**Fixed** (was PLAN.md §9.1, resolved 2026-07-02): overlapping `request()`/`requestAll()` calls for the same permission used to be able to hang the earlier caller forever — iOS's `locationContinuation`/`bluetoothContinuation` fields got overwritten by a second in-flight request, and Android's `multiRequestFlow` (`extraBufferCapacity = 1`) could silently drop a `tryEmit` when the buffer was already full. Both platforms now serialize `request()`/`requestAll()` through a per-controller `Mutex` (`requestMutex`). Keep this in mind if you touch either controller's request path: `requestAll()` must keep routing through an internal lock-free/`requestLocked()` helper instead of calling the public `request()` recursively, since `Mutex` isn't reentrant.
