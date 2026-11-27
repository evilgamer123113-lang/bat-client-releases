package com.batclient.web;

/** Paces web frames without rounding 60Hz down to half of Minecraft's frame rate. */
public final class UiFrameClock {
    private static final long PERIOD = 1_000_000_000L / 60;
    private static final long TOLERANCE = 2_000_000L;
    private long nextFrame;
    private boolean started;

    public void invalidate() { started = false; }

    public boolean shouldRender(long now, boolean urgent) {
        if (!urgent && started && now + TOLERANCE < nextFrame) return false;
        if (urgent || !started || now - nextFrame >= PERIOD) nextFrame = now + PERIOD;
        else nextFrame += PERIOD;
        started = true;
        return true;
    }
}
