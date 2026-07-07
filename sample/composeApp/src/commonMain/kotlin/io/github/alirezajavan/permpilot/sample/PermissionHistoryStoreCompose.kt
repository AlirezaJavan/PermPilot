package io.github.alirezajavan.permpilot.sample

import androidx.compose.runtime.Composable
import io.github.alirezajavan.permpilot.history.PermissionHistoryStore

/**
 * permpilot-history deliberately has zero Compose dependency, so it has no rememberX() factory of
 * its own (unlike permpilot-compose's rememberPermissionController()) -- this is the sample's own
 * thin Compose-aware glue for obtaining a [PermissionHistoryDriverFactory][io.github.alirezajavan.permpilot.history.PermissionHistoryDriverFactory]
 * per platform (Android needs a Context, iOS needs nothing), so wiring permpilot-history into a
 * Compose Multiplatform commonMain App() has a pattern without permpilot-history itself taking on
 * a Compose dependency.
 */
@Composable
expect fun rememberPermissionHistoryStore(): PermissionHistoryStore
