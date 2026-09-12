package com.tonya.dzcscale.model;

/**
 * Raw DZC measurement retained independently from formula outputs.
 * Weight and impedance are selected from separate protocol streams so a
 * transient live-weight packet cannot contaminate the BIA impedance estimate.
 */
public record Measurement(
        double weightKg,
        double impedanceOhm,
        long timeMillis,
        int sampleCount,
        int stabilizedSampleCount,
        int impedanceSampleCount,
        double impedanceStdDevOhm,
        double weightStdDevKg,
        int qualityScore
) {}
