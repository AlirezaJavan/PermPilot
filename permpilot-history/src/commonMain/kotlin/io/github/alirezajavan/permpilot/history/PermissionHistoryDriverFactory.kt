package io.github.alirezajavan.permpilot.history

import app.cash.sqldelight.db.SqlDriver

internal const val DATABASE_NAME = "permpilot_history.db"

/**
 * Creates the platform [SqlDriver] backing [PermPilotHistoryDatabase]. No primary constructor is
 * declared here (deliberately) so each platform's actual can take whatever it needs to construct a
 * driver -- Android's needs a `Context`, iOS's needs nothing. This is the standard SQLDelight KMP
 * driver-factory shape, not a PermPilot-specific pattern.
 */
expect class PermissionHistoryDriverFactory {
    fun createDriver(): SqlDriver
}
