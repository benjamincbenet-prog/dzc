# DZC-D18E3 BLE Protocol

This document describes behavior observed from the target scale and encoded by the app.

## GATT
- Device-name filter: prefix `DZC`
- Service: `0000fff0-0000-1000-8000-00805f9b34fb`
- Command characteristic observed: `FFF1` (not required by the current collection path)
- Notification characteristic: `FFF4`
- CCCD: `2902`, written with notifications enabled

## Frame layout
Each `FFF4` notification is expected to contain 11 bytes:

| Byte(s) | Meaning | Decoding |
|---|---|---|
| 0 | Header | `0xCF` |
| 1–2 | Auxiliary/unknown mirrored field | uint16 little-endian |
| 3–4 | Weight | uint16 little-endian / 100 kg |
| 5–6 | Impedance | uint16 little-endian / 10 ohm |
| 7 | Profile ID | uint8 |
| 8 | Device status | uint8, retained but not used as final-state discriminator |
| 9 | Observed mode/class | `0x01` live weight, `0xA0` final BIA |
| 10 | Checksum | XOR of bytes 0–9 |

## Collection state
`0x01` frames with zero impedance are retained as live weight samples. `0xA0` frames with plausible nonzero impedance are retained as final BIA samples. After a BIA frame, the client waits 1.8 seconds; additional BIA frames extend that window. A 25-second overall timeout protects against incomplete measurements.

The final BIA frame's weight and impedance are authoritative for body composition. Live weights are primarily used for stability diagnostics.
