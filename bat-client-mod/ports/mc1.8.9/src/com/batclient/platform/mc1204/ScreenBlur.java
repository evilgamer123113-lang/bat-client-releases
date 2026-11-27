package com.batclient.platform.mc1204;

import net.minecraft.client.MinecraftClient;

/** 1.8.9 has no MenuBlur post-shader; keep a safe no-op. */
public final class ScreenBlur {
    private ScreenBlur() { }
    public static void draw(MinecraftClient client, float delta) { }
    public static void close() { }
}
