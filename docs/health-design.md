# Health & Fitness Permission Design

Health permissions (Health Connect on Android, HealthKit on iOS) differ from standard runtime permissions because they are granular per data type.

## Proposed API

```kotlin
data class Health(
    val dataTypes: Set<HealthDataType>,
    val access: HealthAccess = HealthAccess.Read
) : Permission.Runtime

enum class HealthDataType {
    Steps,
    HeartRate,
    Sleep,
    ActiveEnergy,
    DistanceWalkingRunning,
    BodyMass,
    Height,
}

enum class HealthAccess { Read, Write, ReadWrite }
```

## Platform Mapping

### Android (Health Connect)
- Uses `androidx.health.connect.client.PermissionController`.
- Requires a separate Activity Result Contract provided by the Health Connect SDK.
- Permission strings: `android.permission.health.READ_STEPS`, `android.permission.health.WRITE_STEPS`, etc.
- If Health Connect is not installed, it should report `ConfigurationError(HealthApiUnavailable)`.

### iOS (HealthKit)
- Uses `HKHealthStore.requestAuthorization(toShare:read:)`.
- Requires `NSHealthShareUsageDescription` and `NSHealthUpdateUsageDescription` in `Info.plist`.
- Requires the HealthKit entitlement (consumer must set this in Xcode).
- **Privacy Design**: iOS returns `NotDetermined` for read access even after a user decision. PermPilot will report this as a documented behavior/limitation.

## Implementation Details

1. **Permission.kt**: Add `Health`, `HealthDataType`, and `HealthAccess`.
2. **ConfigurationErrorReason.kt**: Add `HealthApiUnavailable`.
3. **AndroidPermissionController**: Specialize the `request` flow to detect `Health` and use the Health Connect client.
4. **IosPermissionController**: Implement `HKHealthStore` orchestration.
