package com.tonya.dzcscale;

/** Process-wide gate that prevents overlapping or immediately duplicated measurement sessions. */
public final class MeasurementCoordinator {
    private static boolean active;
    private static long lastCompleted;
    private MeasurementCoordinator() {}
    public static synchronized boolean begin() { if (active) return false; active = true; return true; }
    public static synchronized void complete() { active = false; lastCompleted = System.currentTimeMillis(); }
    public static synchronized void fail() { active = false; }
    public static synchronized boolean recentlyCompleted() { return System.currentTimeMillis() - lastCompleted < 15000L; }
}
