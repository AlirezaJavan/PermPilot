package io.github.alirezajavan.permpilot.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Entry point Xcode's `iosApp` target calls into. Exposed as a plain top-level function (rather
 * than requiring the Swift side to know about ComposeUIViewController) so `iOSApp.swift` only
 * needs `import composeApp` and `MainViewController()` -- see `sample/iosApp/README.md`.
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
