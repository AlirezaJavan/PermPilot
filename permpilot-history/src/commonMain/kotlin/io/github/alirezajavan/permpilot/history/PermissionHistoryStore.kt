package io.github.alirezajavan.permpilot.history

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import io.github.alirezajavan.permpilot.Permission
import io.github.alirezajavan.permpilot.PermissionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The generic (non-permission-specific) key used for events like a plain [Permission.Special]-less `openAppSettings()` call. */
internal const val GENERIC_PERMISSION_KEY = "(generic)"

interface PermissionHistoryStore {
    suspend fun record(permission: Permission?, type: PermissionEventType, state: PermissionState? = null)
    fun events(): Flow<List<PermissionHistoryEntry>>
    fun events(permission: Permission): Flow<List<PermissionHistoryEntry>>
    suspend fun clear()
}

/**
 * `driver` should come from [PermissionHistoryDriverFactory.createDriver] on the consuming platform
 * -- both `AndroidSqliteDriver` and `NativeSqliteDriver` already create the schema on first open
 * when constructed with it, so this class must *not* also call `Schema.create(driver)` itself
 * (doing so throws "table already exists" the moment a real, non-test driver reaches this point).
 *
 * [dispatcher] defaults to [Dispatchers.Default] for real consumers. It's an injectable parameter
 * (not hardcoded) so tests can substitute a `TestDispatcher` tied to their `TestScope`'s scheduler
 * -- otherwise DB work escapes onto a real background thread that `runTest`/`advanceUntilIdle()`
 * has no visibility into, making assertions that run right after a `record()`/`clear()` call
 * genuinely racy (passes when the real thread happens to win the race, flakes under load).
 */
@OptIn(ExperimentalTime::class)
class SqlDelightPermissionHistoryStore(
    driver: SqlDriver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PermissionHistoryStore {

    private val queries = PermPilotHistoryDatabase(driver).permissionHistoryQueries

    override suspend fun record(permission: Permission?, type: PermissionEventType, state: PermissionState?) {
        withContext(dispatcher) {
            queries.insertEvent(
                permissionKey = permission.toHistoryKey(),
                eventType = type.name,
                state = state?.toString(),
                timestampMillis = Clock.System.now().toEpochMilliseconds(),
            )
        }
    }

    override fun events(): Flow<List<PermissionHistoryEntry>> =
        queries.selectAll(::toEntry).asFlow().mapToList(dispatcher).flowOn(dispatcher)

    override fun events(permission: Permission): Flow<List<PermissionHistoryEntry>> =
        queries.selectForPermission(permission.toHistoryKey(), ::toEntry).asFlow().mapToList(dispatcher).flowOn(dispatcher)

    override suspend fun clear() {
        withContext(dispatcher) {
            queries.clear()
        }
    }

    private fun toEntry(
        id: Long,
        permissionKey: String,
        eventType: String,
        state: String?,
        timestampMillis: Long,
    ) = PermissionHistoryEntry(
        id = id,
        permissionKey = permissionKey,
        type = PermissionEventType.valueOf(eventType),
        state = state,
        timestampMillis = timestampMillis,
    )
}

// Stable per-permission key for the audit log -- not the same helper as permpilot-core's internal
// persistenceKey() (that's `internal`, module-scoped, and unreachable from here), but the same shape:
// the class's simple name is enough to identify "which permission" without carrying constructor args
// like Calendar's `access` (those still show up in state's toString() when relevant).
private fun Permission?.toHistoryKey(): String = this?.let { it::class.simpleName ?: it.toString() } ?: GENERIC_PERMISSION_KEY
