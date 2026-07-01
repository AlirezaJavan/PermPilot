package io.github.alirezajavan.permpilot

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

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
    title: String = "Permission Required",
    text: String = "This app needs access to your ${permission.defaultLabel()} to function correctly.",
    confirmLabel: String = "Allow",
    dismissLabel: String = "Deny",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    )
}

@Composable
fun PermissionRestrictedNotice(
    permission: Permission,
    onDismiss: () -> Unit,
    title: String = "Access Restricted",
    text: String = "Access to ${permission.defaultLabel()} is restricted by a device policy " +
        "(such as parental controls or an MDM profile) and can't be changed from " +
        "within this app or its Settings screen.",
    confirmLabel: String = "OK",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(confirmLabel)
            }
        }
    )
}

@Composable
fun PermissionConfigurationErrorNotice(
    permission: Permission,
    reason: ConfigurationErrorReason,
    onDismiss: () -> Unit,
    title: String = "PermPilot configuration error",
    text: String = "Can't request ${permission.defaultLabel()}: ${defaultExplanation(permission, reason)}",
    confirmLabel: String = "OK",
) {
    // This is a developer-facing integration bug, not something an end user can act on -- the
    // default surfaces it loudly rather than pretending it's a normal denial.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(confirmLabel)
            }
        }
    )
}

private fun defaultExplanation(permission: Permission, reason: ConfigurationErrorReason): String = when (reason) {
    ConfigurationErrorReason.NoHostActivity ->
        "rememberPermissionController() was never composed into an Activity-hosted screen, " +
            "so there's nowhere to show the system permission dialog."
    ConfigurationErrorReason.MissingUsageDescription ->
        "the Info.plist usage-description key(s) for ${permission.defaultLabel()} are missing."
    ConfigurationErrorReason.MissingManifestDeclaration ->
        "the AndroidManifest is missing what ${permission.defaultLabel()} needs -- a " +
            "<uses-permission> declaration (for DoNotDisturbAccess: ACCESS_NOTIFICATION_POLICY), " +
            "or a declared NotificationListenerService for NotificationListenerAccess."
}

@Composable
fun PermissionSettingsDialog(
    permission: Permission,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Permission Denied",
    text: String = "You have denied ${permission.defaultLabel()} permission. Please enable it in settings.",
    confirmLabel: String = "Open Settings",
    dismissLabel: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    )
}
