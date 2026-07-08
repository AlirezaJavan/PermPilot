package io.github.alirezajavan.permpilot

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import permpilot.permpilot_compose.generated.resources.Res
import permpilot.permpilot_compose.generated.resources.permpilot_error_confirm
import permpilot.permpilot_compose.generated.resources.permpilot_error_health_unavailable
import permpilot.permpilot_compose.generated.resources.permpilot_error_missing_manifest_declaration
import permpilot.permpilot_compose.generated.resources.permpilot_error_missing_usage_description
import permpilot.permpilot_compose.generated.resources.permpilot_error_no_host_activity
import permpilot.permpilot_compose.generated.resources.permpilot_error_text
import permpilot.permpilot_compose.generated.resources.permpilot_error_title
import permpilot.permpilot_compose.generated.resources.permpilot_rationale_confirm
import permpilot.permpilot_compose.generated.resources.permpilot_rationale_dismiss
import permpilot.permpilot_compose.generated.resources.permpilot_rationale_text
import permpilot.permpilot_compose.generated.resources.permpilot_rationale_title
import permpilot.permpilot_compose.generated.resources.permpilot_restricted_confirm
import permpilot.permpilot_compose.generated.resources.permpilot_restricted_text
import permpilot.permpilot_compose.generated.resources.permpilot_restricted_title
import permpilot.permpilot_compose.generated.resources.permpilot_settings_confirm
import permpilot.permpilot_compose.generated.resources.permpilot_settings_dismiss
import permpilot.permpilot_compose.generated.resources.permpilot_settings_text
import permpilot.permpilot_compose.generated.resources.permpilot_settings_title

/**
 * Fallback human-readable name for a permission in the default dialog copy. Only used inside
 * this file's default parameter values -- consumers who want real (localized) wording override
 * the string parameters, or replace the whole dialog through [PermissionGate]'s slots.
 */
private fun Permission.defaultLabel(): String = this::class.simpleName ?: toString()

/**
 * Every default dialog is customizable at two levels:
 *  1. Text-level: all copy (title, body, button labels) is a plain parameter with a default, so
 *     changing wording or localizing never requires re-implementing the dialog.
 *  2. Slot-level: [PermissionGate] takes each dialog as a composable lambda, so the entire
 *     Material3 AlertDialog can be swapped for any UI (bottom sheet, banner, custom design
 *     system) without touching the gate's state machine.
 */
@Composable
fun PermissionRationaleDialog(
    permission: Permission,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    text: String? = null,
    confirmLabel: String? = null,
    dismissLabel: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title ?: stringResource(Res.string.permpilot_rationale_title)) },
        text = {
            Text(
                text ?: stringResource(
                    Res.string.permpilot_rationale_text,
                    permission.defaultLabel(),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel ?: stringResource(Res.string.permpilot_rationale_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel ?: stringResource(Res.string.permpilot_rationale_dismiss))
            }
        },
    )
}

@Composable
fun PermissionRestrictedNotice(
    permission: Permission,
    onDismiss: () -> Unit,
    title: String? = null,
    text: String? = null,
    confirmLabel: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title ?: stringResource(Res.string.permpilot_restricted_title)) },
        text = {
            Text(
                text ?: stringResource(
                    Res.string.permpilot_restricted_text,
                    permission.defaultLabel(),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(confirmLabel ?: stringResource(Res.string.permpilot_restricted_confirm))
            }
        },
    )
}

@Composable
fun PermissionConfigurationErrorNotice(
    permission: Permission,
    reason: ConfigurationErrorReason,
    onDismiss: () -> Unit,
    title: String? = null,
    text: String? = null,
    confirmLabel: String? = null,
) {
    // This is a developer-facing integration bug, not something an end user can act on -- the
    // default surfaces it loudly rather than pretending it's a normal denial.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title ?: stringResource(Res.string.permpilot_error_title)) },
        text = {
            val explanation = defaultExplanation(permission, reason)
            Text(
                text ?: stringResource(
                    Res.string.permpilot_error_text,
                    permission.defaultLabel(),
                    explanation,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(confirmLabel ?: stringResource(Res.string.permpilot_error_confirm))
            }
        },
    )
}

@Composable
private fun defaultExplanation(
    permission: Permission,
    reason: ConfigurationErrorReason,
): String =
    when (reason) {
        ConfigurationErrorReason.NoHostActivity ->
            stringResource(Res.string.permpilot_error_no_host_activity)
        ConfigurationErrorReason.MissingUsageDescription ->
            stringResource(Res.string.permpilot_error_missing_usage_description, permission.defaultLabel())
        ConfigurationErrorReason.MissingManifestDeclaration ->
            stringResource(Res.string.permpilot_error_missing_manifest_declaration, permission.defaultLabel())
        ConfigurationErrorReason.HealthApiUnavailable ->
            stringResource(Res.string.permpilot_error_health_unavailable)
    }

@Composable
fun PermissionSettingsDialog(
    permission: Permission,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    text: String? = null,
    confirmLabel: String? = null,
    dismissLabel: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title ?: stringResource(Res.string.permpilot_settings_title)) },
        text = {
            Text(
                text ?: stringResource(
                    Res.string.permpilot_settings_text,
                    permission.defaultLabel(),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel ?: stringResource(Res.string.permpilot_settings_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel ?: stringResource(Res.string.permpilot_settings_dismiss))
            }
        },
    )
}
