package com.tonya.dzcscale.body;

import com.tonya.dzcscale.model.BodyMetrics;
import com.tonya.dzcscale.model.Measurement;
import com.tonya.dzcscale.model.Sex;

/**
 * Formula policy v1.2.
 *
 * Primary electrical estimate: Wu et al. 2015 standing foot-to-foot DXA FFM
 * regression, selected because the DZC-D18E3 is a standing foot-to-foot BIA
 * scale and provides whole-body impedance.
 *
 * Optional anthropometric cross-check: Relative Fat Mass (RFM), calculated from
 * height and waist circumference. When a valid waist is available, the app uses
 * a transparent 60/40 BIA/RFM consensus for its single exported body-fat value.
 * The component estimates remain available to the UI for disagreement review.
 */
public final class BodyCompositionCalculator {
    public static final String ALGORITHM_VERSION = "FootToFoot-DXA-RFM-v1.2";
    public static final String PRIMARY_MODEL =
            "Wu 2015 foot-to-foot BIA + optional waist RFM consensus";
    private static final double TBW_HYDRATION_FRACTION = 0.73;
    private static final double BIA_WEIGHT = 0.60;
    private static final double RFM_WEIGHT = 0.40;

    public static BodyMetrics calculate(Measurement m, double heightCm, int age, Sex sex) {
        return calculate(m, heightCm, age, sex, Double.NaN);
    }

    /** Calculates one transparent, export-ready result from scale and profile inputs. */
    public static BodyMetrics calculate(Measurement m, double heightCm, int age, Sex sex, double waistCm) {
        if (m.weightKg() <= 0 || m.impedanceOhm() <= 0 || heightCm < 100 || heightCm > 250
                || age < 16 || age > 100) {
            throw new IllegalArgumentException("Profile or measurement outside supported range");
        }

        double sexCode = sex == Sex.MALE ? 1.0 : 0.0;
        double impedanceIndex = (heightCm * heightCm) / m.impedanceOhm();

        // Geometry-matched electrical model: estimates fat-free mass first.
        double ffm = 13.055
                + (0.204 * m.weightKg())
                + (0.394 * impedanceIndex)
                - (0.136 * age)
                + (8.125 * sexCode);
        ffm = clamp(ffm, 0.0, m.weightKg());

        double biaFatMass = m.weightKg() - ffm;
        double biaBodyFatPercent = 100.0 * biaFatMass / m.weightKg();

        boolean waistEnhanced = isValidWaist(waistCm, heightCm);
        double waistBodyFatPercent = Double.NaN;
        double waistToHeightRatio = Double.NaN;
        double bodyFatPercent = biaBodyFatPercent;

        if (waistEnhanced) {
            waistToHeightRatio = waistCm / heightCm;
            // Relative Fat Mass (Woolcott & Bergman): H and waist must use the same units.
            waistBodyFatPercent = sex == Sex.MALE
                    ? 64.0 - 20.0 * (heightCm / waistCm)
                    : 76.0 - 20.0 * (heightCm / waistCm);
            waistBodyFatPercent = clamp(waistBodyFatPercent, 2.0, 75.0);
            // Consensus deliberately remains transparent: no hidden calibration.
            bodyFatPercent = BIA_WEIGHT * biaBodyFatPercent + RFM_WEIGHT * waistBodyFatPercent;
        }

        bodyFatPercent = clamp(bodyFatPercent, 0.0, 75.0);
        double fatMass = m.weightKg() * bodyFatPercent / 100.0;
        double leanMass = m.weightKg() - fatMass;
        double bodyWater = leanMass * TBW_HYDRATION_FRACTION;
        double bmi = m.weightKg() / Math.pow(heightCm / 100.0, 2.0);

        double bmr = 10.0 * m.weightKg()
                + 6.25 * heightCm
                - 5.0 * age
                + (sex == Sex.MALE ? 5.0 : -161.0);

        return new BodyMetrics(
                m.weightKg(), m.impedanceOhm(), bmi, bodyFatPercent, fatMass, leanMass,
                bodyWater, bmr, waistEnhanced ? waistCm : Double.NaN, waistToHeightRatio,
                biaBodyFatPercent, waistBodyFatPercent, waistEnhanced,
                ALGORITHM_VERSION, PRIMARY_MODEL, m.qualityScore()
        );
    }

    private static boolean isValidWaist(double waistCm, double heightCm) {
        if (!Double.isFinite(waistCm) || waistCm < 45.0 || waistCm > 200.0) return false;
        double ratio = waistCm / heightCm;
        return ratio >= 0.25 && ratio <= 0.90;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private BodyCompositionCalculator() {}
}
