package io.github.alirezajavan.permpilot.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alirezajavan.permpilot.CalendarAccess
import io.github.alirezajavan.permpilot.HealthAccess
import io.github.alirezajavan.permpilot.HealthDataType
import io.github.alirezajavan.permpilot.Permission
import io.github.alirezajavan.permpilot.PermissionController
import io.github.alirezajavan.permpilot.PermissionDashboard
import io.github.alirezajavan.permpilot.PermissionGate
import io.github.alirezajavan.permpilot.PermissionState
import io.github.alirezajavan.permpilot.PermissionsGate
import io.github.alirezajavan.permpilot.history.HistoryPermissionController
import io.github.alirezajavan.permpilot.history.PermissionHistoryEntry
import io.github.alirezajavan.permpilot.rememberPermissionController
import io.github.alirezajavan.permpilot.resolveCombinedState
import kotlinx.coroutines.launch

/**
 * One row per catalog entry so every Runtime permission's full request/rationale/settings/
 * restricted flow gets exercised manually against a real device -- permission dialogs are OS
 * chrome and can't be driven by instrumented tests (see CLAUDE.md: sample/ is a QA harness).
 */
private val demoPermissions: List<Permission.Runtime> =
    listOf(
        Permission.Camera,
        Permission.Microphone,
        Permission.LocationWhileInUse,
        Permission.LocationAlways,
        Permission.Notifications,
        Permission.Contacts,
        Permission.WriteContacts,
        Permission.Calendar(CalendarAccess.Full),
        Permission.PhotoLibrary,
        Permission.MediaLocation,
        Permission.AudioFiles,
        Permission.BluetoothScan,
        Permission.BluetoothConnect,
        Permission.BluetoothAdvertise,
        Permission.NearbyWifiDevices,
        Permission.BodySensors,
        Permission.BodySensorsBackground,
        Permission.ActivityRecognition,
        Permission.Health(
            dataTypes = setOf(HealthDataType.Steps, HealthDataType.HeartRate, HealthDataType.Sleep),
            access = HealthAccess.Read,
        ),
        Permission.CallPhone,
        Permission.ReadPhoneState,
        Permission.ReadPhoneNumbers,
        Permission.AnswerPhoneCalls,
        Permission.ReadCallLog,
        Permission.WriteCallLog,
        Permission.SendSms,
        Permission.ReadSms,
        Permission.ReceiveSms,
        Permission.AppTrackingTransparency,
        Permission.SpeechRecognition,
        Permission.Reminders,
    )

/**
 * Special permissions have no native request dialog at all -- there's no PermissionGate for
 * them, just a state() query plus a Settings redirect the consumer triggers directly.
 */
private val demoSpecialPermissions: List<Permission.Special> =
    listOf(
        Permission.SystemAlertWindow,
        Permission.ExactAlarm,
        Permission.FullScreenIntent,
        Permission.IgnoreBatteryOptimizations,
        Permission.WriteSettings,
        Permission.ManageExternalStorage,
        Permission.DoNotDisturbAccess,
        Permission.UsageAccess,
        Permission.NotificationListenerAccess,
    )

@Composable
fun App() {
    MaterialTheme {
        Scaffold { padding ->
            val baseController = rememberPermissionController()
            val historyStore = rememberPermissionHistoryStore()
            val scope = rememberCoroutineScope()
            // Decorates the real controller with an audit log -- every row below drives requests
            // through this single wrapped instance so demoPermissions/demoSpecialPermissions all
            // land in the same history feed instead of each row's own untracked controller.
            val controller =
                remember(baseController, historyStore) {
                    HistoryPermissionController(baseController, historyStore, scope)
                }
            val historyEntries by historyStore.events().collectAsState(initial = emptyList())

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(padding),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    HistoryCard(historyEntries, onClear = { scope.launch { historyStore.clear() } })
                }
                item {
                    PermissionsGateDemoRow(controller)
                }
                item {
                    ViewModelDemoRow(controller)
                }
                item {
                    DashboardDemo(controller)
                }
                items(demoPermissions) { permission ->
                    PermissionDemoRow(permission, controller)
                }
                items(demoSpecialPermissions) { special ->
                    SpecialPermissionDemoRow(special, controller)
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entries: List<PermissionHistoryEntry>,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Permission history (${entries.size})", style = MaterialTheme.typography.titleMedium)
            // The full log, newest first, in its own independently scrollable list. The bounded
            // height is what makes nesting a lazy list inside the screen's LazyColumn legal --
            // and keeps the card from pushing every permission row off screen as the log grows.
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
            ) {
                items(entries.asReversed()) { entry ->
                    Text(
                        "${entry.permissionKey}: ${entry.type}" + (entry.state?.let { " -> $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Button(onClick = onClear) {
                Text("Clear history")
            }
        }
    }
}

@Composable
private fun DashboardDemo(controller: PermissionController) {
    var showDashboard by remember { mutableStateOf(false) }
    val allPermissions = remember { demoPermissions + demoSpecialPermissions }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("PermissionDashboard (Inline Demo)", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = { showDashboard = !showDashboard },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(if (showDashboard) "Hide Dashboard" else "Show Dashboard")
            }

            if (showDashboard) {
                PermissionDashboard(
                    permissions = allPermissions,
                    controller = controller,
                    modifier = Modifier.heightIn(max = 400.dp),
                    contentPadding = PaddingValues(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionsGateDemoRow(controller: PermissionController) {
    var showGate by remember { mutableStateOf(false) }
    val permissions = remember { listOf(Permission.Camera, Permission.Microphone) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("PermissionsGate (Camera + Mic)", style = MaterialTheme.typography.titleMedium)

            if (!showGate) {
                Button(
                    onClick = { showGate = true },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Request both")
                }
            }

            if (showGate) {
                PermissionsGate(
                    permissions = permissions,
                    controller = controller,
                    onDismiss = { showGate = false },
                ) { states ->
                    val combined = resolveCombinedState(states.values.toList())
                    LaunchedEffect(combined) {
                        if (combined == PermissionState.Granted) {
                            showGate = false
                        }
                    }
                    Text(
                        "Combined state: $combined",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecialPermissionDemoRow(
    special: Permission.Special,
    controller: PermissionController,
) {
    val state by controller.state(special).collectAsState()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(special::class.simpleName ?: "Unknown", style = MaterialTheme.typography.titleMedium)
            Text("State: $state", style = MaterialTheme.typography.bodyMedium)
            when (state) {
                PermissionState.Granted ->
                    Text(
                        "Granted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                // e.g. MissingManifestDeclaration -- Settings can't fix an integration mistake,
                // so don't offer a redirect that can only dead-end.
                is PermissionState.ConfigurationError ->
                    Text(
                        "Configuration error -- see State above",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                else ->
                    Button(
                        onClick = { controller.openAppSettings(special) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Open Settings")
                    }
            }
        }
    }
}

@Composable
private fun PermissionDemoRow(
    permission: Permission.Runtime,
    controller: PermissionController,
) {
    var showGate by remember { mutableStateOf(false) }
    val state by controller.state(permission).collectAsState()
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(permission.label(), style = MaterialTheme.typography.titleMedium)
            Text("State: $state", style = MaterialTheme.typography.bodyMedium)

            if (state is PermissionState.Limited) {
                // Limited (partial photo access, coarse-only location) is a *working* grant, so
                // PermissionGate deliberately shows no prompt for it. Upgrading is a direct
                // re-request -- the OS shows its own picker/upgrade dialog, no rationale needed.
                Button(
                    onClick = { scope.launch { controller.request(permission) } },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Request full access")
                }
            } else if (state != PermissionState.Granted && !showGate) {
                Button(
                    onClick = { showGate = true },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Grant ${permission.label()}")
                }
            }

            if (showGate) {
                // onDismiss unmounts the gate, which is safe because it now only fires on a real
                // user dismissal -- never mid-request, where unmounting would cancel the gate's
                // request coroutine while the OS dialog is still up.
                PermissionGate(
                    permission = permission,
                    controller = controller,
                    onDismiss = { showGate = false },
                ) { newState ->
                    // State writes belong in an effect, not composition; keyed on newState so the
                    // gate is retired exactly once, after the grant actually lands.
                    LaunchedEffect(newState) {
                        if (newState == PermissionState.Granted || newState is PermissionState.Limited) {
                            showGate = false
                        }
                    }
                    Text(
                        when (newState) {
                            PermissionState.Granted -> "Permission granted"
                            is PermissionState.Limited -> "Limited access granted (${newState.reason})"
                            else -> "Awaiting permission ($newState)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun Permission.Runtime.label(): String =
    when (this) {
        is Permission.Calendar -> "Calendar ($access)"
        is Permission.Health -> "Health ($access, ${dataTypes.size} types)"
        else -> this::class.simpleName ?: "Unknown"
    }
