package com.batclient.hud;

public final class ZoomInterpolation {
    private ZoomInterpolation() { }
    public static double target(boolean held, double level) {
        return held ? 1 / Math.max(1, Math.min(40, level)) : 1;
    }
    public static double scrollLevel(double current, double scroll, double speed, double maximum) {
        return Math.max(1, Math.min(Math.max(10, Math.min(40, maximum)), current * Math.pow(1.2, Math.max(-20, Math.min(20, scroll)) * Math.max(.25, Math.min(2, speed)))));
    }
    public static double mouseScale(double zoomFactor, double sensitivityPercent) {
        double factor = Math.max(.025, Math.min(1, zoomFactor));
        return factor + (1 - factor) * (factor * Math.max(.1, Math.min(1, sensitivityPercent / 100)) - factor);
    }
    public static double step(double current, double target, double elapsed) {
        return step(current, target, elapsed, 18);
    }
    public static double step(double current, double target, double elapsed, double speed) {
        double next = current + (target - current) * (1 - Math.exp(-Math.max(5, Math.min(30, speed)) * Math.max(0, Math.min(.1, elapsed))));
        return Math.abs(next - target) < .0001 ? target : next;
    }
}
