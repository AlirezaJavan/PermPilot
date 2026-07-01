package io.github.alirezajavan.permpilot.history

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class PermissionHistoryDriverFactory {
    actual fun createDriver(): SqlDriver = NativeSqliteDriver(PermPilotHistoryDatabase.Schema, DATABASE_NAME)
}
