package io.github.alirezajavan.permpilot.history

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class PermissionHistoryDriverFactory(
    private val context: Context,
) {
    actual fun createDriver(): SqlDriver = AndroidSqliteDriver(PermPilotHistoryDatabase.Schema, context.applicationContext, DATABASE_NAME)
}
