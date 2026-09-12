# Code Guide

## Safe places to make changes
- Protocol decoding: `ble/DzcPacketParser.java`
- Connection and sample lifecycle: `ble/DzcBleClient.java`
- Formula policy: `body/BodyCompositionCalculator.java`
- Persistent settings: `SettingsActivity.java`
- UI workflow: `MainActivity.java`
- Health Connect records: `health/HealthConnectBridge.kt`

## Design principles
1. Keep measured data separate from estimated metrics.
2. Validate protocol frames before they affect a measurement.
3. Treat the final BIA frame as authoritative for body composition.
4. Keep formula version/model names with output values for provenance.
5. Persist profile/settings separately from transient measurement state.
6. Keep Health Connect writing behind a dedicated bridge.

## Adding a new metric
Add it to `BodyMetrics`, calculate it in `BodyCompositionCalculator`, display it in `MainActivity`, and add a Health Connect record only when the target API supports the metric and the permission/write behavior is appropriate.
