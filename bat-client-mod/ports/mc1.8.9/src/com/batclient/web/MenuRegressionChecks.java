package com.batclient.web;

import net.minecraft.client.MinecraftClient;

/** Opt-in development regression probe (no-op on 1.8.9). */
public final class MenuRegressionChecks {
    private MenuRegressionChecks() { }
    public static void tick(MinecraftClient client) { }
}
