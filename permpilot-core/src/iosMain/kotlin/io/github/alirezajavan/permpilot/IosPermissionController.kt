package io.github.alirezajavan.permpilot

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.AppTrackingTransparency.ATTrackingManager
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreMotion.CMMotionActivityManager
import platform.EventKit.EKEntityType
import platform.EventKit.EKEventStore
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.HealthKit.HKAuthorizationStatusSharingAuthorized
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKObjectType
import platform.HealthKit.HKQuantityType
import platform.HealthKit.HKQuantityTypeIdentifierActiveEnergyBurned
import platform.HealthKit.HKQuantityTypeIdentifierBodyMass
import platform.HealthKit.HKQuantityTypeIdentifierDistanceWalkingRunning
import platform.HealthKit.HKQuantityTypeIdentifierHeartRate
import platform.HealthKit.HKQuantityTypeIdentifierHeight
import platform.HealthKit.HKQuantityTypeIdentifierStepCount
import platform.MediaPlayer.MPMediaLibrary
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAuthorizationStatus
import platform.Photos.PHPhotoLibrary
import platform.Speech.SFSpeechRecognizer
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

fun PermissionController.Companion.create(): PermissionController = IosPermissionController()

class IosPermissionController : PermissionController {
    private val states = mutableMapOf<Permission, MutableStateFlow<PermissionState>>()

    private val _events = MutableSharedFlow<PermissionEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<PermissionEvent> = _events.asSharedFlow()

    // CLLocationManager/CBCentralManager report results through a delegate, not a completion
    // handler, so the manager and its in-flight continuation both need to be held as instance
    // state for the lifetime of the request.
    private val locationManager = CLLocationManager()
    private var locationContinuation: CancellableContinuation<Unit>? = null
    private val locationDelegate =
        object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                resumeLocationContinuation()
            }
        }

    private var bluetoothCentralManager: CBCentralManager? = null
    private var bluetoothContinuation: CancellableContinuation<Unit>? = null
    private val bluetoothDelegate =
        object : NSObject(), CBCentralManagerDelegateProtocol {
            override fun centralManagerDidUpdateState(central: CBCentralManager) {
                runOnMain {
                    // Published from the delegate itself, not just the awaiting caller: if that
                    // caller was cancelled while the system alert was up, its resume below is a
                    // no-op, but the StateFlow must still receive the real outcome (same rule as
                    // the Android controller's launcher callback).
                    val status = mapBluetoothAuthorization(CBManager.authorization)
                    updateState(Permission.BluetoothScan, status)
                    updateState(Permission.BluetoothConnect, status)
                    updateState(Permission.BluetoothAdvertise, status)
                }
                bluetoothContinuation?.let {
                    bluetoothContinuation = null
                    runOnMain { it.resume(Unit) }
                }
            }
        }

    init {
        locationManager.delegate = locationDelegate
    }

    // Serializes every request()/requestAll() call through this controller. Without it, a second
    // in-flight request for a CLLocationManager/CBCentralManager-backed permission
    // (LocationWhileInUse, LocationAlways, BluetoothScan) overwrites locationContinuation /
    // bluetoothContinuation before the first call's delegate callback ever fires, so the first
    // caller's CancellableContinuation is never resumed and hangs forever (see CLAUDE.md's
    // concurrency-hardening note). A Mutex
    // is sufficient because iOS can only show one system permission alert at a time regardless.
    private val requestMutex = Mutex()

    override fun state(permission: Permission): StateFlow<PermissionState> =
        states
            .getOrPut(permission) {
                MutableStateFlow(checkState(permission))
            }.asStateFlow()

    override suspend fun request(permission: Permission.Runtime): PermissionState =
        requestMutex.withLock {
            requestLocked(permission)
        }

    // Callable only while requestMutex is already held -- requestAll() below needs to drive
    // several of these under a single lock acquisition, and Mutex isn't reentrant.
    private suspend fun requestLocked(permission: Permission.Runtime): PermissionState {
        if (!hasRequiredUsageDescriptions(permission)) {
            val error = PermissionState.ConfigurationError(ConfigurationErrorReason.MissingUsageDescription)
            updateState(permission, error)
            return error
        }

        _events.tryEmit(PermissionEvent.RequestStarted(permission))
        val newState =
            when (permission) {
                Permission.Camera -> requestAVMediaAccess(AVMediaTypeVideo)
                Permission.Microphone -> requestAVMediaAccess(AVMediaTypeAudio)
                Permission.Contacts -> requestContactsAccess()
                is Permission.Calendar -> requestCalendarAccess(permission.access)
                Permission.PhotoLibrary -> requestPhotoLibraryAccess()
                Permission.LocationWhileInUse -> requestForegroundLocation()
                Permission.LocationAlways -> requestBackgroundLocation()
                Permission.Notifications -> requestNotificationsAccess()
                Permission.BluetoothScan,
                Permission.BluetoothConnect,
                Permission.BluetoothAdvertise,
                -> requestBluetoothAccess()
                Permission.AppTrackingTransparency -> requestTrackingAuthorization()
                // Same CNContactStore authorization as Contacts -- iOS doesn't distinguish read/write.
                Permission.WriteContacts -> requestContactsAccess()
                Permission.ActivityRecognition -> requestActivityRecognitionAccess()
                Permission.AudioFiles -> requestAudioFilesAccess()
                Permission.SpeechRecognition -> requestSpeechRecognitionAccess()
                Permission.Reminders -> requestRemindersAccess()
                is Permission.Health -> requestHealthAccess(permission)
                Permission.MediaLocation -> PermissionState.Granted
                // Android-only concepts: no iOS equivalent exists, so requesting them is a no-op.
                Permission.NearbyWifiDevices, Permission.BodySensors, Permission.BodySensorsBackground,
                Permission.CallPhone, Permission.ReadPhoneState, Permission.ReadPhoneNumbers, Permission.AnswerPhoneCalls,
                Permission.ReadCallLog, Permission.WriteCallLog,
                Permission.SendSms, Permission.ReadSms, Permission.ReceiveSms,
                -> PermissionState.Granted
            }
        updateState(permission, newState)
        _events.tryEmit(PermissionEvent.RequestResult(permission, newState))
        return newState
    }

    override suspend fun requestAll(vararg permissions: Permission.Runtime): Map<Permission, PermissionState> =
        requestMutex.withLock {
            // iOS can only show one system permission alert at a time; requests are serialized
            // here so callers never have to worry about that platform constraint themselves.
            permissions.associate { (it as Permission) to requestLocked(it) }
        }

    override fun openAppSettings() {
        val settingsUrl = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
        if (settingsUrl != null) {
            UIApplication.sharedApplication.openURL(settingsUrl)
        }
    }

    override fun openAppSettings(special: Permission.Special) {
        // iOS exposes exactly one generic settings URL -- there is no per-permission deep link,
        // unlike Android's dedicated Settings intents.
        openAppSettings()
    }

    // --- Camera / Microphone -------------------------------------------------------------

    private suspend fun requestAVMediaAccess(mediaType: String?): PermissionState =
        suspendCancellableCoroutine { continuation ->
            AVCaptureDevice.requestAccessForMediaType(requireNotNull(mediaType)) { granted ->
                runOnMain {
                    // AVFoundation's prompt is a genuine one-shot: once denied, requesting again
                    // just re-invokes the handler with granted=false without showing UI.
                    continuation.resume(if (granted) PermissionState.Granted else PermissionState.PermanentlyDenied)
                }
            }
        }

    // --- Contacts --------------------------------------------------------------------------

    private suspend fun requestContactsAccess(): PermissionState =
        suspendCancellableCoroutine { continuation ->
            CNContactStore().requestAccessForEntityType(CNEntityType.CNEntityTypeContacts) { granted, _ ->
                runOnMain {
                    continuation.resume(if (granted) PermissionState.Granted else PermissionState.PermanentlyDenied)
                }
            }
        }

    // --- Calendar ----------------------------------------------------------------------------
    // iOS 17 splits calendar access into full (read+write) and write-only tiers; pre-17 there is
    // only a single all-or-nothing grant, so a WriteOnly request there falls back to full access.

    private suspend fun requestCalendarAccess(access: CalendarAccess): PermissionState {
        if (!isAtLeastIOS(17)) {
            return requestLegacyCalendarAccess()
        }
        val eventStore = EKEventStore()
        return suspendCancellableCoroutine { continuation ->
            val completion: (Boolean, NSError?) -> Unit = { granted, _ ->
                runOnMain {
                    continuation.resume(if (granted) PermissionState.Granted else PermissionState.PermanentlyDenied)
                }
            }
            when (access) {
                CalendarAccess.Full -> eventStore.requestFullAccessToEventsWithCompletion(completion)
                CalendarAccess.WriteOnly -> eventStore.requestWriteOnlyAccessToEventsWithCompletion(completion)
            }
        }
    }

    private suspend fun requestLegacyCalendarAccess(): PermissionState =
        suspendCancellableCoroutine { continuation ->
            EKEventStore().requestAccessToEntityType(EKEntityType.EKEntityTypeEvent) { granted, _ ->
                runOnMain {
                    continuation.resume(if (granted) PermissionState.Granted else PermissionState.PermanentlyDenied)
                }
            }
        }

    // --- Photo library -----------------------------------------------------------------------

    private suspend fun requestPhotoLibraryAccess(): PermissionState {
        if (!isAtLeastIOS(14)) {
            return requestLegacyPhotoLibraryAccess()
        }
        return suspendCancellableCoroutine { continuation ->
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { status ->
                runOnMain { continuation.resume(mapPhotoLibraryStatus(status)) }
            }
        }
    }

    private suspend fun requestLegacyPhotoLibraryAccess(): PermissionState =
        suspendCancellableCoroutine { continuation ->
            @Suppress("DEPRECATION")
            PHPhotoLibrary.requestAuthorization { status ->
                runOnMain { continuation.resume(mapPhotoLibraryStatus(status)) }
            }
        }

    // --- Location ----------------------------------------------------------------------------
    // requestAlwaysAuthorization() only has any effect once when-in-use access has already been
    // granted -- Apple's docs are explicit that calling it beforehand is a silent no-op. The two
    // calls also always surface as two separate system prompts; they can never be merged.

    private suspend fun requestForegroundLocation(): PermissionState {
        val current = locationManager.authorizationStatus
        if (current != kCLAuthorizationStatusNotDetermined) {
            return mapLocationStatus(current, requestedAlways = false, locationManager.accuracyAuthorization)
        }
        awaitLocationAuthorizationChange { locationManager.requestWhenInUseAuthorization() }
        return mapLocationStatus(locationManager.authorizationStatus, requestedAlways = false, locationManager.accuracyAuthorization)
    }

    private suspend fun requestBackgroundLocation(): PermissionState {
        val foreground = requestForegroundLocation()
        // Reduced accuracy (Limited) still authorizes the app for when-in-use access -- the
        // Always upgrade prompt is independent of accuracy, so only a real denial should bail out.
        if (foreground != PermissionState.Granted && foreground !is PermissionState.Limited) return foreground

        if (locationManager.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways) {
            return mapLocationStatus(locationManager.authorizationStatus, requestedAlways = true, locationManager.accuracyAuthorization)
        }

        awaitLocationAuthorizationChange { locationManager.requestAlwaysAuthorization() }
        return mapLocationStatus(locationManager.authorizationStatus, requestedAlways = true, locationManager.accuracyAuthorization)
    }

    private suspend fun awaitLocationAuthorizationChange(request: () -> Unit) {
        suspendCancellableCoroutine { continuation ->
            locationContinuation = continuation
            request()
        }
    }

    private fun resumeLocationContinuation() {
        runOnMain {
            // Published from the delegate callback itself so a caller cancelled while the system
            // alert was up can't strand either location StateFlow on a stale value -- the mapping
            // reads the live authorization status, so both tiers are safe to recompute here.
            updateState(
                Permission.LocationWhileInUse,
                mapLocationStatus(locationManager.authorizationStatus, requestedAlways = false, locationManager.accuracyAuthorization),
            )
            updateState(
                Permission.LocationAlways,
                mapLocationStatus(locationManager.authorizationStatus, requestedAlways = true, locationManager.accuracyAuthorization),
            )
        }
        locationContinuation?.let {
            locationContinuation = null
            runOnMain { it.resume(Unit) }
        }
    }

    // --- Notifications -----------------------------------------------------------------------
    // UNUserNotificationCenter has no synchronous status getter, only an async settings fetch,
    // so checkState() returns a placeholder immediately and refreshes the real value once the
    // completion handler fires.

    private suspend fun requestNotificationsAccess(): PermissionState =
        suspendCancellableCoroutine { continuation ->
            val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(options) { granted, _ ->
                runOnMain {
                    continuation.resume(if (granted) PermissionState.Granted else PermissionState.PermanentlyDenied)
                }
            }
        }

    private fun refreshNotificationsState() {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus ?: UNAuthorizationStatusNotDetermined
            runOnMain { updateState(Permission.Notifications, mapNotificationStatus(status)) }
        }
    }

    // --- Bluetooth ---------------------------------------------------------------------------
    // There is no explicit "request" call for Bluetooth on iOS: instantiating a CBCentralManager
    // is itself what triggers the one-time system prompt the first time it happens.

    private suspend fun requestBluetoothAccess(): PermissionState {
        val current = CBManager.authorization
        if (current != CBManagerAuthorizationNotDetermined) {
            return mapBluetoothAuthorization(current)
        }
        suspendCancellableCoroutine { continuation ->
            bluetoothContinuation = continuation
            bluetoothCentralManager = CBCentralManager(delegate = bluetoothDelegate, queue = null)
        }
        return mapBluetoothAuthorization(CBManager.authorization)
    }

    // --- App Tracking Transparency -------------------------------------------------------------

    private suspend fun requestTrackingAuthorization(): PermissionState {
        if (!isAtLeastIOS(14)) return PermissionState.Granted
        return suspendCancellableCoroutine { continuation ->
            ATTrackingManager.requestTrackingAuthorizationWithCompletionHandler { status ->
                runOnMain { continuation.resume(mapTrackingStatus(status)) }
            }
        }
    }

    // --- Activity Recognition (Core Motion) -----------------------------------------------------
    // CMMotionActivityManager has no explicit "request" call either -- issuing a historical query
    // is what triggers the one-time system prompt the first time it happens.

    private val motionActivityManager = CMMotionActivityManager()

    private suspend fun requestActivityRecognitionAccess(): PermissionState =
        suspendCancellableCoroutine { continuation ->
            val now = NSDate()
            motionActivityManager.queryActivityStartingFromDate(now, toDate = now, toQueue = NSOperationQueue.mainQueue()) { _, _ ->
                runOnMain {
                    continuation.resume(mapActivityRecognitionStatus(CMMotionActivityManager.authorizationStatus()))
                }
            }
        }

    // --- Audio files (Apple Music library) --------------------------------------------------

    private suspend fun requestAudioFilesAccess(): PermissionState =
        suspendCancellableCoroutine { continuation ->
            MPMediaLibrary.requestAuthorization { status ->
                runOnMain { continuation.resume(mapAudioFilesStatus(status)) }
            }
        }

    // --- Speech recognition -----------------------------------------------------------------

    private suspend fun requestSpeechRecognitionAccess(): PermissionState =
        suspendCancellableCoroutine { continuation ->
            SFSpeechRecognizer.requestAuthorization { status ->
                runOnMain { continuation.resume(mapSpeechRecognitionStatus(status)) }
            }
        }

    // --- Reminders ---------------------------------------------------------------------------
    // A distinct EKEventStore authorization from Calendar's, despite sharing the same framework
    // and EKAuthorizationStatus enum.

    private suspend fun requestRemindersAccess(): PermissionState {
        if (!isAtLeastIOS(17)) {
            return requestLegacyRemindersAccess()
        }
        return suspendCancellableCoroutine { continuation ->
            EKEventStore().requestFullAccessToRemindersWithCompletion { granted, _ ->
                runOnMain {
                    continuation.resume(if (granted) PermissionState.Granted else PermissionState.PermanentlyDenied)
                }
            }
        }
    }

    private suspend fun requestLegacyRemindersAccess(): PermissionState =
        suspendCancellableCoroutine { continuation ->
            EKEventStore().requestAccessToEntityType(EKEntityType.EKEntityTypeReminder) { granted, _ ->
                runOnMain {
                    continuation.resume(if (granted) PermissionState.Granted else PermissionState.PermanentlyDenied)
                }
            }
        }

    // --- Health -----------------------------------------------------------------------------

    private val healthStore = HKHealthStore()

    private suspend fun requestHealthAccess(permission: Permission.Health): PermissionState {
        if (!HKHealthStore.isHealthDataAvailable()) {
            return PermissionState.ConfigurationError(ConfigurationErrorReason.HealthApiUnavailable)
        }

        val types = permission.dataTypes.map { it.toHKObjectType() }.toSet()
        return suspendCancellableCoroutine { continuation ->
            val completion: (Boolean, NSError?) -> Unit = { success, _ ->
                runOnMain {
                    if (success) {
                        continuation.resume(checkHealthState(permission))
                    } else {
                        continuation.resume(PermissionState.Denied(canRequestAgain = true))
                    }
                }
            }
            when (permission.access) {
                HealthAccess.Read -> healthStore.requestAuthorizationToShareTypes(null, readTypes = types, completion = completion)
                HealthAccess.Write ->
                    healthStore.requestAuthorizationToShareTypes(
                        typesToShare = types,
                        readTypes = null,
                        completion = completion,
                    )
                HealthAccess.ReadWrite ->
                    healthStore.requestAuthorizationToShareTypes(
                        typesToShare = types,
                        readTypes = types,
                        completion = completion,
                    )
            }
        }
    }

    private fun checkHealthState(permission: Permission.Health): PermissionState {
        if (!HKHealthStore.isHealthDataAvailable()) {
            return PermissionState.ConfigurationError(ConfigurationErrorReason.HealthApiUnavailable)
        }

        val types = permission.dataTypes.map { it.toHKObjectType() }
        val statuses =
            types.map { type ->
                healthStore.authorizationStatusForType(type)
            }

        return when {
            statuses.all { it == HKAuthorizationStatusSharingAuthorized } -> PermissionState.Granted
            // HealthKit read-only access always returns NotDetermined even after a grant for privacy.
            // We report this as NotDetermined or a custom state if we want to be more specific.
            // For now, if any is NotDetermined, we return NotDetermined.
            statuses.any { it == platform.HealthKit.HKAuthorizationStatusNotDetermined } -> PermissionState.NotDetermined
            else -> PermissionState.PermanentlyDenied
        }
    }

    private fun HealthDataType.toHKObjectType(): HKObjectType =
        when (this) {
            HealthDataType.Steps -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount)!!
            HealthDataType.HeartRate -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeartRate)!!
            HealthDataType.Sleep ->
                platform.HealthKit.HKCategoryType.categoryTypeForIdentifier(
                    platform.HealthKit.HKCategoryTypeIdentifierSleepAnalysis,
                )!!
            HealthDataType.ActiveEnergy -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierActiveEnergyBurned)!!
            HealthDataType.DistanceWalkingRunning ->
                HKQuantityType.quantityTypeForIdentifier(
                    HKQuantityTypeIdentifierDistanceWalkingRunning,
                )!!
            HealthDataType.BodyMass -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyMass)!!
            HealthDataType.Height -> HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeight)!!
        }

    // --- state() dispatch --------------------------------------------------------------------

    override fun refreshAll() {
        // toList() snapshots the keys so this loop is unaffected by the async Notifications
        // refresh (refreshNotificationsState) mutating `states` on the main queue mid-iteration.
        states.keys.toList().forEach { permission ->
            if (permission == Permission.Notifications) {
                // checkState()'s Notifications branch always returns a synchronous NotDetermined
                // placeholder (there is no synchronous status getter) and refreshes the real value
                // asynchronously as a side effect. Routing it through the generic updateState below
                // would flash an already-Granted flow back to NotDetermined on every resume; calling
                // the async refresh directly avoids that self-inflicted flicker.
                refreshNotificationsState()
            } else {
                updateState(permission, checkState(permission))
            }
        }
    }

    // getOrPut, not a plain lookup: request() can resolve (including ConfigurationError, for a
    // missing Info.plist key) before state() has ever been called for this permission, in which
    // case `states` has no entry yet -- a plain lookup-and-set would silently drop the result.
    private fun updateState(
        permission: Permission,
        state: PermissionState,
    ) {
        val flow = states.getOrPut(permission) { MutableStateFlow(state) }
        val oldState = flow.value
        flow.value = state
        if (oldState != state) {
            _events.tryEmit(PermissionEvent.StateChanged(permission, state))
        }
    }

    private fun checkState(permission: Permission): PermissionState {
        if (permission is Permission.Runtime && !hasRequiredUsageDescriptions(permission)) {
            return PermissionState.ConfigurationError(ConfigurationErrorReason.MissingUsageDescription)
        }
        return checkPermissionState(permission)
    }

    private fun checkPermissionState(permission: Permission): PermissionState =
        when (permission) {
            Permission.Camera ->
                mapAVAuthorizationStatus(
                    AVCaptureDevice.authorizationStatusForMediaType(requireNotNull(AVMediaTypeVideo)),
                )
            Permission.Microphone ->
                mapAVAuthorizationStatus(
                    AVCaptureDevice.authorizationStatusForMediaType(requireNotNull(AVMediaTypeAudio)),
                )
            Permission.Contacts ->
                mapContactsAuthorizationStatus(
                    CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts),
                )
            is Permission.Calendar ->
                mapCalendarAuthorizationStatus(
                    EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent),
                )
            Permission.PhotoLibrary -> mapPhotoLibraryStatus(currentPhotoLibraryStatus())
            Permission.LocationWhileInUse ->
                mapLocationStatus(
                    locationManager.authorizationStatus,
                    requestedAlways = false,
                    locationManager.accuracyAuthorization,
                )
            Permission.LocationAlways ->
                mapLocationStatus(
                    locationManager.authorizationStatus,
                    requestedAlways = true,
                    locationManager.accuracyAuthorization,
                )
            is Permission.Health -> checkHealthState(permission)
            Permission.Notifications -> {
                refreshNotificationsState()
                PermissionState.NotDetermined
            }
            Permission.BluetoothScan,
            Permission.BluetoothConnect,
            Permission.BluetoothAdvertise,
            -> mapBluetoothAuthorization(CBManager.authorization)
            Permission.AppTrackingTransparency ->
                if (isAtLeastIOS(14)) {
                    mapTrackingStatus(ATTrackingManager.trackingAuthorizationStatus)
                } else {
                    PermissionState.Granted
                }
            // Same CNContactStore authorization as Contacts -- iOS doesn't distinguish read/write.
            Permission.WriteContacts ->
                mapContactsAuthorizationStatus(
                    CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts),
                )
            Permission.ActivityRecognition -> mapActivityRecognitionStatus(CMMotionActivityManager.authorizationStatus())
            Permission.AudioFiles -> mapAudioFilesStatus(MPMediaLibrary.authorizationStatus())
            Permission.SpeechRecognition -> mapSpeechRecognitionStatus(SFSpeechRecognizer.authorizationStatus())
            Permission.Reminders ->
                mapCalendarAuthorizationStatus(
                    EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeReminder),
                )
            Permission.MediaLocation -> PermissionState.Granted
            // Android-only concepts: no iOS equivalent exists, so these are permanently satisfied.
            Permission.SystemAlertWindow, Permission.ExactAlarm, Permission.FullScreenIntent, Permission.IgnoreBatteryOptimizations,
            Permission.WriteSettings, Permission.ManageExternalStorage,
            Permission.DoNotDisturbAccess, Permission.UsageAccess, Permission.NotificationListenerAccess,
            Permission.NearbyWifiDevices, Permission.BodySensors, Permission.BodySensorsBackground,
            Permission.CallPhone, Permission.ReadPhoneState, Permission.ReadPhoneNumbers, Permission.AnswerPhoneCalls,
            Permission.ReadCallLog, Permission.WriteCallLog,
            Permission.SendSms, Permission.ReadSms, Permission.ReceiveSms,
            -> PermissionState.Granted
            // No public API exists to query Local Network access at all; this is a documented
            // limitation, not a bug -- see docs/permission-matrix.md.
            Permission.LocalNetwork -> PermissionState.Granted
        }

    private fun currentPhotoLibraryStatus(): PHAuthorizationStatus {
        @Suppress("DEPRECATION")
        return if (isAtLeastIOS(14)) {
            PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
        } else {
            PHPhotoLibrary.authorizationStatus()
        }
    }
}

private fun runOnMain(block: () -> Unit) {
    dispatch_async(dispatch_get_main_queue(), block)
}
