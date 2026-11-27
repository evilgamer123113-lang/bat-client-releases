package com.batclient;

import com.batclient.platform.ClientHookServices;
import com.batclient.platform.mc189.GLFW;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.Display;

/** Camera-only orbit for 1.8 / 1.8.9. */
public final class Freelook {
    private static boolean active, lastHeld, blocked;
    private static Object world, player;
    private static int previous = -1;
    private static float yaw, pitch;

    private Freelook() {}

    public static void init() {
        BatKeybinds.register("freelook", ClientHookServices.get().registerKeyBinding(new KeyBinding("key.batclient.freelook", GLFW.GLFW_KEY_LEFT_ALT, "category.batclient.title")));
    }

    public static boolean active() { return active; }
    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }

    public static void tick(MinecraftClient c) {
        boolean held = BatKeybinds.held(c, "freelook"), edge = held && !lastHeld;
        lastHeld = held;
        if (!held) blocked = false;
        JsonObject mod = VisualModules.settings("freelook");
        boolean usable = HudConfig.enabled(mod) && c.world != null && c.player != null && c.player.isAlive()
            && c.getCameraEntity() == c.player && c.currentScreen == null && Display.isActive();
        if (!usable || active && (world != c.world || player != c.player)) { stop(c, true); blocked = held; return; }
        if (active && c.options.perspective != 1) { stop(c, false); blocked = held; return; }
        boolean toggle = "Toggle".equals(HudConfig.str(mod, "mode", "Hold"));
        if (active && (toggle ? edge : !held)) { stop(c, true); return; }
        if (!active && !blocked && (toggle ? edge : held)) start(c);
    }

    private static void start(MinecraftClient c) {
        previous = c.options.perspective;
        yaw = c.player.yaw + (previous == 2 ? 180 : 0);
        pitch = previous == 2 ? -c.player.pitch : c.player.pitch;
        world = c.world;
        player = c.player;
        active = true;
        c.options.perspective = 1;
    }

    private static void stop(MinecraftClient c, boolean restore) {
        if (active && restore && previous >= 0 && c.options.perspective == 1) c.options.perspective = previous;
        active = false;
        previous = -1;
        world = player = null;
    }

    public static boolean mouse(double x, double y) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (!active) return false;
        if (c.currentScreen != null || c.world != world || c.player != player || !Display.isActive() || !VisualModules.enabled("freelook")) {
            stop(c, true);
            return false;
        }
        float speed = VisualModules.number(VisualModules.settings("freelook"), "sensitivity", 100, 10, 200) / 100f;
        yaw = MathHelper.wrapDegrees(yaw + (float) x * .15f * speed);
        pitch = MathHelper.clamp(pitch + (float) y * .15f * speed, -90, 90);
        return true;
    }
}
