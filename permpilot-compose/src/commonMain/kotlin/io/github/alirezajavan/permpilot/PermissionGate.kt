package io.github.alirezajavan.permpilot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

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
    configurationErrorContent: @Composable (reason: ConfigurationErrorReason, onDismiss: () -> Unit) -> Unit =
        { reason, onDismiss -> PermissionConfigurationErrorNotice(permission, reason, onDismiss) },
    onDismiss: () -> Unit = {},
    content: @Composable (PermissionState) -> Unit,
) {
    val state by controller.state(permission).collectAsState()
    val scope = rememberCoroutineScope()

    // A prompt is only ever silenced for the specific state it was shown for. Once the
    // underlying permission state actually changes -- a request resolves, or the user returns
    // from Settings -- the gate re-evaluates from scratch instead of staying silent forever.
    var dismissedFor by remember(permission) { mutableStateOf<PermissionState?>(null) }

    fun dismiss() {
        dismissedFor = state
        onDismiss()
    }

    fun request() {
        // Silence the prompt for the current state only -- deliberately NOT dismiss(): confirming
        // the rationale is not a dismissal, and invoking the consumer's onDismiss here invites
        // them to remove the gate from composition, which would cancel `scope` (and with it the
        // in-flight request) while the OS dialog is still on screen. That exact wiring was how
        // grants got silently dropped: the result resumed a cancelled continuation and the UI
        // never saw the state change.
        dismissedFor = state
        scope.launch { controller.request(permission) }
    }

    // content always reflects the live state so callers can render permission-aware UI (e.g. a
    // blurred placeholder while Denied); the gate's job is only to decide which prompt, if any,
    // to layer on top of it.
    content(state)

    // Which prompt (if any) goes on top is a pure decision extracted to resolveGatePrompt so the
    // full state x dismissal matrix is unit-testable without a Compose runtime.
    when (val prompt = resolveGatePrompt(state, dismissedFor)) {
        GatePrompt.None -> Unit

        // Priming before the single native prompt attempt costs nothing on Android and is the
        // only way to explain *why* on iOS, which never gives a second chance (PLAN.md §4.4).
        GatePrompt.Rationale -> rationale(::request, ::dismiss)

        GatePrompt.Settings -> settingsPrompt({ dismiss(); controller.openAppSettings() }, ::dismiss)

        // Restricted (MDM/parental controls) is terminal -- nothing in Settings can fix it, so
        // there is no settingsPrompt fallback here, only the dedicated notice.
        GatePrompt.Restricted -> restrictedContent(::dismiss)

        // A library integration mistake (missing Activity host / Info.plist key), not a user
        // decision -- surfaced through the same exhaustive `when` instead of a thrown exception.
        is GatePrompt.ConfigurationError -> configurationErrorContent(prompt.reason, ::dismiss)
    }
}
