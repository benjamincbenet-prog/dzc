# Body Composition Calculations

## Inputs
- Weight (kg), measured by the scale.
- Whole-body foot-to-foot impedance (ohms), measured by the scale.
- Height (cm), age, and sex from the user profile.
- Optional waist circumference (cm), measured consistently with a tape.

## Primary electrical estimate
The implementation uses the documented standing foot-to-foot FFM regression:

`FFM = 13.055 + 0.204*W + 0.394*(H²/Z) - 0.136*Age + 8.125*Sex`

Where W is kg, H is cm, Z is ohms, and Sex is 0 for female and 1 for male.

The electrical estimate is converted to body fat as:
- `fatMass = weight - FFM`
- `BIA bodyFatPercent = 100 * fatMass / weight`

## Optional waist cross-check
When waist circumference is available, the app also calculates Relative Fat Mass (RFM):
- Male: `RFM = 64 - 20 * (height / waist)`
- Female: `RFM = 76 - 20 * (height / waist)`

Height and waist must use the same units. The app stores both component estimates and the waist-to-height ratio.

## Single exported body-fat value
Health Connect requires one body-fat percentage. With a valid waist measurement, version 1.2 exports a transparent consensus:

`consensus = 0.60 * BIA estimate + 0.40 * waist RFM estimate`

Without waist, the exported value is the BIA estimate. This consensus is a practical cross-check, not a clinically validated replacement for DXA or a device-specific clinical equation. The dashboard displays the BIA and waist components so disagreement is visible rather than hidden.

## Derived values
The app then calculates:
- `fatMass = weight * finalBodyFatPercent / 100`
- `leanMass = weight - fatMass`
- `bodyWater = leanMass * 0.73`
- `BMI = weight / (heightMeters²)`

## BMR
The app uses Mifflin-St Jeor:
- Male: `10W + 6.25H - 5Age + 5`
- Female: `10W + 6.25H - 5Age - 161`

## Measurement technique for waist
Use a flexible tape, measure after a normal exhale, avoid compressing the abdomen, and use the same anatomical location and technique each time. Consistency matters more than changing locations between sessions.

## Limitations
The estimates are model-derived and sensitive to hydration, electrode contact, physiology, waist technique, and the limits of single-frequency foot-to-foot BIA. Quality score describes data acquisition, not clinical certainty. A clinical comparison should be used for trend calibration rather than assumed to prove one formula universally accurate.
