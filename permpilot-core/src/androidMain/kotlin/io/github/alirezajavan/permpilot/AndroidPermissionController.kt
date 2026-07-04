package io.github.alirezajavan.permpilot

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.Settings as KeyValueSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

class AndroidPermissionController(
    private val context: Context
) : PermissionController {

    private val prefs: KeyValueSettings = SharedPreferencesSettings(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val states = mutableMapOf<Permission, MutableStateFlow<PermissionState>>()

    // shouldShowRequestPermissionRationale is an Activity API; the controller itself is held
    // via applicationContext so it survives Activity recreation, so the current Activity is
    // tracked separately and refreshed by the Compose bridge on every recomposition.
    private var activityRef: WeakReference<Activity>? = null

    val multiRequestFlow = MutableSharedFlow<MultiRequest>(extraBufferCapacity = 1)

    // Serializes every request()/requestAll() call through this controller. Without it, two
    // overlapping calls can both tryEmit() into multiRequestFlow's single-slot buffer -- the
    // second tryEmit silently returns false and drops that MultiRequest, leaving its
    // CancellableContinuation resumed never, hanging that caller forever (PLAN.md §9.1). A Mutex
    // is sufficient because Android can only show one permission dialog at a time regardless, so
    // there's no real concurrency to preserve here -- just correctness for callers that (wrongly
    // or not) fire two requests close together.
    private val requestMutex = Mutex()

    // The app's own declared <uses-permission> entries, read once. Null means "unknown" (the
    // package declares none at all, or the lookup failed -- e.g. bare Robolectric test apps), and
    // unknown is deliberately never treated as missing: this check exists to catch the real-world
    // mistake of forgetting ONE declaration, where requestedPermissions is non-null and simply
    // lacks that entry, without ever producing false ConfigurationErrors elsewhere.
    private val declaredPermissions: Set<String>? by lazy {
        try {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                ?.toSet()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    // Requesting a permission the manifest never declared doesn't fail loudly on Android -- the
    // OS instantly reports it denied with no dialog, which the resolver would then misread as a
    // real user denial (and as PermanentlyDenied from the second attempt on). Detect it up front
    // and report ConfigurationError instead.
    private fun isMissingFromManifest(manifestPermissions: List<String>): Boolean {
        val declared = declaredPermissions ?: return false
        return manifestPermissions.any { it !in declared }
    }

    fun updateActivity(activity: Activity?) {
        activityRef = activity?.let { WeakReference(it) }
    }

    private fun hasHostActivity(): Boolean = activityRef?.get() != null

    override fun state(permission: Permission): StateFlow<PermissionState> {
        return states.getOrPut(permission) {
            MutableStateFlow(checkState(permission))
        }.asStateFlow()
    }

    override suspend fun request(permission: Permission.Runtime): PermissionState = requestMutex.withLock {
        requestLocked(permission)
    }

    // Callable only while requestMutex is already held -- requestAll() below needs to drive
    // several of these under a single lock acquisition, and Mutex isn't reentrant.
    private suspend fun requestLocked(permission: Permission.Runtime): PermissionState = when (permission) {
        Permission.LocationAlways -> requestBackgroundLocation()
        Permission.BodySensorsBackground -> requestBodySensorsBackground()
        else -> requestRuntimePermission(permission)
    }

    override suspend fun requestAll(vararg permissions: Permission.Runtime): Map<Permission, PermissionState> =
        requestMutex.withLock {
            // Background location and background body sensors can never be bundled into the same
            // system dialog as anything else (Android requires their foreground counterpart to
            // already be granted before either can even be asked), so both are always pulled out of
            // the batch and driven through their own staged flow.
            val staged = setOf(Permission.LocationAlways, Permission.BodySensorsBackground)
            val batchPermissions = permissions.filterNot { it in staged }

            val results = mutableMapOf<Permission, PermissionState>()
            if (batchPermissions.isNotEmpty()) {
                results.putAll(requestBatch(batchPermissions))
            }
            if (permissions.contains(Permission.LocationAlways)) {
                results[Permission.LocationAlways] = requestBackgroundLocation()
            }
            if (permissions.contains(Permission.BodySensorsBackground)) {
                results[Permission.BodySensorsBackground] = requestBodySensorsBackground()
            }
            results
        }

    override fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    @SuppressLint("BatteryLife")
    override fun openAppSettings(special: Permission.Special) {
        val (action, needsPackageData) = when (special) {
            Permission.SystemAlertWindow -> Settings.ACTION_MANAGE_OVERLAY_PERMISSION to true
            Permission.ExactAlarm -> if (Build.VERSION.SDK_INT >= 31) {
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM to true
            } else {
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS to true
            }
            Permission.IgnoreBatteryOptimizations -> Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS to true
            Permission.WriteSettings -> Settings.ACTION_MANAGE_WRITE_SETTINGS to true
            Permission.ManageExternalStorage -> if (Build.VERSION.SDK_INT >= 30) {
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION to true
            } else {
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS to true
            }
            // Unlike the five above, these three open a *generic list* of every app requesting
            // that access -- there is no reliable per-app deep link for them, so no package: data
            // URI is attached (attaching one is silently ignored by some OEMs, mishandled by others).
            Permission.DoNotDisturbAccess -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS to false
            Permission.UsageAccess -> Settings.ACTION_USAGE_ACCESS_SETTINGS to false
            Permission.NotificationListenerAccess -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS to false
        }

        val intent = Intent(action).apply {
            if (needsPackageData) {
                data = Uri.fromParts("package", context.packageName, null)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private suspend fun requestRuntimePermission(permission: Permission.Runtime): PermissionState {
        val manifestPermissions = permission.toManifestPermissions()
        if (manifestPermissions.isEmpty()) {
            val granted = PermissionState.Granted
            updateState(permission, granted)
            return granted
        }

        // Google's documented workflow is check-then-request: when the permission is already
        // fully granted there is nothing to ask the OS for, so short-circuit before the Activity
        // check below -- otherwise an already-granted permission requested before any Activity is
        // attached would be misreported as ConfigurationError. Limited states (coarse-only
        // location, partial media) deliberately fall through: re-requesting them is the documented
        // upgrade path and does show a real dialog.
        val current = checkState(permission)
        if (current == PermissionState.Granted) {
            updateState(permission, current)
            return current
        }

        if (isMissingFromManifest(permission.requiredManifestDeclarations())) {
            val error = PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration)
            updateState(permission, error)
            return error
        }

        // Without a live Activity, rememberPermissionController()'s launcher was never composed
        // into a real host, so tryEmit()-ing here would suspend forever waiting for a result that
        // can never arrive. Fail fast with a reportable state instead of hanging.
        if (!hasHostActivity()) {
            val error = PermissionState.ConfigurationError(ConfigurationErrorReason.NoHostActivity)
            updateState(permission, error)
            return error
        }

        val hadRequestedBefore = isRequested(permission)
        markRequested(permission)

        return suspendCancellableCoroutine { continuation ->
            multiRequestFlow.tryEmit(MultiRequest(manifestPermissions.toTypedArray()) { results ->
                // State is published from inside the launcher callback, not after the suspend
                // point: if the awaiting caller was cancelled while the OS dialog was up (e.g. the
                // composable that launched the request left composition), resume() below becomes a
                // no-op -- but the StateFlow, and every PermissionGate observing it, must still
                // receive the real outcome instead of silently staying stale.
                val newState = resolveState(permission, results, hadRequestedBefore)
                updateState(permission, newState)
                continuation.resume(newState)
            })
        }
    }

    private suspend fun requestBatch(permissions: List<Permission.Runtime>): Map<Permission, PermissionState> {
        val applicable = permissions.filter { it.toManifestPermissions().isNotEmpty() }
        val notApplicable = permissions.filterNot { applicable.contains(it) }

        // Same check-then-request rule as the single-permission path: anything already fully
        // granted is answered locally and dropped from the native batch, so a batch of
        // all-granted permissions never touches the launcher (and never needs an Activity).
        val alreadyGranted = applicable.filter { checkState(it) == PermissionState.Granted }
        val notGranted = applicable.filterNot { alreadyGranted.contains(it) }

        // Undeclared entries are answered locally with ConfigurationError and excluded from the
        // native batch -- bundling one in would make the OS auto-deny it, and its false result
        // would pollute the resolver's view of the whole batch.
        val undeclared = notGranted.filter { isMissingFromManifest(it.requiredManifestDeclarations()) }
        val toRequest = notGranted.filterNot { undeclared.contains(it) }

        if (toRequest.isNotEmpty() && !hasHostActivity()) {
            val error = PermissionState.ConfigurationError(ConfigurationErrorReason.NoHostActivity)
            return permissions.associate { p ->
                updateState(p, error)
                (p as Permission) to error
            }
        }

        val hadRequestedBeforeMap = toRequest.associateWith { isRequested(it) }
        toRequest.forEach { markRequested(it) }

        val manifestPermissions = toRequest.flatMap { it.toManifestPermissions() }.distinct().toTypedArray()

        val batchResults: Map<Permission, PermissionState> = if (manifestPermissions.isEmpty()) {
            emptyMap()
        } else {
            suspendCancellableCoroutine { continuation ->
                multiRequestFlow.tryEmit(MultiRequest(manifestPermissions) { results ->
                    val resultMap = toRequest.associate { p ->
                        val state = resolveState(p, results, hadRequestedBeforeMap.getValue(p))
                        updateState(p, state)
                        (p as Permission) to state
                    }
                    continuation.resume(resultMap)
                })
            }
        }

        val grantedWithoutRequest = (alreadyGranted + notApplicable).associate { p ->
            updateState(p, PermissionState.Granted)
            (p as Permission) to PermissionState.Granted
        }

        val undeclaredErrors = undeclared.associate { p ->
            val error = PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration)
            updateState(p, error)
            (p as Permission) to error
        }

        return batchResults + grantedWithoutRequest + undeclaredErrors
    }

    private suspend fun requestBackgroundLocation(): PermissionState {
        // ACCESS_BACKGROUND_LOCATION cannot be requested (or even shown in the same dialog) until
        // foreground location is already granted -- the OS silently ignores it otherwise.
        val foregroundState = requestRuntimePermission(Permission.LocationWhileInUse)
        if (foregroundState != PermissionState.Granted && foregroundState !is PermissionState.Limited) {
            updateState(Permission.LocationAlways, foregroundState)
            return foregroundState
        }

        if (Build.VERSION.SDK_INT < 29) {
            // Below Android 10 there is no separate background-location permission: foreground
            // access already implies background access.
            val granted = PermissionState.Granted
            updateState(Permission.LocationAlways, granted)
            return granted
        }

        if (isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            val granted = PermissionState.Granted
            updateState(Permission.LocationAlways, granted)
            return granted
        }

        if (isMissingFromManifest(listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))) {
            val error = PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration)
            updateState(Permission.LocationAlways, error)
            return error
        }

        if (!hasHostActivity()) {
            val error = PermissionState.ConfigurationError(ConfigurationErrorReason.NoHostActivity)
            updateState(Permission.LocationAlways, error)
            return error
        }

        val hadRequestedBefore = isRequested(Permission.LocationAlways)
        markRequested(Permission.LocationAlways)

        return suspendCancellableCoroutine { continuation ->
            multiRequestFlow.tryEmit(
                MultiRequest(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) { results ->
                    val state = if (results[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true) {
                        PermissionState.Granted
                    } else {
                        resolveDeniedState(
                            listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                            hadRequestedBefore
                        )
                    }
                    // Published here rather than after the suspend point so a cancelled caller
                    // can't strand the StateFlow on a stale value (same rule as
                    // requestRuntimePermission).
                    updateState(Permission.LocationAlways, state)
                    continuation.resume(state)
                }
            )
        }
    }

    private suspend fun requestBodySensorsBackground(): PermissionState {
        // Same staging rule as background location: BODY_SENSORS_BACKGROUND is meaningless (and
        // silently ignored by the OS) until foreground BodySensors access is already granted.
        val foregroundState = requestRuntimePermission(Permission.BodySensors)
        if (foregroundState != PermissionState.Granted) {
            updateState(Permission.BodySensorsBackground, foregroundState)
            return foregroundState
        }

        if (Build.VERSION.SDK_INT < 33) {
            // Below Android 13 there is no separate background-sensors permission: foreground
            // access already implies background access.
            val granted = PermissionState.Granted
            updateState(Permission.BodySensorsBackground, granted)
            return granted
        }

        if (isGranted(Manifest.permission.BODY_SENSORS_BACKGROUND)) {
            val granted = PermissionState.Granted
            updateState(Permission.BodySensorsBackground, granted)
            return granted
        }

        if (isMissingFromManifest(listOf(Manifest.permission.BODY_SENSORS_BACKGROUND))) {
            val error = PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration)
            updateState(Permission.BodySensorsBackground, error)
            return error
        }

        if (!hasHostActivity()) {
            val error = PermissionState.ConfigurationError(ConfigurationErrorReason.NoHostActivity)
            updateState(Permission.BodySensorsBackground, error)
            return error
        }

        val hadRequestedBefore = isRequested(Permission.BodySensorsBackground)
        markRequested(Permission.BodySensorsBackground)

        return suspendCancellableCoroutine { continuation ->
            multiRequestFlow.tryEmit(
                MultiRequest(arrayOf(Manifest.permission.BODY_SENSORS_BACKGROUND)) { results ->
                    val state = if (results[Manifest.permission.BODY_SENSORS_BACKGROUND] == true) {
                        PermissionState.Granted
                    } else {
                        resolveDeniedState(
                            listOf(Manifest.permission.BODY_SENSORS_BACKGROUND),
                            hadRequestedBefore
                        )
                    }
                    // Same rule as requestRuntimePermission: publish before resuming so a
                    // cancelled caller can't strand the StateFlow on a stale value.
                    updateState(Permission.BodySensorsBackground, state)
                    continuation.resume(state)
                }
            )
        }
    }

    private fun resolveState(
        permission: Permission.Runtime,
        results: Map<String, Boolean>,
        hadRequestedBefore: Boolean
    ): PermissionState {
        val manifestPermissions = permission.toManifestPermissions()
        val canShowRationale = canShowRationaleFor(manifestPermissions)
        return when (permission) {
            Permission.PhotoLibrary ->
                resolvePhotoLibraryGrantResult(Build.VERSION.SDK_INT, results, hadRequestedBefore, canShowRationale)
            Permission.LocationWhileInUse ->
                resolveForegroundLocationGrantResult(results, hadRequestedBefore, canShowRationale)
            else -> resolveGrantResult(manifestPermissions, results, hadRequestedBefore, canShowRationale)
        }
    }

    // shouldShowRequestPermissionRationale is an Activity API and only meaningful with one
    // attached; the actual Denied-vs-PermanentlyDenied decision logic lives in
    // resolveDeniedStateFrom (AndroidPermissionMapping.kt) as a pure, directly-testable function.
    private fun canShowRationaleFor(manifestPermissions: List<String>): Boolean {
        val activity = activityRef?.get()
        return activity != null && manifestPermissions.any { mp ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, mp)
        }
    }

    private fun resolveDeniedState(
        manifestPermissions: List<String>,
        hadRequestedBefore: Boolean
    ): PermissionState = resolveDeniedStateFrom(canShowRationaleFor(manifestPermissions), hadRequestedBefore)

    override fun refreshAll() {
        // toList() snapshots the keys so re-entrant state()/getOrPut calls triggered by the
        // updateState below (there are none today, but future permissions might) can't cause a
        // ConcurrentModificationException while this loop is iterating.
        states.keys.toList().forEach { permission ->
            updateState(permission, checkState(permission))
        }
    }

    // getOrPut, not a plain lookup: request()/requestBackgroundLocation()/etc. can resolve before
    // state() has ever been called for this permission (e.g. a fire-and-forget request() with no
    // observer yet), in which case `states` has no entry -- a plain `states[permission]?.value = ..`
    // would silently drop the result, and the next state() call would recompute from checkState()
    // instead, losing outcomes checkState() can't re-derive on its own (most notably
    // ConfigurationError, which no live device query can reconstruct after the fact).
    private fun updateState(permission: Permission, state: PermissionState) {
        states.getOrPut(permission) { MutableStateFlow(state) }.value = state
    }

    private fun checkState(permission: Permission): PermissionState {
        // Surface a missing <uses-permission> from state() too, not just on request() -- a
        // PermissionGate (or any observer) then reports the integration mistake immediately on
        // first composition instead of masquerading as NotDetermined until the first tap. Skipped
        // when everything needed is already granted (e.g. granted via adb in a test build).
        if (permission is Permission.Runtime) {
            val required = permission.requiredManifestDeclarations()
            if (required.isNotEmpty() && !required.all { isGranted(it) } && isMissingFromManifest(required)) {
                return PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration)
            }
        }
        return checkStateDispatch(permission)
    }

    private fun checkStateDispatch(permission: Permission): PermissionState = when (permission) {
        Permission.SystemAlertWindow -> checkSystemAlertWindowState()
        Permission.ExactAlarm -> checkExactAlarmState()
        Permission.IgnoreBatteryOptimizations -> checkIgnoreBatteryOptimizationsState()
        Permission.WriteSettings -> checkWriteSettingsState()
        Permission.ManageExternalStorage -> checkManageExternalStorageState()
        Permission.DoNotDisturbAccess -> checkDoNotDisturbAccessState()
        Permission.UsageAccess -> checkUsageAccessState()
        Permission.NotificationListenerAccess -> checkNotificationListenerAccessState()
        // No Android equivalent exists; AppTrackingTransparency/SpeechRecognition/Reminders also
        // land here via the generic runtime-permission path below since their manifest-permission
        // lists are empty.
        Permission.LocalNetwork -> PermissionState.Granted
        Permission.PhotoLibrary -> checkPhotoLibraryState()
        Permission.LocationWhileInUse -> checkLocationWhileInUseState()
        Permission.LocationAlways -> checkLocationAlwaysState()
        Permission.BodySensorsBackground -> checkBodySensorsBackgroundState()
        else -> checkRuntimePermissionState(permission)
    }

    // Every Special permission's Settings surface filters on a manifest declaration: the app
    // simply never appears in the target list (or the request intent is rejected) without it.
    // Each check below therefore runs granted-first -- a grant that exists anyway (adb, OEM
    // preload) is still reported truthfully -- then declaration, then the normal denied state.
    private fun checkSystemAlertWindowState(): PermissionState {
        if (Settings.canDrawOverlays(context)) return PermissionState.Granted
        if (isMissingFromManifest(listOf(Manifest.permission.SYSTEM_ALERT_WINDOW))) {
            return PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration)
        }
        return PermissionState.Denied(canRequestAgain = true)
    }

    private fun checkExactAlarmState(): PermissionState {
        if (Build.VERSION.SDK_INT < 31) return PermissionState.Granted
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (alarmManager?.canScheduleExactAlarms() == true) return PermissionState.Granted
        // Either declaration works: SCHEDULE_EXACT_ALARM (user-revocable) or USE_EXACT_ALARM
        // (API 33+, alarm-clock category apps, granted unconditionally).
        val declared = declaredPermissions
        if (declared != null &&
            Manifest.permission.SCHEDULE_EXACT_ALARM !in declared &&
            "android.permission.USE_EXACT_ALARM" !in declared
        ) {
            return PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration)
        }
        return PermissionState.Denied(canRequestAgain = true)
    }

    private fun checkIgnoreBatteryOptimizationsState(): PermissionState {
        val powerManager = context.getSystemService(PowerManager::class.java)
        if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true) {
            return PermissionState.Granted
        }
        // Without this declaration the ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS dialog is
        // rejected by the system with a SecurityException-shaped no-op.
        if (isMissingFromManifest(listOf(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))) {
            return PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration)
        }
        return PermissionState.Denied(canRequestAgain = true)
    }

    private fun checkWriteSettingsState(): PermissionState {
        if (Settings.System.canWrite(context)) return PermissionState.Granted
        if (isMissingFromManifest(listOf(Manifest.permission.WRITE_SETTINGS))) {
            return PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration)
        }
        return PermissionState.Denied(canRequestAgain = true)
    }

    private fun checkManageExternalStorageState(): PermissionState {
        // MANAGE_EXTERNAL_STORAGE (the scoped-storage opt-out) didn't exist before Android 11;
        // apps relied on READ/WRITE_EXTERNAL_STORAGE instead, so there's nothing to gate on.
        if (Build.VERSION.SDK_INT < 30) return PermissionState.Granted
        if (android.os.Environment.isExternalStorageManager()) return PermissionState.Granted
        if (isMissingFromManifest(listOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE))) {
            return PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration)
        }
        return PermissionState.Denied(canRequestAgain = true)
    }

    private fun checkDoNotDisturbAccessState(): PermissionState {
        val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
        if (notificationManager?.isNotificationPolicyAccessGranted == true) return PermissionState.Granted
        // The DND-access Settings screen is a list of apps that declare ACCESS_NOTIFICATION_POLICY;
        // without the declaration this app never appears there, so the user has no way to grant it
        // -- that's an integration mistake, not a denial.
        if (isMissingFromManifest(listOf(Manifest.permission.ACCESS_NOTIFICATION_POLICY))) {
            return PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration)
        }
        return PermissionState.Denied(canRequestAgain = true)
    }

    @Suppress("DEPRECATION")
    private fun checkUsageAccessState(): PermissionState {
        val appOps = context.getSystemService(android.app.AppOpsManager::class.java)
        val mode = appOps?.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        if (mode == android.app.AppOpsManager.MODE_ALLOWED) return PermissionState.Granted
        // The usage-access Settings list filters on apps declaring PACKAGE_USAGE_STATS.
        if (isMissingFromManifest(listOf(Manifest.permission.PACKAGE_USAGE_STATS))) {
            return PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration)
        }
        return PermissionState.Denied(canRequestAgain = true)
    }

    private fun checkNotificationListenerAccessState(): PermissionState {
        // The notification-listener Settings screen only lists apps that declare a
        // NotificationListenerService; without one this app can never be granted access there.
        if (!hasNotificationListenerService()) {
            return PermissionState.ConfigurationError(ConfigurationErrorReason.MissingManifestDeclaration)
        }
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
        return if (enabled) {
            PermissionState.Granted
        } else {
            PermissionState.Denied(canRequestAgain = true)
        }
    }

    private fun checkBodySensorsBackgroundState(): PermissionState {
        if (!isGranted(Manifest.permission.BODY_SENSORS)) {
            return if (!isRequested(Permission.BodySensors)) {
                PermissionState.NotDetermined
            } else {
                resolveDeniedState(
                    listOf(Manifest.permission.BODY_SENSORS),
                    hadRequestedBefore = true
                )
            }
        }

        if (Build.VERSION.SDK_INT < 33) return PermissionState.Granted

        if (isGranted(Manifest.permission.BODY_SENSORS_BACKGROUND)) return PermissionState.Granted
        if (!isRequested(Permission.BodySensorsBackground)) return PermissionState.NotDetermined
        return resolveDeniedState(
            listOf(Manifest.permission.BODY_SENSORS_BACKGROUND),
            hadRequestedBefore = true
        )
    }

    private fun checkLocationAlwaysState(): PermissionState {
        val foregroundGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (!foregroundGranted) {
            return if (!isRequested(Permission.LocationWhileInUse)) {
                PermissionState.NotDetermined
            } else {
                resolveDeniedState(
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    hadRequestedBefore = true
                )
            }
        }

        if (Build.VERSION.SDK_INT < 29) return PermissionState.Granted

        if (isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) return PermissionState.Granted
        if (!isRequested(Permission.LocationAlways)) return PermissionState.NotDetermined
        return resolveDeniedState(
            listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            hadRequestedBefore = true
        )
    }

    private fun checkLocationWhileInUseState(): PermissionState {
        val fineGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseGranted = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        return when {
            fineGranted -> PermissionState.Granted
            coarseGranted -> PermissionState.Limited(LimitedReason.ApproximateLocationOnly)
            !isRequested(Permission.LocationWhileInUse) -> PermissionState.NotDetermined
            else -> resolveDeniedState(
                listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                hadRequestedBefore = true
            )
        }
    }

    private fun checkPhotoLibraryState(): PermissionState = when {
        Build.VERSION.SDK_INT >= 34 -> {
            val fullGranted = isGranted(Manifest.permission.READ_MEDIA_IMAGES) &&
                isGranted(Manifest.permission.READ_MEDIA_VIDEO)
            val partialGranted = isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            when {
                fullGranted -> PermissionState.Granted
                partialGranted -> PermissionState.Limited(LimitedReason.PartialMediaAccess)
                !isRequested(Permission.PhotoLibrary) -> PermissionState.NotDetermined
                else -> resolveDeniedState(
                    listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO),
                    hadRequestedBefore = true
                )
            }
        }
        Build.VERSION.SDK_INT == 33 -> {
            val granted = isGranted(Manifest.permission.READ_MEDIA_IMAGES) &&
                isGranted(Manifest.permission.READ_MEDIA_VIDEO)
            when {
                granted -> PermissionState.Granted
                !isRequested(Permission.PhotoLibrary) -> PermissionState.NotDetermined
                else -> resolveDeniedState(
                    listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO),
                    hadRequestedBefore = true
                )
            }
        }
        else -> {
            val granted = isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
            when {
                granted -> PermissionState.Granted
                !isRequested(Permission.PhotoLibrary) -> PermissionState.NotDetermined
                else -> resolveDeniedState(
                    listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    hadRequestedBefore = true
                )
            }
        }
    }

    private fun checkRuntimePermissionState(permission: Permission): PermissionState {
        val manifestPermissions = (permission as? Permission.Runtime)?.toManifestPermissions() ?: return PermissionState.Granted
        if (manifestPermissions.isEmpty()) return PermissionState.Granted

        val allGranted = manifestPermissions.all { isGranted(it) }
        if (allGranted) return PermissionState.Granted

        if (!isRequested(permission)) return PermissionState.NotDetermined
        return resolveDeniedState(manifestPermissions, hadRequestedBefore = true)
    }

    private fun hasNotificationListenerService(): Boolean {
        val intent = Intent("android.service.notification.NotificationListenerService")
            .setPackage(context.packageName)
        return context.packageManager.queryIntentServices(intent, 0).isNotEmpty()
    }

    private fun isGranted(manifestPermission: String): Boolean =
        ContextCompat.checkSelfPermission(context, manifestPermission) == PackageManager.PERMISSION_GRANTED

    private fun isRequested(permission: Permission): Boolean =
        prefs.getBoolean(requestedKey(permission), false)

    private fun markRequested(permission: Permission) {
        prefs.putBoolean(requestedKey(permission), true)
    }

    private fun requestedKey(permission: Permission): String = "requested_${permission.persistenceKey()}"

    private companion object {
        const val PREFS_NAME = "io.github.alirezajavan.permpilot.state"
    }
}

class MultiRequest(
    val permissions: Array<String>,
    val onResult: (Map<String, Boolean>) -> Unit
)
