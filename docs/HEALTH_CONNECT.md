# Health Connect

## Availability and permissions
The app checks `HealthConnectClient.getSdkStatus()` before syncing. It requests write permissions for:
- Weight
- Body fat percentage
- Lean body mass
- Body water mass
- Basal metabolic rate

## Records
All records share the measurement timestamp and use metadata identifying the device as Bear Electric Appliance / DZC-D18E3.

## Sync modes
- **Manual**: user taps Save to Health Connect.
- **Automatic**: after a completed measurement, the app begins the same save/permission flow automatically.

The app does not read existing Health Connect records. A successful save marks the current in-memory measurement as saved to reduce accidental duplicate writes during that activity session.
