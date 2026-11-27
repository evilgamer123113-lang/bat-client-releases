package com.batclient;

import com.batclient.hud.TrailHistory;
import com.batclient.platform.ClientHookServices;
import com.batclient.platform.WorldFrame;
import com.batclient.platform.mc1194.DrawContext;
import com.batclient.platform.mc1204.Vertices;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.Window;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Breadcrumbs of observed movement for 1.8 / 1.8.9. */
public final class ReturnTrail {
    private static final TrailHistory HISTORY = new TrailHistory();
    private static List<TrailHistory.Point> points = Collections.emptyList(), route = Collections.emptyList();
    private static String key = "";
    private static Object world, player;
    private static int ticks, cursor = -1;
    private static boolean wasDead;

    private ReturnTrail() {}

    public static void init() {
        BatKeybinds.register("trail_retrace", ClientHookServices.get().registerKeyBinding(new KeyBinding("key.batclient.trail_retrace", 0, "category.batclient.title")));
        ClientHookServices.get().afterEntities(ReturnTrail::render);
        ClientHookServices.get().hud(ReturnTrail::guide);
    }

    private static long now() { return System.nanoTime() / 1_000_000L; }

    private static String identity(MinecraftClient c) {
        String scope = c.getServer() != null ? "save:" + c.getServer().getLevelName()
            : c.getCurrentServerEntry() != null ? "server:" + ServerProfiles.normalize(c.getCurrentServerEntry().address)
            : "connection:" + c.getNetworkHandler().getClientConnection().getAddress();
        return scope + "|" + (c.world != null ? c.world.dimension.getType() : 0);
    }

    private static void stop() { cursor = -1; route = Collections.emptyList(); HISTORY.interrupt(); }

    public static void tick(MinecraftClient c) {
        boolean retrace = BatKeybinds.pressed(c, "trail_retrace");
        if (c.world == null || c.player == null) {
            if (world != null) HISTORY.clearAll();
            world = player = null;
            key = "";
            points = Collections.emptyList();
            stop();
            return;
        }
        if (world != c.world || player != c.player) {
            world = c.world;
            player = c.player;
            key = identity(c);
            HISTORY.context(key);
            HISTORY.interrupt();
            stop();
            wasDead = false;
        }
        JsonObject mod = VisualModules.settings("returntrail");
        if (!HudConfig.enabled(mod)) {
            points = Collections.emptyList();
            stop();
            return;
        }
        long time = now(), lifetime = (long) (VisualModules.number(mod, "minutes", 10, 1, 30) * 60000);
        int cap = (int) VisualModules.number(mod, "points", 512, 64, 1024);
        if (c.player.removed || c.player.getHealth() <= 0) {
            if (!wasDead && HudConfig.bool(mod, "clearOnDeath", true)) HISTORY.clear(key);
            wasDead = true;
            stop();
            points = HISTORY.points(key, time, lifetime, cap);
            return;
        }
        wasDead = false;
        if (retrace && c.currentScreen == null) action(cursor >= 0 ? "resume" : "retrace");
        if (++ticks % 5 != 0) return;
        points = HISTORY.points(key, time, lifetime, cap);
        if (cursor >= 0) {
            if (route.isEmpty() || time - route.get(cursor).time() >= lifetime) { stop(); return; }
            while (cursor >= 0 && route.get(cursor).distanceSquared(c.player.x, c.player.y, c.player.z) <= 2.25) cursor--;
            if (cursor < 0) {
                stop();
                c.inGameHud.setOverlayMessage(new LiteralText("Back at the start of your trail"), false);
            }
            return;
        }
        if (c.isPaused()) return;
        if (c.player.hasVehicle() || c.player.abilities.flying || (HudConfig.bool(mod, "groundOnly", false) && !c.player.onGround)) {
            HISTORY.interrupt();
            return;
        }
        HISTORY.sample(key, c.player.x, c.player.y, c.player.z, time, lifetime, cap, VisualModules.number(mod, "spacing", 1, .5f, 4));
        points = HISTORY.points(key, time, lifetime, cap);
    }

    public static String action(String action) {
        MinecraftClient c = MinecraftClient.getInstance();
        String error = "";
        JsonObject mod = VisualModules.settings("returntrail");
        if (action.equals("clear")) {
            HISTORY.clear(key);
            points = Collections.emptyList();
            stop();
        } else if (action.equals("resume")) {
            stop();
        } else if (action.equals("retrace")) {
            if (!HudConfig.enabled(mod) || c.world == null || c.player == null || c.world != world || c.player != player) {
                error = "Enable Return Trail and join a world first.";
            } else {
                List<TrailHistory.Point> latest = TrailHistory.latestRoute(points);
                if (latest.size() < 2) error = "Walk a little farther to record a route first.";
                else { route = latest; cursor = route.size() - 2; }
            }
        } else if (!action.equals("status")) {
            error = "Unknown trail action.";
        }
        JsonObject result = new JsonObject();
        result.addProperty("error", error);
        result.addProperty("points", points.size());
        result.addProperty("returning", cursor >= 0);
        result.addProperty("remaining", Math.max(0, cursor + 1));
        result.addProperty("recording", HudConfig.enabled(mod) && c.player != null && c.world == world && cursor < 0);
        return result.toString();
    }

    private static void line(BufferBuilder v, Vec3d a, Vec3d b, int color) {
        Vertices.color(v.vertex(a.x, a.y, a.z), color).next();
        Vertices.color(v.vertex(b.x, b.y, b.z), color).next();
    }

    private static void render(WorldFrame ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        JsonObject mod = VisualModules.settings("returntrail");
        if (!HudConfig.enabled(mod) || c.player == null || c.world != world || c.currentScreen != null || !MinecraftClient.isHudEnabled()) return;
        List<TrailHistory.Point> visible = cursor >= 0 ? route : points;
        Vec3d camera = ctx.cameraPos();
        double range = VisualModules.number(mod, "range", 48, 8, 128);
        long age = now(), ttl = (long) (VisualModules.number(mod, "minutes", 10, 1, 30) * 60000);
        int base = HudRenderer.parseColor(HudConfig.str(mod, "color", "#75CFFFFF"), 0xFF75CFFF);
        float opacity = VisualModules.number(mod, "opacity", 80, 10, 100) / 100;
        String style = HudConfig.str(mod, "style", "Both");
        BufferBuilder vertices = VisualLayers.beginLines(VisualModules.number(mod, "width", 2, 1, 4));
        try {
            int drawn = 0;
            for (int i = visible.size() - 1; i > 0 && drawn < 256; i--) {
                TrailHistory.Point p = visible.get(i), previous = visible.get(i - 1);
                if (p.breakBefore() || age - p.time() >= ttl || p.distanceSquared(c.player.x, c.player.y, c.player.z) > range * range || cursor >= 0 && i > cursor + 1) continue;
                Vec3d a = new Vec3d(p.x(), p.y() + .045, p.z()).subtract(camera);
                Vec3d b = new Vec3d(previous.x(), previous.y() + .045, previous.z()).subtract(camera);
                float fade = (float) MathHelper.clamp(1 - (double) (age - p.time()) / ttl, .15, 1);
                int color = (Math.round((base >>> 24) * opacity * fade) << 24) | (base & 0xFFFFFF);
                if (!style.equals("Arrows")) line(vertices, a, b, color);
                if (!style.equals("Line")) {
                    Vec3d direction = new Vec3d(b.x - a.x, 0, b.z - a.z);
                    if (direction.dotProduct(direction) > .0001) {
                        direction = direction.normalize();
                        Vec3d side = new Vec3d(-direction.z, 0, direction.x);
                        Vec3d tip = new Vec3d(a.x + direction.x * .18, a.y + direction.y * .18, a.z + direction.z * .18);
                        Vec3d back = new Vec3d(a.x - direction.x * .12, a.y - direction.y * .12, a.z - direction.z * .12);
                        line(vertices, tip, new Vec3d(back.x + side.x * .12, back.y + side.y * .12, back.z + side.z * .12), color);
                        line(vertices, tip, new Vec3d(back.x - side.x * .12, back.y - side.y * .12, back.z - side.z * .12), color);
                    }
                }
                drawn++;
            }
        } finally {
            VisualLayers.endLines();
        }
    }

    private static void guide(DrawContext ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        JsonObject mod = VisualModules.settings("returntrail");
        if (cursor < 0 || !HudConfig.enabled(mod) || !HudConfig.bool(mod, "guide", true) || c.player == null || c.currentScreen != null || !MinecraftClient.isHudEnabled()) return;
        TrailHistory.Point target = route.get(cursor);
        double dx = target.x() - c.player.x, dz = target.z() - c.player.z;
        float yaw = MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(-dx, dz)) - c.player.yaw));
        String arrow = Math.abs(yaw) < 20 ? "^" : Math.abs(yaw) > 160 ? "v" : yaw > 0 ? ">" : "<";
        double distance = Math.sqrt(target.distanceSquared(c.player.x, c.player.y, c.player.z));
        for (int i = cursor; i > 0; i--) distance += Math.sqrt(route.get(i).distanceSquared(route.get(i - 1).x(), route.get(i - 1).y(), route.get(i - 1).z()));
        String text = arrow + " Trail back · " + Math.round(distance) + "m";
        Window win = new Window(c);
        int width = c.textRenderer.getStringWidth(text), x = ((int) win.getScaledWidth() - width) / 2, y = Math.round((float) win.getScaledHeight() * .21f);
        HudRenderer.fillRounded(ctx, x - 7, y - 4, width + 14, 17, 4, 0x99000000);
        ctx.drawText(c.textRenderer, text, x, y, 0xFFFFFFFF, true);
    }
}
