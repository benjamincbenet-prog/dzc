# User Guide

## First-time setup
1. Open **Profile & Settings**.
2. Enter your name (optional), height in centimeters, age, and sex.
3. Optionally enable **Automatically sync completed measurements**.
4. Save the profile.

Height and age are required because the body-composition formula uses them. The app currently validates height from 100–250 cm and age from 16–100 years.

## Taking a measurement
1. Make sure Bluetooth is enabled.
2. Tap **Connect DZC Scale**.
3. Grant Android Bluetooth permissions if requested.
4. Step on the scale and remain still.
5. The app collects live weight packets and waits for the final impedance/BIA packet.
6. Results appear when the measurement is complete.

## Understanding the results
- **Weight**: direct scale measurement selected from the final BIA frame.
- **BMI**: weight divided by height squared.
- **Impedance**: final electrical resistance reported by the scale.
- **Estimated body fat**: derived from the documented FFM model.
- **Fat mass / lean mass / body water / BMR**: calculated estimates.
- **Quality**: acquisition-quality score; it is not a medical accuracy percentage.
- **Live samples**: weight-stream packets retained during the measurement.
- **BIA frames**: final body-composition frames received.
- **Standard deviation**: variability of the retained values. A single impedance frame naturally has an impedance SD of 0 because there is no repeatability sample to compare.

## Health Connect
When auto-sync is off, tap **Save to Health Connect** after a completed measurement. When auto-sync is on, the app starts the same permission/write flow automatically after a completed measurement. Android/Health Connect permissions may still need to be granted.

The app avoids saving the same in-memory measurement twice after a successful write.
