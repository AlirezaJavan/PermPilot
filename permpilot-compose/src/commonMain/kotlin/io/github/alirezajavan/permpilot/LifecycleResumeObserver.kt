package io.github.alirezajavan.permpilot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Invokes [onResume] every time the host [androidx.lifecycle.LifecycleOwner] reports `ON_RESUME`
 * -- on Android that's the hosting Activity (including the return trip from a Settings redirect);
 * on iOS the JetBrains Compose Multiplatform runtime drives the same lifecycle from
 * `UIApplication` foreground/background notifications. Kept as one shared implementation so both
 * platform [rememberPermissionController] actuals get the exact same "re-check on resume" wiring.
 */
@Composable
internal fun ObserveLifecycleResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume by rememberUpdatedState(onResume)
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) currentOnResume()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
