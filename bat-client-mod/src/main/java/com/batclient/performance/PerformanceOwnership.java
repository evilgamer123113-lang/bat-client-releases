package com.batclient.performance;

/** Cross-classloader handshake with the optional cosmetics Java agent. No JVM tuning. */
public final class PerformanceOwnership {
    private PerformanceOwnership() { }
    public static void claim() {
        try { System.setProperty("batclient.performance.owner", "mod"); }
        catch (SecurityException ignored) { }
    }
}
