# PermPilot — Feature & Permission Roadmap

A **self-contained, executable roadmap** for extending PermPilot. Any AI coding agent (or human)
should be able to open this file cold, pick the top unchecked item, and complete it end to end
without further context beyond `CLAUDE.md` and `docs/permission-matrix.md`.

> This is the single forward-looking planning doc for the repo. Architecture rationale, the
> permission matrix, and the platform gotchas live in `CLAUDE.md` (the "Hard rules learned the hard
> way" and per-platform notes) and `docs/permission-matrix.md`. There is no separate `PLAN.md`.

## How to use this file

1. Pick the **highest-priority unchecked item** (they are ordered by value-per-effort within each
   section). Nothing here has hard ordering dependencies unless a step says so explicitly.
2. Do every sub-step in order. **Each item ends with a mandatory "Wire into sample app" step and a
   "Verify" step** — an item is not done until both pass.
3. When a sub-step is complete, change its `- [ ]` to `- [x]`. When every sub-step of an item is
   done, change the item's heading marker `⬜` to `✅`.
4. Follow the guardrails in `CLAUDE.md` — especially the **"New permission checklist"** (rule 9), the
   `Denied` vs `PermanentlyDenied` rule (rule 1), staged-permission rule (rule 2), the
   `api` vs `implementation` rule (rule 7), and the "keep the README current" and `apiDump` rules.
5. **Never invent a platform enum binding from Swift docs** — compile it (see `CLAUDE.md` §"Verifying
   iOS code without a Mac"). Run `./gradlew build` before considering any cross-cutting change done.

## Status legend

- ✅ item fully done  ·  ⬜ item not started / in progress
- `- [x]` sub-step done  ·  `- [ ]` sub-step not done

---

## Section A — New permissions to add to the catalog

Each of these follows the **same 9-point "New permission checklist" in `CLAUDE.md` rule 9**. That
checklist is the canonical procedure; the sub-steps below only call out what's *specific* to each
permission. The generic checklist (do this for every permission in this section) is:

> `Permission.kt` entry → Android `toManifestPermissions()` mapping (+ real `check*State()` if
> `Special`) → iOS native call + status mapping + `IosInfoPlistRequirements.kt` entry → sample app
> wiring → sample `AndroidManifest.xml` `<uses-permission>` → `docs/permission-matrix.md` row →
> `./gradlew :permpilot-core:apiDump` → `README.md` feature list → `./gradlew build`.

### ✅ A1. Bluetooth Connect & Bluetooth Advertise (Android 12+)

Today only `BluetoothScan` exists, but Android split runtime Bluetooth into **three** separate
permissions in API 31 (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`). A consumer
connecting to an already-bonded device needs `BLUETOOTH_CONNECT`, which PermPilot cannot currently
express.

- [x] Add `data object BluetoothConnect : Runtime` and `data object BluetoothAdvertise : Runtime` to `Permission.kt`.
- [x] Android mapping: `BLUETOOTH_CONNECT` / `BLUETOOTH_ADVERTISE`, both **API 31+ only**. Pre-31 there is no runtime equivalent (legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` were normal, install-time) → return empty list → auto-`Granted`, same pattern as `POST_NOTIFICATIONS` below API 33.
- [x] iOS: both map to the **same `CBCentralManager` authorization as `BluetoothScan`** (iOS has one Bluetooth authorization, not a scan/connect/advertise split). Delegate to the existing Bluetooth request/check in `IosPermissionController`.
- [x] iOS Info.plist: reuse `NSBluetoothAlwaysUsageDescription` (already required by `BluetoothScan`) — no new key.
- [x] **Wire into sample app:** add both to `demoPermissions` in `sample/composeApp/App.kt`; add `<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>` and `..._ADVERTISE` (with `android:minSdkVersion`/no `maxSdkVersion`) to `sample/androidApp/.../AndroidManifest.xml`.
- [x] Docs: add a `docs/permission-matrix.md` row (Android manifest permissions + API gating, iOS "no-op `Granted`").
- [x] **Verify:** `./gradlew :permpilot-core:apiDump` then `./gradlew build`.

### ✅ A2. Health / Fitness (Android Health Connect ↔ iOS HealthKit)

The single most-requested capability missing from the catalog. **This is a large item** — Health
Connect and HealthKit are per-data-type permission models, not a single grant, so it needs a design
decision first. Do **A2a** before writing code.

- [x] **A2a (design):** Decide the shape. Recommended: `data class Health(val dataTypes: Set<HealthDataType>) : Runtime` where `HealthDataType` is a small shipped enum (`Steps`, `HeartRate`, `Sleep`, `ActiveEnergy`, …). Health permissions are read/write per type; add `val access: HealthAccess = Read` if write matters. Documented in `docs/health-design.md`.
- [x] Android: Health Connect uses its own permission strings (`android.permission.health.READ_STEPS`, etc.) and a **separate `PermissionController` contract from Health Connect's SDK**, not the standard `ActivityResultContracts.RequestMultiplePermissions`. This likely needs its own staged path in `AndroidPermissionController` — treat like a mini-subsystem, keep the orchestration/mapping split.
- [x] iOS: `HKHealthStore.requestAuthorization(toShare:read:)`. Needs `NSHealthShareUsageDescription` / `NSHealthUpdateUsageDescription` Info.plist keys and the HealthKit entitlement (document the entitlement requirement — it can't be set from KMP).
- [x] Add a `ConfigurationErrorReason` variant if Health Connect app / HealthKit availability is absent, so an unavailable-on-device case reports as data, not a crash.
- [x] **Wire into sample app** (guard the row behind availability), docs, `apiDump`, `README.md`, `./gradlew build`.

> ⚠️ This item is worth splitting into its own branch/PR. If time-boxed, ship **A2a design doc first**
> and leave implementation as a follow-up unchecked item.

### ✅ A3. Media Location (`ACCESS_MEDIA_LOCATION`, Android 10+)

Lets an app read the original GPS EXIF location baked into a photo. Distinct from location
permission. Android-only.

- [x] `data object MediaLocation : Runtime`; Android maps to `ACCESS_MEDIA_LOCATION` (API 29+); iOS no-op `Granted` (Photos already exposes location to an authorized app).
- [x] Note the ordering dependency in the doc comment: it's only meaningful once photo/media read access is granted.
- [x] Standard checklist + **sample wiring** + verify.

### ✅ A4. Full-Screen Intent (`USE_FULL_SCREEN_INTENT`, Android 14+) — Special

On Android 14+ this became a user-revocable, Settings-gated permission (alarm/calling apps only get
it auto-granted). Fits the `Special` model exactly.

- [x] `data object FullScreenIntent : Special`; real state check via `NotificationManager.canUseFullScreenIntent()` (API 34+; below 34 → `Granted`).
- [x] Settings intent: `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` **with the `package:` data URI** (see `CLAUDE.md` rule 4 — special-permission intents that need the URI).
- [x] iOS no-op `Granted`. Standard `Special` checklist + **sample `demoSpecialPermissions` wiring** + verify.

### ✅ A5. Wire up the two already-modeled-but-unused `LimitedReason`s

`LimitedReason.SelectedContactsOnly` exists in the enum but nothing produces it. iOS 18 added
**limited contacts access** (`CNAuthorizationStatusLimited`), and Android has no equivalent.

- [x] iOS: map `CNContactStore` `CNAuthorizationStatusLimited` (iOS 18+) → `Limited(SelectedContactsOnly)` in `IosPermissionStatusMapping.kt`. Verify the enum binding name **by compiling**, not from docs.
- [x] Confirm `Contacts`/`WriteContacts` resolvers don't fall through the all-or-nothing path (see `CLAUDE.md` rule 10 — partial-grant tiers).
- [x] Add an `iosTest` case for the new mapping. **Sample app** already lists Contacts — just confirm the row renders the `Limited` state; docs row update; verify.

---

## Section B — Library features & ergonomics

These are ordered by value-per-effort — the ergonomics additions most likely to matter to real
consumers beyond the base request/state/settings surface.

### ✅ B1. `PermissionsGate` (plural) — one gate for several permissions

Wraps a `List<Permission.Runtime>`, drives them through `controller.requestAll(...)` with a single
rationale/settings flow. Removes the boilerplate of nesting several `PermissionGate`s for
the "camera app needs Camera + Microphone + PhotoLibrary" case.

- [x] Add `PermissionsGate(permissions: List<Permission.Runtime>, ...)` to `permpilot-compose` (new file or alongside `PermissionGate.kt`). Reuse the existing dialog slots; the combined state is the "worst" state across permissions (any `Restricted`/`ConfigurationError` dominates, then `PermanentlyDenied`, then `Denied`, etc. — define the precedence explicitly in a doc comment).
- [x] Keep `permpilot-compose` free of platform `when` blocks (`CLAUDE.md` Compose conventions) — all batching already lives in `controller.requestAll`.
- [x] Preserve the `dismissedFor: PermissionState?` (not per-permission-boolean) dismissal model — here it's `dismissedFor` the *combined* state.
- [x] **Wire into sample app:** add a demo row that gates on Camera + Microphone together via `PermissionsGate`.
- [x] `./gradlew :permpilot-compose:apiDump`, update `README.md` usage example, `./gradlew build`.

### ✅ B2. Non-Compose entry point (ViewModel-friendly factory)

`rememberPermissionController()` is Compose-only. Add a plain factory so pure-Kotlin/ViewModel layers
can drive permissions.

- [x] `permpilot-core`: add `expect` factory — Android `PermissionController.create(activityProvider: () -> Activity?)`, iOS `PermissionController.create()`. This must **not** introduce a Compose dependency into `permpilot-core`.
- [x] Android actual: reuse `AndroidPermissionController` but source the current `Activity` from the injected provider instead of the Compose `SideEffect` bridge.
- [x] `rememberPermissionController()` in `permpilot-compose` should ideally delegate to this factory (avoid two divergent construction paths).
- [x] **Wire into sample app:** add a small non-Compose demo (e.g. a ViewModel that requests Camera and exposes state) to prove the path.
- [x] `apiDump` both affected modules, `README.md`, `./gradlew build`.

### ✅ B3. Localize the default dialogs

`PermissionRationaleDialog`/`PermissionSettingsDialog`/`PermissionRestrictedNotice`/
`PermissionConfigurationErrorNotice` hardcode English.

- [x] Move strings to Compose Multiplatform `composeResources` (`Res.string.*`) in `permpilot-compose`.
- [x] Ship at least 2–3 translations (e.g. `values-es`, `values-fr`, `values-de` equivalents) so the defaults are usable non-English without overriding slots.
- [x] Keep every string overridable via the existing composable-lambda slots (don't hardcode inside `PermissionGate`).
- [x] **Wire into sample app:** nothing new required, but confirm the sample still builds with resources; optionally add a locale toggle.
- [x] `./gradlew build` (no API change expected → no `apiDump`, but confirm `apiCheck` stays green).

### ✅ B4. Privacy-dashboard composable

Productize the sample's `demoPermissions`/`demoSpecialPermissions` list into a real library
component: a screen listing every permission with its current state + a settings-link button.

- [x] Add `PermissionDashboard(permissions: List<Permission>, controller, ...)` to `permpilot-compose`. Uses `controller.state(...)` for each and the appropriate settings action (`openAppSettings()` vs `openAppSettings(special)`).
- [x] Make rows/row-content overridable via slots, consistent with the dialog-slot convention.
- [x] **Wire into sample app:** replace (or add alongside) the hand-rolled list in `App.kt` with `PermissionDashboard` to exercise the new component.
- [x] `apiDump`, `README.md`, `./gradlew build`.

### ✅ B5. Analytics/observability hook on the core controller

An optional structured-event stream so consumers can wire permission funnels into analytics without
scattering logging. Note: `permpilot-history` already models `Requested`/`Resolved`/`SettingsOpened`
events — **reuse those types, don't invent parallel ones**, but keep this in `permpilot-core` so it
doesn't force the SQLDelight dependency.

- [x] Decide placement: because `PermissionController` is **binary-compat-locked**, do **not** add an abstract method. Instead add an optional constructor-injected listener (`PermissionEventListener?`) or a `SharedFlow` exposed on the concrete controllers, mirroring how `permpilot-history` stayed a decorator (`CLAUDE.md` publishing notes / rule about the locked interface).
- [x] Move the shared event value types into `permpilot-core` (or a tiny shared spot) so both `permpilot-core`'s hook and `permpilot-history` reference one definition. If this moves a type, coordinate the `apiDump` for both modules.
- [x] **Wire into sample app:** log emitted events to the existing `HistoryCard` or Logcat to show the stream firing.
- [x] `apiDump` affected modules, `README.md`, `./gradlew build`.

### ✅ B6. Pluggable persistence backend (Android)

The Android `hasRequested` flag store is hardcoded to `SharedPreferencesSettings`. Accept an injected
`com.russhwolf.settings.Settings` (defaulting to current behavior) so consumers can point it at an
encrypted store and so the controller is unit-testable without real `SharedPreferences`.

- [x] Add an optional `settings: Settings = <current default>` parameter to `AndroidPermissionController`'s constructor and the Android `create(...)` factory (B2). **Default must preserve today's exact behavior.**
- [x] Add a unit test that injects an in-memory `MapSettings` and asserts the `Denied` vs `PermanentlyDenied` disambiguation without Robolectric — this is the payoff.
- [x] **Wire into sample app:** no user-facing change required; optionally demonstrate injecting a custom `Settings`.
- [x] `apiDump` if the constructor is public API, `./gradlew build`.

---

## Section C — Bigger / longer-horizon items

Lower priority or larger scope. Pull these in only after Sections A–B are substantially done.

### ⬜ C1. Compile-time manifest / Info.plist auditing (Gradle task or lint rule)

Catch a missing manifest entry / usage-description key **at build time** — one step earlier than the
runtime `ConfigurationError` safety net.

- [ ] Prototype a Gradle task that scans consumer source for `Permission.X` usages and cross-references `AndroidManifest.xml` / `Info.plist`, failing the build on a missing entry. (A Detekt/lint rule is the alternative — decide based on IDE-integration value.)
- [ ] Ship it as an opt-in plugin, not on by default. **Sample app:** wire the task into `sample`'s build to demonstrate a passing audit; add a deliberately-broken branch in docs to show a failing one.

### ⬜ C2. Compose Multiplatform desktop / wasm actuals

A no-op `Granted`-everywhere actual (matching how each platform already no-ops the other's
platform-only permissions) so one `PermissionGate`-based screen compiles and runs unchanged across
all CMP targets.

- [ ] Add `jvm`(desktop)/`wasmJs` targets to `permpilot-core` + `permpilot-compose` with no-op `Granted` `PermissionController` actuals and the `rememberPermissionController()`/lifecycle actuals.
- [ ] Confirm binary-compatibility-validator handling for the new targets (may need baseline updates or exclusions — note the validator only covers the iOS klib ABI today, not the Android target; see `CLAUDE.md`'s publishing/`apiCheck` note).
- [ ] **Wire into sample app:** add a `sample/desktopApp` (and/or wasm) target that runs the existing `App()` unchanged, proving cross-target compile.
- [ ] `README.md` supported-platforms section, `./gradlew build`.

### ⬜ C3. Finish `sample/iosApp`'s `.xcodeproj` (needs a Mac)

Not a coding task for a non-Mac agent. The `.xcodeproj`/`project.pbxproj` is Xcode's own serialized
project state — hand-authoring it blind risks an unopenable project with no line-numbered error, so
it's deliberately left for a Mac. Everything else it needs is already staged (Compose entry point,
`Info.plist` with all usage-description keys, Swift wrapper, setup README); follow
`sample/iosApp/README.md` on a Mac — it's a ~10-minute mechanical step, not a design task.

- [ ] (On a Mac) create the `.xcodeproj` per `sample/iosApp/README.md`, wire the framework via `embedAndSignAppleFrameworkForXcode` in a Run Script build phase, run on a simulator + one physical device (for Local Network / ATT).

### ⬜ C4. Manual QA matrix across real API levels & iOS versions (needs a Mac + emulators)

The `sample` app exercises every catalog entry, but OS permission dialogs can't be driven by
instrumented tests, so the catalog still needs a real manual run-through. This is the last piece of
"tested" that automation can't cover.

- [ ] Android: run the sample on emulators at API **24 / 29 / 33 / 34** (and latest), verifying the version-gated cases — pre-33 `POST_NOTIFICATIONS` auto-`Granted`, pre-31 Bluetooth/Nearby-Wi-Fi fallback to `ACCESS_FINE_LOCATION`, API 34 partial photo access (`Limited(PartialMediaAccess)`), background-location and body-sensors-background two-step staging, and `Denied` vs `PermanentlyDenied` after a "Don't ask again".
- [ ] iOS: run on simulators at **iOS 15 / 17 / 18** plus **one physical device** (Local Network and ATT can't be exercised on a simulator), verifying the iOS-17 Calendar/Reminders full-vs-write-only split, iOS-14 precise-location reduced accuracy → `Limited(ApproximateLocationOnly)`, and photo `.limited`.
- [ ] Record results in a checklist (a `docs/qa-matrix.md` table is a good home) so regressions are visible run-over-run.

---

## Progress summary

Keep this table in sync as items flip to ✅ (it's the fast at-a-glance view; the sections above are
the source of truth).

| Item | Title | Status |
|---|---|---|
| A1 | Bluetooth Connect & Advertise | ✅ |
| A2 | Health / Fitness (Health Connect ↔ HealthKit) | ✅ |
| A3 | Media Location | ✅ |
| A4 | Full-Screen Intent (Special) | ✅ |
| A5 | Wire up `SelectedContactsOnly` (iOS 18 limited contacts) | ✅ |
| B1 | `PermissionsGate` (plural) | ✅ |
| B2 | Non-Compose entry point | ✅ |
| B3 | Localize default dialogs | ✅ |
| B4 | Privacy-dashboard composable | ✅ |
| B5 | Analytics/observability hook | ✅ |
| B6 | Pluggable persistence backend | ✅ |
| C1 | Compile-time manifest/Info.plist audit | ⬜ |
| C2 | Desktop / wasm actuals | ⬜ |
| C3 | `sample/iosApp` `.xcodeproj` (needs a Mac) | ⬜ |
| C4 | Manual QA matrix (API levels / iOS versions) | ⬜ |
