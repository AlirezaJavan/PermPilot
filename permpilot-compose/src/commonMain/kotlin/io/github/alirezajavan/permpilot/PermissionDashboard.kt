package io.github.alirezajavan.permpilot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A productized version of a privacy dashboard that lists permissions and their current states.
 *
 * @param permissions The list of permissions to display.
 * @param controller The [PermissionController] to use for state observation and actions.
 * @param modifier The modifier to apply to the [LazyColumn].
 * @param contentPadding The padding to apply to the [LazyColumn].
 * @param rowContent A custom row content renderer for each permission.
 */
@Composable
fun PermissionDashboard(
    permissions: List<Permission>,
    controller: PermissionController = rememberPermissionController(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    rowContent: @Composable (Permission, PermissionState, () -> Unit) -> Unit = { permission, state, onAction ->
        DefaultPermissionDashboardRow(permission, state, onAction)
    },
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(permissions) { permission ->
            val state by controller.state(permission).collectAsState()
            rowContent(permission, state) {
                when (permission) {
                    is Permission.Special -> controller.openAppSettings(permission)
                    else -> controller.openAppSettings()
                }
            }
        }
    }
}

@Composable
fun DefaultPermissionDashboardRow(
    permission: Permission,
    state: PermissionState,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = permission.label(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "State: $state",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state != PermissionState.Granted && state !is PermissionState.ConfigurationError) {
                Button(onClick = onAction) {
                    Text("Settings")
                }
            }
        }
    }
}

private fun Permission.label(): String =
    when (this) {
        is Permission.Calendar -> "Calendar ($access)"
        is Permission.Health -> "Health (${dataTypes.size} types)"
        else -> this::class.simpleName ?: toString()
    }
