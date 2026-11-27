package com.batclient.hud;

import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/** Counts matching stacks in a bounded inventory range, without mutating them. */
public final class ItemCounter {
    private ItemCounter() { }
    public static int count(int slots, IntPredicate matches, IntUnaryOperator amount) {
        int total = 0;
        for (int i=0;i<slots;i++) if (matches.test(i)) total += Math.max(0,amount.applyAsInt(i));
        return total;
    }
    public static boolean warning(int count, boolean enabled, float threshold, int fallback) {
        int limit = Float.isFinite(threshold) ? (int)Math.max(0,Math.min(1024,threshold)) : fallback;
        return enabled && count <= limit;
    }
}
