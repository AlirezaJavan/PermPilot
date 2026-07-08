package io.github.alirezajavan.permpilot.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alirezajavan.permpilot.Permission
import io.github.alirezajavan.permpilot.PermissionController
import io.github.alirezajavan.permpilot.PermissionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A plain ViewModel that drives permissions using a [PermissionController] injected via its
 * constructor, proving the non-Compose factory entry point works as expected.
 */
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
    // In a real app this might come from a DI container or a factory that uses the new
    // PermissionController.create(...) platform-specific entry points.
    val viewModel = remember(controller) { PermissionViewModel(controller) }
    val state by viewModel.cameraState.collectAsState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("ViewModel Demo (Camera)", style = MaterialTheme.typography.titleMedium)
            Text("State from VM: $state", style = MaterialTheme.typography.bodyMedium)

            Button(
                onClick = { viewModel.requestCamera() },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Request from ViewModel")
            }
        }
    }
}
