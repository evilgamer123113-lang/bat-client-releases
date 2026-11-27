package com.batclient.hud;

import java.lang.ref.WeakReference;

/** One biome label, valid only for this world, language, tick and block position. */
public final class BiomeLabelCache {
    private WeakReference<Object> world;
    private Object language;
    private long tick;
    private int x, y, z;
    private String label;

    public String find(Object world, Object language, long tick, int x, int y, int z) {
        return this.world != null && this.world.get() == world && this.language == language && this.tick == tick
                && this.x == x && this.y == y && this.z == z ? label : null;
    }

    public void store(Object world, Object language, long tick, int x, int y, int z, String label) {
        if (this.world == null || this.world.get() != world) this.world = new WeakReference<>(world);
        this.language = language; this.tick = tick;
        this.x = x; this.y = y; this.z = z; this.label = label;
    }

    public void clear() { world = null; language = null; label = null; }
}
