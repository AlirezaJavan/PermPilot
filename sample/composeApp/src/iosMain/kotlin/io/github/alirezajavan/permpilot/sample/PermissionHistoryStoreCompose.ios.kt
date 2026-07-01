package io.github.alirezajavan.permpilot.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.alirezajavan.permpilot.history.PermissionHistoryDriverFactory
import io.github.alirezajavan.permpilot.history.PermissionHistoryStore
import io.github.alirezajavan.permpilot.history.SqlDelightPermissionHistoryStore

@Composable
actual fun rememberPermissionHistoryStore(): PermissionHistoryStore {
    return remember {
        SqlDelightPermissionHistoryStore(PermissionHistoryDriverFactory().createDriver())
    }
}
