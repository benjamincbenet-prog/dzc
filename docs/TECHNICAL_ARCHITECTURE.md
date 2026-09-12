# Technical Architecture

## Modules and responsibilities

### UI
- `MainActivity.java`: measurement workflow, profile summary, result presentation, and Health Connect entry point.
- `SettingsActivity.java`: profile editing and auto-sync preference.
- `HealthPermissionsRationaleActivity.java`: explanation of requested Health Connect permissions.

### BLE
- `DzcBleClient.java`: scan, connect, discover services, subscribe to notifications, collect samples, finalize measurements, and calculate acquisition diagnostics.
- `DzcPacketParser.java`: validates and decodes the 11-byte notification frame.

### Body calculations
- `BodyCompositionCalculator.java`: transforms raw measurement plus profile data into `BodyMetrics`.
- `Measurement.java`: raw/selected measurement and sampling diagnostics.
- `BodyMetrics.java`: measured and calculated output values.
- `Sex.java`: formula input enum.

### Health Connect
- `HealthConnectBridge.kt`: permission checks and asynchronous record insertion.

## End-to-end data flow
`Profile -> MainActivity -> DzcBleClient -> DzcPacketParser -> Measurement -> BodyCompositionCalculator -> BodyMetrics -> HealthConnectBridge`.

BLE callbacks are converted into UI updates on the main thread. Health Connect work is performed with Kotlin coroutines on `Dispatchers.IO` and callbacks are returned to the main thread.

## Persistent storage
`SharedPreferences` file `user_profile` stores name, height, age, sex, and auto-sync preference. The current measurement is intentionally in-memory only.
