package com.batclient;

import com.batclient.platform.mc1194.DrawContext;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import java.util.Collections;
import java.util.List;

/** Elytra and Shield HUDs are 1.9+ features; keep safe 1.8.9 stubs. */
public final class TravelHud {
    private TravelHud() { }
    public static void tick(MinecraftClient c) { }
    public static List<String> lines(MinecraftClient c, String id, JsonObject mod, boolean preview) { return Collections.emptyList(); }
    public static int width(MinecraftClient c, String id, JsonObject mod, boolean preview) { return 0; }
    public static int height(MinecraftClient c, String id, JsonObject mod, boolean preview) { return 0; }
    public static void draw(DrawContext ctx, MinecraftClient c, String id, JsonObject mod, boolean preview) { }
}
