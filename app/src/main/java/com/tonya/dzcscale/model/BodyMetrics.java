package com.tonya.dzcscale.model;

/**
 * Measured values, deterministic estimates, and model provenance for one completed
 * scale session. The individual BIA and waist estimates are retained so the UI can
 * remain transparent even though Health Connect receives one final body-fat value.
 */
public record BodyMetrics(
        double weightKg,
        double impedanceOhm,
        double bmi,
        double bodyFatPercent,
        double fatMassKg,
        double leanMassKg,
        double bodyWaterKg,
        double bmrKcal,
        double waistCm,
        double waistToHeightRatio,
        double biaBodyFatPercent,
        double waistBodyFatPercent,
        boolean waistEnhanced,
        String algorithmVersion,
        String primaryModel,
        int qualityScore
) {}
