package io.github.alirezajavan.permpilot.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alirezajavan.permpilot.Permission
import io.github.alirezajavan.permpilot.PermissionController
import io.github.alirezajavan.permpilot.PermissionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PermissionViewModel(
    private val controller: PermissionController,
) : ViewModel() {
    val cameraState: StateFlow<PermissionState> = controller.state(Permission.Camera)

    fun requestCamera() {
        viewModelScope.launch {
            controller.request(Permission.Camera)
        }
    }
}

@Composable
fun ViewModelDemoRow(controller: PermissionController) {
    val viewModel = remember(controller) { PermissionViewModel(controller) }
    val state by viewModel.cameraState.collectAsState()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "ViewModel Integration",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Keep your UI logic clean. Drive permissions directly from your ViewModels without any Compose dependencies.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier =
                    Modifier
                        .padding(top = 20.dp)
                        .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        permissionIcon(Permission.Camera),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint =
                            if (state ==
                                PermissionState.Granted
                            ) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Camera", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "State: $state",
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (state ==
                                    PermissionState.Granted
                                ) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                        )
                    }
                }

                Button(
                    onClick = { viewModel.requestCamera() },
                    enabled = state != PermissionState.Granted,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Request")
                }
            }
        }
    }
}

@Composable
private fun permissionIcon(permission: Permission): androidx.compose.ui.graphics.vector.ImageVector = permission.icon()
