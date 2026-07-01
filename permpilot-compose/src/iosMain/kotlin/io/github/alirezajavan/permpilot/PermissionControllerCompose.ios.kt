package io.github.alirezajavan.permpilot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPermissionController(): PermissionController {
    val controller = remember { IosPermissionController() }

    // Covers both the Settings-redirect return trip and plain backgrounding/foregrounding, e.g.
    // a Photos/Contacts grant made from Springboard's picker while this app was suspended.
    ObserveLifecycleResume { controller.refreshAll() }

    return controller
}
