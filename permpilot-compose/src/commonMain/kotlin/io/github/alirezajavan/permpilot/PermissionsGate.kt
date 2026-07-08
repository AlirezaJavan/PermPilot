package io.github.alirezajavan.permpilot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * A [PermissionGate] for multiple permissions at once. Wraps a [List] of [Permission.Runtime] and
 * drives them through [PermissionController.requestAll] with a single combined rationale or
 * settings flow.
 *
 * The combined state follows this precedence (highest dominates):
 *  1. [PermissionState.ConfigurationError] (any reason)
 *  2. [PermissionState.Restricted]
 *  3. [PermissionState.PermanentlyDenied]
 *  4. [PermissionState.Denied] (canRequestAgain = false)
 *  5. [PermissionState.Denied] (canRequestAgain = true)
 *  6. [PermissionState.NotDetermined]
 *  7. [PermissionState.Limited]
 *  8. [PermissionState.Granted]
 */
@Composable
fun PermissionsGate(
    permissions: List<Permission.Runtime>,
    controller: PermissionController = rememberPermissionController(),
    rationale: @Composable (onRequest: () -> Unit, onDismiss: () -> Unit) -> Unit = { onRequest, onDismiss ->
        // Uses the first applicable permission as a representative label for the default dialogs,
        // or just the list if the representative is missing.
        val representative = permissions.firstOrNull() ?: Permission.Camera
        PermissionRationaleDialog(representative, onConfirm = onRequest, onDismiss = onDismiss)
    },
    settingsPrompt: @Composable (onOpenSettings: () -> Unit, onDismiss: () -> Unit) -> Unit = { onOpenSettings, onDismiss ->
        val representative = permissions.firstOrNull() ?: Permission.Camera
        PermissionSettingsDialog(representative, onConfirm = onOpenSettings, onDismiss = onDismiss)
    },
    restrictedContent: @Composable (onDismiss: () -> Unit) -> Unit = { onDismiss ->
        val representative = permissions.firstOrNull() ?: Permission.Camera
        PermissionRestrictedNotice(representative, onDismiss)
    },
    configurationErrorContent: @Composable (reason: ConfigurationErrorReason, onDismiss: () -> Unit) -> Unit =
        { reason, onDismiss ->
            val representative = permissions.firstOrNull() ?: Permission.Camera
            PermissionConfigurationErrorNotice(representative, reason, onDismiss)
        },
    onDismiss: () -> Unit = {},
    content: @Composable (Map<Permission, PermissionState>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Observe all permission states and combine them. Seeded from each StateFlow's real current
    // value (not a hardcoded NotDetermined) -- controller.state(it) is synchronous and already
    // reflects reality, so an already-granted/denied permission never flashes a bogus Rationale/
    // Settings prompt for the one frame before the LaunchedEffect below delivers its first value.
    var states by remember(permissions) {
        mutableStateOf<Map<Permission, PermissionState>>(
            permissions.associateWith { controller.state(it).value },
        )
    }

    LaunchedEffect(permissions, controller) {
        val flows = permissions.map { controller.state(it) }
        combine(flows) { it }.collect { values ->
            states = permissions.zip(values).toMap()
        }
    }

    val combinedState = remember(states) { resolveCombinedState(states.values.toList()) }

    var dismissedFor by remember(permissions) { mutableStateOf<PermissionState?>(null) }

    fun dismiss() {
        dismissedFor = combinedState
        onDismiss()
    }

    fun request() {
        dismissedFor = combinedState
        scope.launch { controller.requestAll(*permissions.toTypedArray()) }
    }

    content(states)

    when (val prompt = resolveGatePrompt(combinedState, dismissedFor)) {
        GatePrompt.None -> Unit
        GatePrompt.Rationale -> rationale(::request, ::dismiss)
        GatePrompt.Settings ->
            settingsPrompt({
                dismiss()
                controller.openAppSettings()
            }, ::dismiss)
        GatePrompt.Restricted -> restrictedContent(::dismiss)
        is GatePrompt.ConfigurationError -> configurationErrorContent(prompt.reason, ::dismiss)
    }
}

fun resolveCombinedState(states: List<PermissionState>): PermissionState {
    if (states.isEmpty()) return PermissionState.Granted

    // Precedence sorting: ConfigurationError > Restricted > PermanentlyDenied > Denied > NotDetermined > Limited > Granted
    return states.minByOrNull { state ->
        when (state) {
            is PermissionState.ConfigurationError -> 0
            PermissionState.Restricted -> 1
            PermissionState.PermanentlyDenied -> 2
            is PermissionState.Denied -> if (!state.canRequestAgain) 3 else 4
            PermissionState.NotDetermined -> 5
            is PermissionState.Limited -> 6
            PermissionState.Granted -> 7
        }
    } ?: PermissionState.Granted
}
