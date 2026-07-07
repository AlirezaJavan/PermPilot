package io.github.alirezajavan.permpilot

/**
 * Which prompt [PermissionGate] should layer on top of its content for a given state. Extracted
 * from the composable as a pure function (mirroring permpilot-core's orchestration/mapping split)
 * so the complete state x dismissed-for matrix -- including "a dismissed prompt must reappear once
 * the underlying state actually changes" -- is directly unit-testable without a Compose runtime.
 */
internal sealed interface GatePrompt {
    data object None : GatePrompt

    data object Rationale : GatePrompt

    data object Settings : GatePrompt

    data object Restricted : GatePrompt

    data class ConfigurationError(
        val reason: ConfigurationErrorReason,
    ) : GatePrompt
}

internal fun resolveGatePrompt(
    state: PermissionState,
    dismissedFor: PermissionState?,
): GatePrompt {
    // A prompt is only ever silenced for the exact state it was dismissed (or acted on) for; any
    // state change re-evaluates from scratch, so the gate never goes silent forever.
    if (dismissedFor == state) return GatePrompt.None

    return when (state) {
        PermissionState.Granted, is PermissionState.Limited -> GatePrompt.None

        PermissionState.NotDetermined -> GatePrompt.Rationale

        is PermissionState.Denied ->
            if (state.canRequestAgain) GatePrompt.Rationale else GatePrompt.Settings

        PermissionState.PermanentlyDenied -> GatePrompt.Settings

        PermissionState.Restricted -> GatePrompt.Restricted

        is PermissionState.ConfigurationError -> GatePrompt.ConfigurationError(state.reason)
    }
}
