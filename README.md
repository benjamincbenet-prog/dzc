# DZC Scale Health

DZC Scale Health is an Android application for the Bear Electric Appliance (Xiong) DZC-D18E3 Bluetooth Low Energy smart scale. It collects weight and the scale's final foot-to-foot bioelectrical impedance result, estimates body-composition metrics using a documented formula, and can write supported results to Health Connect.

## Highlights
- DZC-D18E3 BLE discovery by `DZC` device-name prefix.
- GATT service `FFF0` and notification characteristic `FFF4`.
- 11-byte packet validation with `0xCF` header and XOR checksum.
- Separate live-weight and final BIA packet handling.
- Persistent profile: name, height, age, sex, and optional waist circumference.
- Persistent manual/automatic Health Connect sync preference.
- Estimated BMI, transparent body-fat consensus, fat mass, lean mass, body water, and BMR.
- Measurement quality diagnostics based on packet collection and stability.

## Documentation
See the `docs/` directory:
- `USER_GUIDE.md` — using the app.
- `TECHNICAL_ARCHITECTURE.md` — project architecture and data flow.
- `BLE_PROTOCOL.md` — observed DZC-D18E3 protocol.
- `BODY_COMPOSITION.md` — formulas, assumptions, and limitations.
- `HEALTH_CONNECT.md` — permissions and sync behavior.
- `CODE_GUIDE.md` — source-code map and maintenance guide.
- `BUILDING.md` — CodeOnTheGo/Gradle build notes.

## Important measurement note
Weight and impedance are measurements received from the scale. Body composition, body water, and BMR are estimates. They should be interpreted as trend-oriented estimates rather than clinical diagnoses.

## App Icon

The launcher icon uses the polished DZC Scale Health branding artwork added in this build. Android receives density-specific launcher assets for mdpi through xxxhdpi, with both standard and round icon manifest entries.


## Version 1.6 UI polish

- Added explicit edge-to-edge system-bar inset handling on the dashboard and Profile screen.
- Replaced the text-only Profile action with a circular initials avatar.
- Simplified the dashboard greeting; age, height, and sex remain on the Profile screen.
- Added consistent overlines to the Scale, Latest Measurement, and Health Connect cards.
- Increased card/action spacing and improved result/footer readability.
- Improved the disabled primary-action treatment so it remains visually distinct.
- Rebuilt the round launcher icon as a true circular scale/health mark instead of placing the square icon inside a launcher circle.

## Version 1.7 waist-enhanced estimation

- Added persistent optional waist circumference to the user profile.
- Added waist-to-height ratio and RFM anthropometric cross-check.
- Added transparent 60% foot-to-foot BIA + 40% waist RFM consensus when waist is available.
- The dashboard continues to expose component estimates while Health Connect receives one final body-fat value.
- Added in-app guidance for repeatable waist measurement technique.


## v1.8 Smart Start
- NFC tags can launch `dzcscale://measure` measurement sessions.
- Android Quick Settings tile starts a measurement session.
- Completed measurements are persisted locally in Measurement History.
