package com.batclient.hud;

/** Durability warning rules, independent of item rendering. */
public final class ArmorWarning {
    private ArmorWarning() { }
    public static boolean low(int maximum, int damage, boolean enabled, float threshold) {
        if (!enabled || maximum <= 0 || damage < 0) return false;
        float percent = Float.isFinite(threshold) ? Math.max(1, Math.min(50, threshold)) : 15;
        return Math.max(0L, (long) maximum - damage) * 100.0 / maximum <= percent;
    }
    public static boolean visible(boolean flashing, long millis) {
        return !flashing || Math.floorMod(millis, 1000) < 650;
    }
}
