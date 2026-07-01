package io.github.alirezajavan.permpilot.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.alirezajavan.permpilot.history.PermissionHistoryDriverFactory
import io.github.alirezajavan.permpilot.history.PermissionHistoryStore
import io.github.alirezajavan.permpilot.history.SqlDelightPermissionHistoryStore

@Composable
actual fun rememberPermissionHistoryStore(): PermissionHistoryStore {
    val context = LocalContext.current
    return remember {
        SqlDelightPermissionHistoryStore(PermissionHistoryDriverFactory(context.applicationContext).createDriver())
    }
}
