package io.github.alirezajavan.permpilot.sample

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
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
import io.github.alirezajavan.permpilot.history.PermissionHistoryStore
import io.github.alirezajavan.permpilot.rememberPermissionController
import io.github.alirezajavan.permpilot.resolveCombinedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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

private enum class Screen(
    val title: String,
    val icon: ImageVector,
) {
    Catalog("Catalog", Icons.AutoMirrored.Filled.List),
    Demos("Demos", Icons.Default.Build),
    History("History", Icons.Default.History),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    AppTheme {
        var currentScreen by remember { mutableStateOf(Screen.Catalog) }
        val baseController = rememberPermissionController()
        val historyStore = rememberPermissionHistoryStore()
        val scope = rememberCoroutineScope()
        val controller =
            remember(baseController, historyStore) {
                HistoryPermissionController(baseController, historyStore, scope)
            }
        val historyEntries by historyStore.events().collectAsState(initial = emptyList())
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "PermPilot",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* TODO: More options */ }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 8.dp,
                ) {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = currentScreen,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 2 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 2 })
                },
            ) { screen ->
                when (screen) {
                    Screen.Catalog -> CatalogScreen(controller)
                    Screen.Demos -> DemosScreen(controller)
                    Screen.History -> HistoryScreen(historyEntries, historyStore, scope)
                }
            }
        }
    }
}

@Composable
private fun CatalogScreen(controller: PermissionController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Runtime Permissions",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        items(demoPermissions) { permission ->
            PermissionDemoRow(permission, controller)
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                "Special Permissions",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        items(demoSpecialPermissions) { special ->
            SpecialPermissionDemoRow(special, controller)
        }
    }
}

@Composable
private fun DemosScreen(controller: PermissionController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(
                "Showcase Components",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                "Pre-built UI components and integration patterns to speed up your development.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    }
}

@Composable
private fun HistoryScreen(
    entries: List<PermissionHistoryEntry>,
    historyStore: PermissionHistoryStore,
    scope: CoroutineScope,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Audit Log",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${entries.size} events recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = { scope.launch { historyStore.clear() } },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Clear All")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No history yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries.asReversed()) { entry ->
                    HistoryEntryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryRow(entry: PermissionHistoryEntry) {
    val typeColor =
        when (entry.type.name) {
            "StateChange" -> MaterialTheme.colorScheme.primary
            "Request" -> MaterialTheme.colorScheme.secondary
            "OpenSettings" -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon =
                        when (entry.type.name) {
                            "StateChange" -> Icons.Default.History
                            "Request" -> Icons.Default.Security
                            "OpenSettings" -> Icons.Default.Settings
                            else -> Icons.AutoMirrored.Filled.List
                        }
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = typeColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        entry.permissionKey,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Surface(
                    color = typeColor.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        entry.type.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = typeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            entry.state?.let { state ->
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Result:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        state,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardDemo(controller: PermissionController) {
    var showDashboard by remember { mutableStateOf(false) }
    val allPermissions = remember { demoPermissions + demoSpecialPermissions }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "PermissionDashboard",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "A complete, ready-to-use UI for managing all your app's permissions in one place.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { showDashboard = !showDashboard },
                modifier =
                    Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(if (showDashboard) "Hide Dashboard" else "Launch Dashboard")
            }

            if (showDashboard) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PermissionDashboard(
                    permissions = allPermissions,
                    controller = controller,
                    modifier =
                        Modifier
                            .heightIn(max = 500.dp)
                            .padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionsGateDemoRow(controller: PermissionController) {
    var showGate by remember { mutableStateOf(false) }
    val permissions = remember { listOf(Permission.Camera, Permission.Microphone) }

    val states by
        remember(permissions) {
            combine(permissions.map { controller.state(it) }) { it.toList() }
        }.collectAsState(initial = permissions.map { controller.state(it).value })
    val combined = resolveCombinedState(states)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "PermissionsGate",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Declarative multi-permission request handling with automatic rationale and settings redirection.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (combined == PermissionState.Granted) {
                Row(
                    modifier =
                        Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "All permissions granted!",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (!showGate) {
                Button(
                    onClick = { showGate = true },
                    modifier =
                        Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Request Camera & Mic")
                }
            }

            if (showGate && combined != PermissionState.Granted) {
                PermissionsGate(
                    permissions = permissions,
                    controller = controller,
                    onDismiss = { showGate = false },
                ) { gateStates ->
                    Text(
                        "Current combined state: ${resolveCombinedState(gateStates.values.toList())}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = special.icon(),
                contentDescription = null,
                tint =
                    if (state ==
                        PermissionState.Granted
                    ) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(24.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    special::class.simpleName ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                StateIndicator(state)
            }

            when (state) {
                PermissionState.Granted -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                is PermissionState.ConfigurationError -> {
                    Text(
                        "Error",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> {
                    Button(
                        onClick = { controller.openAppSettings(special) },
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text("Settings", style = MaterialTheme.typography.labelLarge)
                    }
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

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = permission.icon(),
                contentDescription = null,
                tint =
                    if (state ==
                        PermissionState.Granted
                    ) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(24.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    permission.label(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                StateIndicator(state)
            }

            if (state is PermissionState.Limited) {
                Button(
                    onClick = { scope.launch { controller.request(permission) } },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Upgrade", style = MaterialTheme.typography.labelLarge)
                }
            } else if (state != PermissionState.Granted && !showGate) {
                Button(
                    onClick = { showGate = true },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Grant", style = MaterialTheme.typography.labelLarge)
                }
            } else if (state == PermissionState.Granted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }

            if (showGate) {
                PermissionGate(
                    permission = permission,
                    controller = controller,
                    onDismiss = { showGate = false },
                ) { newState ->
                    LaunchedEffect(newState) {
                        if (newState == PermissionState.Granted || newState is PermissionState.Limited) {
                            showGate = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateIndicator(state: PermissionState) {
    val (text, color) =
        when (state) {
            PermissionState.Granted -> "Granted" to MaterialTheme.colorScheme.primary
            is PermissionState.Limited -> "Limited (${state.reason})" to MaterialTheme.colorScheme.tertiary
            is PermissionState.Denied -> {
                if (state.canRequestAgain) {
                    "Denied" to MaterialTheme.colorScheme.secondary
                } else {
                    "Denied (Settings)" to MaterialTheme.colorScheme.error
                }
            }
            PermissionState.PermanentlyDenied -> "Permanently Denied" to MaterialTheme.colorScheme.error
            PermissionState.NotDetermined -> "Not Determined" to MaterialTheme.colorScheme.outline
            PermissionState.Restricted -> "Restricted" to MaterialTheme.colorScheme.error
            is PermissionState.ConfigurationError -> "Config Error" to MaterialTheme.colorScheme.error
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

private fun Permission.Runtime.label(): String =
    when (this) {
        is Permission.Calendar -> "Calendar ($access)"
        is Permission.Health -> "Health ($access, ${dataTypes.size} types)"
        else -> this::class.simpleName ?: "Unknown"
    }
