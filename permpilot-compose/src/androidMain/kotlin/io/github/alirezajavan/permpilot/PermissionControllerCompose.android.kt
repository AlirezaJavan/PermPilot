package io.github.alirezajavan.permpilot

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.collectLatest

@Composable
actual fun rememberPermissionController(): PermissionController {
    val context = LocalContext.current
    val controller = remember { AndroidPermissionController(context.applicationContext) }

    // shouldShowRequestPermissionRationale is an Activity-only API; refresh the controller's
    // reference every recomposition so it survives Activity recreation across config changes.
    SideEffect {
        controller.updateActivity(context.findActivity())
    }

    var currentMultiCallback by remember { mutableStateOf<((Map<String, Boolean>) -> Unit)?>(null) }

    val multiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        currentMultiCallback?.invoke(results)
        currentMultiCallback = null
    }

    LaunchedEffect(controller) {
        controller.multiRequestFlow.collectLatest { request ->
            currentMultiCallback = request.onResult
            multiLauncher.launch(request.permissions)
        }
    }

    // Special permissions (and PermanentlyDenied runtime ones) are only ever resolved via a
    // Settings redirect, which never calls back into request() -- ON_RESUME after returning from
    // that Settings screen is the only signal available that the state may have changed.
    ObserveLifecycleResume { controller.refreshAll() }

    return controller
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
