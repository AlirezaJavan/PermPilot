package io.github.alirezajavan.permpilot.sample

import androidx.compose.runtime.Composable
import io.github.alirezajavan.permpilot.history.PermissionHistoryStore

/**
 * permpilot-history deliberately has zero Compose dependency, so it has no rememberX() factory of
 * its own (unlike permpilot-compose's rememberPermissionController()) -- this is the sample's own
 * thin Compose-aware glue for obtaining a [PermissionHistoryDriverFactory][io.github.alirezajavan.permpilot.history.PermissionHistoryDriverFactory]
 * per platform (Android needs a Context, iOS needs nothing), matching PLAN.md's note that wiring
 * permpilot-history into a Compose Multiplatform commonMain App() needed a pattern the sample
 * didn't have yet.
 */
@Composable
expect fun rememberPermissionHistoryStore(): PermissionHistoryStore
