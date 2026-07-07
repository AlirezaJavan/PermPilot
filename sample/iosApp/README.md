# sample/iosApp

There is deliberately no checked-in `.xcodeproj` here. Xcode project files are effectively a build
artifact of Xcode itself, and hand-authoring `project.pbxproj` without Xcode to generate/validate it
is a real way to ship an unopenable project -- a single malformed entry breaks the whole file with
no useful error. What *is* checked in and safe to hand-write is everything Xcode's wizard would
otherwise generate for you: the Swift entry point, the view wrapper around Compose Multiplatform's
`UIViewController`, and the `Info.plist` with every required `NS*UsageDescription` key already
filled in (see `docs/ios-info-plist-checklist.md`). This doc is the exact recipe to turn those into
a real, runnable Xcode project. None of these steps have been run against a real Xcode install --
this repo was authored on Windows, which has no Xcode at all -- so treat this as a precise starting
point to verify on your Mac, not a guaranteed-working recording.

## Prerequisites

- macOS with Xcode 15+ installed.
- This repo cloned, with `./gradlew :sample:composeApp:iosSimulatorArm64Test` (or any other iOS
  Gradle task) already working from a terminal, confirming the Kotlin/Native toolchain is set up.

## 1. Create the Xcode project

1. Xcode → File → New → Project → iOS → **App**.
2. Product Name: `iosApp`. Interface: **SwiftUI**. Language: **Swift**. Uncheck "Include Tests".
3. Save it at `sample/iosApp/` (the repo root of this folder) -- Xcode will create `iosApp.xcodeproj`
   and an `iosApp/` folder alongside the `iosApp/` folder that already exists here with the files
   below. When prompted about existing files, keep Xcode's project file but don't let it silently
   overwrite `ContentView.swift`, `iOSApp.swift`, or `Info.plist` -- replace Xcode's generated
   versions of those three with the ones already checked in here (they wire up
   `composeApp`'s `MainViewController()` and declare every required permission usage-description).
4. Project settings → General → Minimum Deployments → **iOS 15.0** (matches this repo's primary iOS
   support target for this repo).

## 2. Wire up the Kotlin/Native framework (no CocoaPods)

`sample/composeApp/build.gradle.kts` already configures `iosArm64()`/`iosSimulatorArm64()` static
framework targets named `composeApp` (see `build-logic/convention/.../KmpTargets.kt` --
`baseName = project.name`), which is why `ContentView.swift` does `import composeApp`. To have
Xcode build that framework as part of every build, without CocoaPods:

1. Select the `iosApp` target → **Build Phases** → `+` → **New Run Script Phase**.
2. Drag the new phase to be the *first* phase, above "Compile Sources".
3. Paste:
   ```sh
   cd "$SRCROOT/../.."
   ./gradlew :sample:composeApp:embedAndSignAppleFrameworkForXcode
   ```
4. Build Settings → search **User Script Sandboxing** → set to **No** (the Gradle invocation above
   needs filesystem/network access the sandbox blocks).
5. Build once (⌘B). The task reads Xcode's own `CONFIGURATION`/`SDK_NAME`/`ARCHS` environment
   variables to build the matching `iosArm64` or `iosSimulatorArm64` framework variant and embeds +
   code-signs it automatically -- no manual "Link Binary With Libraries" step or Framework Search
   Paths entry needed.

## 3. Run

Pick an iOS 15+ simulator (or a physical device for `LocalNetwork`/`AppTrackingTransparency`, which
need real hardware/network — see `docs/ROADMAP.md` C4) and Run (⌘R). You should see the same permission list
as the Android sample, backed by the same `permpilot-core`/`permpilot-compose`/`permpilot-history`
Kotlin code.

## Files in this folder

- `iosApp/iOSApp.swift` -- `@main` SwiftUI `App` entry point.
- `iosApp/ContentView.swift` -- wraps `MainViewControllerKt.MainViewController()` (from
  `sample/composeApp/src/iosMain/.../MainViewController.kt`) in a `UIViewControllerRepresentable`.
- `iosApp/Info.plist` -- every `NS*UsageDescription` key the sample's `demoPermissions` list needs;
  see `docs/ios-info-plist-checklist.md` for the full per-permission mapping if you're building your
  own app instead of running the sample as-is.
