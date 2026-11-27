package com.batclient;

import com.batclient.platform.ClientHookServices;
import com.batclient.platform.WorldFrame;
import com.batclient.platform.mc1194.DrawContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.Window;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Bounded camera-facing markers for saved positions only. No chunk loads, entities or network queries. */
public final class WaypointRenderer {
    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer WIN_POS = BufferUtils.createFloatBuffer(3);

    private WaypointRenderer() { }

    public static void init() {
        ClientHookServices.get().afterEntities(WaypointRenderer::world);
        ClientHookServices.get().hud(WaypointRenderer::navigation);
    }

    public static int color(WaypointStore.Entry e) {
        int rgb = e.chroma()
            ? java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 10000) / 10000f, .7f, 1f) & 0xFFFFFF
            : Integer.parseInt(e.color().substring(1), 16);
        return (Math.round(e.opacity() * 2.55f) << 24) | rgb;
    }

    public static String distance(double d) {
        return d >= 1000 ? String.format(Locale.ROOT, "%.1f km", d / 1000) : Math.round(d) + " m";
    }

    private static double distance(MinecraftClient c, WaypointStore.Entry e) {
        return Math.sqrt(c.player.squaredDistanceTo(e.x() + .5, e.y(), e.z() + .5));
    }

    private static void world(WorldFrame ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        JsonObject mod = VisualModules.settings("waypoints");
        if (!HudConfig.enabled(mod) || c.player == null || c.world == null || !MinecraftClient.isHudEnabled() || c.currentScreen != null) return;
        double range = VisualModules.number(mod, "range", 10000, 64, 30000000);
        int limit = (int) VisualModules.number(mod, "markers", 12, 1, 24);
        List<WaypointStore.Entry> values = new ArrayList<>();
        for (WaypointStore.Entry e : Waypoints.current()) {
            if (e.visible() && e.opacity() > 0 && e.dimension().equals(Waypoints.dimension()) && distance(c, e) <= range) {
                values.add(e);
            }
        }
        values.sort(Comparator.<WaypointStore.Entry>comparingInt(e -> e.tracked() ? 0 : e.favorite() ? 1 : 2)
            .thenComparingDouble(e -> distance(c, e)));
        if (values.size() > 64) values = values.subList(0, 64);

        int effects = 0;
        double effectRange = c.options.viewDistance * 16 + 64;
        for (WaypointStore.Entry e : values) {
            if (distance(c, e) >= effectRange) continue;
            if (HudConfig.bool(mod, "ring", true)) groundRing(ctx, e);
            if (e.beam() && HudConfig.bool(mod, "beam", false)) beam(ctx, e, (int) VisualModules.number(mod, "beamHeight", 128, 16, 256));
            if (++effects >= limit) break;
        }

        MODELVIEW.clear();
        PROJECTION.clear();
        VIEWPORT.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);

        Window win = new Window(c);
        int sw = (int) win.getScaledWidth();
        int sh = (int) win.getScaledHeight();
        int scaleFactor = Math.max(1, win.getScaleFactor());
        List<float[]> occupied = new ArrayList<>();
        Vec3d camera = ctx.cameraPos();
        int drawn = 0;

        for (WaypointStore.Entry e : values) {
            Vec3d real = new Vec3d(e.x() + .5, e.y() + 2.5, e.z() + .5);
            Vec3d delta = real.subtract(camera);
            double actual = distance(c, e);
            double lenSq = delta.x * delta.x + delta.y * delta.y + delta.z * delta.z;
            if (lenSq < .04) continue;
            double len = Math.sqrt(lenSq);
            double clamped = HudConfig.bool(mod, "throughWalls", true) ? Math.min(16, len) : len;
            double ox = delta.x / len * clamped, oy = delta.y / len * clamped, oz = delta.z / len * clamped;

            WIN_POS.clear();
            if (!GLU.gluProject((float) ox, (float) oy, (float) oz, MODELVIEW, PROJECTION, VIEWPORT, WIN_POS)) continue;
            float wz = WIN_POS.get(2);
            if (wz <= 0f || wz >= 1f) continue;
            float sx = WIN_POS.get(0) / scaleFactor;
            float sy = sh - WIN_POS.get(1) / scaleFactor;
            if (sx < -40 || sx > sw + 40 || sy < -40 || sy > sh + 40) continue;

            String title = e.name() + " [" + Math.round(actual) + "m]";
            float scale = VisualModules.number(mod, "scale", 1, .6f, 1.8f);
            float plateWidth = c.textRenderer.getStringWidth(title);
            if (HudConfig.bool(mod, "coordinates", false)) {
                plateWidth = Math.max(plateWidth, c.textRenderer.getStringWidth(e.x() + ", " + e.y() + ", " + e.z()));
            }
            int pad = (int) VisualModules.number(mod, "padding", 4, 0, 10);
            float width = (plateWidth + pad * 2) * scale;
            float height = ((HudConfig.bool(mod, "coordinates", false) ? 22 : 10) + pad * 2) * scale;
            float displacement = 0;
            boolean accepted = false;
            for (int step = 0; step < 5; step++) {
                displacement = step == 0 ? 0 : (step % 2 == 1 ? -1 : 1) * ((step + 1) / 2) * (height + 3);
                float[] box = {sx - width / 2, sy + displacement - 10, sx + width / 2, sy + displacement + height};
                boolean overlap = false;
                for (float[] other : occupied) {
                    if (box[0] < other[2] && box[2] > other[0] && box[1] < other[3] && box[3] > other[1]) { overlap = true; break; }
                }
                if (!overlap && box[1] > 12 && box[3] < sh - 12) {
                    occupied.add(box);
                    accepted = true;
                    break;
                }
            }
            if (!accepted) continue;

            boolean through = HudConfig.bool(mod, "throughWalls", true);
            boolean background = HudConfig.bool(mod, "background", true);
            boolean shadow = HudConfig.bool(mod, "shadow", true);
            float l = -plateWidth / 2 - pad, r = plateWidth / 2 + pad, t = -pad;
            float b = (HudConfig.bool(mod, "coordinates", false) ? 21 : 9) + pad;
            int tint = color(e);
            float radius = Math.min(3.5f, (b - t) / 2);

            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate((float) ox, (float) oy, (float) oz);
                GlStateManager.rotate(-c.getEntityRenderManager().yaw, 0f, 1f, 0f);
                GlStateManager.rotate(c.getEntityRenderManager().pitch, 1f, 0f, 0f);
                float pixel = (float) (clamped * 0.025) * scale;
                GlStateManager.scale(-pixel, -pixel, pixel);
                GlStateManager.translate(0f, displacement / scale, 0f);

                VisualLayers.beginPlate(through);
                if (HudConfig.bool(mod, "glow", true) && HudConfig.bool(mod, "border", true)) {
                    rounded(l - 2, t - 2, r + 2, b + 2, radius + 2, (Math.round(e.opacity() * .2f) << 24) | (tint & 0xFFFFFF), 2.5f);
                    rounded(l - 1, t - 1, r + 1, b + 1, radius + 1, (Math.round(e.opacity() * .35f) << 24) | (tint & 0xFFFFFF), 1.7f);
                }
                if (background) rounded(l, t, r, b, radius, (Math.round(e.opacity() * 1.15f) << 24), 0);
                if (HudConfig.bool(mod, "border", true)) rounded(l, t, r, b, radius, tint, .65f);
                VisualLayers.endPlate();

                GlStateManager.enableTexture();
                if (through) GlStateManager.disableDepthTest();
                int alpha = tint & 0xFF000000;
                line(title, alpha | 0xFFFFFF, 0, shadow);
                if (HudConfig.bool(mod, "coordinates", false)) line(e.x() + ", " + e.y() + ", " + e.z(), alpha | 0xDDDDDD, 12, shadow);
                if (through) GlStateManager.enableDepthTest();
            } finally {
                GlStateManager.popMatrix();
            }
            if (++drawn >= limit) break;
        }
    }

    private static void line(String value, int color, int y, boolean shadow) {
        net.minecraft.client.font.TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int x = Math.round(-font.getStringWidth(value) / 2f);
        font.draw(value, (float) x, (float) y, color, shadow);
    }

    private static float[] cornerPoint(float l, float t, float r, float b, float radius, int index) {
        int corner = (index / 9) % 4;
        float angle = (float) Math.toRadians(180 + corner * 90 + (index % 9) * 90f / 8);
        float cx = corner == 0 || corner == 3 ? l + radius : r - radius;
        float cy = corner < 2 ? t + radius : b - radius;
        return new float[]{cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius};
    }

    private static void rounded(float l, float t, float r, float b, float radius, int color, float stroke) {
        int aCol = (color >>> 24) & 255, rCol = (color >>> 16) & 255, gCol = (color >>> 8) & 255, bCol = color & 255;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < 36; i++) {
            float[] a = cornerPoint(l, t, r, b, radius, i);
            float[] z = cornerPoint(l, t, r, b, radius, (i + 1) % 36);
            float[] ai = stroke > 0 ? cornerPoint(l + stroke, t + stroke, r - stroke, b - stroke, Math.max(0, radius - stroke), i) : new float[]{(l + r) / 2, (t + b) / 2};
            float[] zi = stroke > 0 ? cornerPoint(l + stroke, t + stroke, r - stroke, b - stroke, Math.max(0, radius - stroke), (i + 1) % 36) : ai;
            buf.vertex(a[0], a[1], -.02).color(rCol, gCol, bCol, aCol).next();
            buf.vertex(z[0], z[1], -.02).color(rCol, gCol, bCol, aCol).next();
            buf.vertex(zi[0], zi[1], -.02).color(rCol, gCol, bCol, aCol).next();
            buf.vertex(ai[0], ai[1], -.02).color(rCol, gCol, bCol, aCol).next();
        }
        tess.draw();
    }

    private static void groundRing(WorldFrame ctx, WaypointStore.Entry e) {
        Vec3d camera = ctx.cameraPos();
        float x = (float) (e.x() + .5 - camera.x), y = (float) (e.y() + .035 - camera.y), z = (float) (e.z() + .5 - camera.z);
        int tint = color(e);
        VisualLayers.beginPlate(false);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
        for (int layer = 0; layer < 3; layer++) {
            float outer = .65f + layer * .045f, inner = .62f - layer * .015f;
            int shade = layer == 0 ? tint : ((layer == 1 ? 35 : 15) * (tint >>> 24) / 255 << 24) | (tint & 0xFFFFFF);
            int aCol = (shade >>> 24) & 255, rCol = (shade >>> 16) & 255, gCol = (shade >>> 8) & 255, bCol = shade & 255;
            for (int i = 0; i < 64; i++) {
                double a = i * Math.PI / 32, b = (i + 1) * Math.PI / 32;
                float ax = (float) Math.cos(a), az = (float) Math.sin(a), bx = (float) Math.cos(b), bz = (float) Math.sin(b);
                buf.vertex(x + ax * outer, y, z + az * outer).color(rCol, gCol, bCol, aCol).next();
                buf.vertex(x + bx * outer, y, z + bz * outer).color(rCol, gCol, bCol, aCol).next();
                buf.vertex(x + bx * inner, y, z + bz * inner).color(rCol, gCol, bCol, aCol).next();
                buf.vertex(x + ax * inner, y, z + az * inner).color(rCol, gCol, bCol, aCol).next();
            }
        }
        tess.draw();
        VisualLayers.endPlate();
    }

    private static void beam(WorldFrame ctx, WaypointStore.Entry e, int height) {
        Vec3d pos = ctx.cameraPos();
        float x = (float) (e.x() + .5 - pos.x), y = (float) (e.y() - pos.y), z = (float) (e.z() + .5 - pos.z);
        float w = .16f, h = Math.min(height, 256 - e.y());
        if (h <= 0) return;
        int tint = color(e), alpha = Math.min(120, tint >>> 24);
        int rCol = (tint >>> 16) & 255, gCol = (tint >>> 8) & 255, bCol = tint & 255;
        VisualLayers.beginPlate(false);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
        for (int side = 0; side < 4; side++) {
            double a = side * Math.PI / 2, b = (side + 1) * Math.PI / 2;
            float ax = x + (float) Math.cos(a) * w, az = z + (float) Math.sin(a) * w;
            float bx = x + (float) Math.cos(b) * w, bz = z + (float) Math.sin(b) * w;
            buf.vertex(ax, y, az).color(rCol, gCol, bCol, alpha).next();
            buf.vertex(bx, y, bz).color(rCol, gCol, bCol, alpha).next();
            buf.vertex(bx, y + h, bz).color(rCol, gCol, bCol, alpha).next();
            buf.vertex(ax, y + h, az).color(rCol, gCol, bCol, alpha).next();
        }
        tess.draw();
        VisualLayers.endPlate();
    }

    public static void compass(DrawContext ctx, MinecraftClient c, double heading, boolean preview) {
        JsonObject mod = VisualModules.settings("waypoints");
        if (preview || c.player == null || !HudConfig.enabled(mod) || !HudConfig.bool(mod, "compass", true)) return;
        int count = 0;
        for (WaypointStore.Entry e : Waypoints.current()) {
            if (e.visible() && e.opacity() > 0 && e.dimension().equals(Waypoints.dimension())) {
                double dx = e.x() + .5 - c.player.x, dz = e.z() + .5 - c.player.z;
                double angle = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
                float offset = MathHelper.wrapDegrees((float) (angle - heading)) * .8f;
                if (Math.abs(offset) > 72) continue;
                int x = Math.round(80 + offset);
                ctx.fill(x - 2, 1, x + 3, 5, color(e));
                if (e.tracked()) ctx.drawBorder(x - 3, 0, 7, 6, 0xFFFFFFFF);
                if (++count >= 24) break;
            }
        }
    }

    private static void navigation(DrawContext ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        JsonObject mod = VisualModules.settings("waypoints");
        if (!HudConfig.enabled(mod) || !HudConfig.bool(mod, "navigation", true) || c.player == null || c.world == null || c.currentScreen != null || !MinecraftClient.isHudEnabled()) return;
        WaypointStore.Entry point = null;
        for (WaypointStore.Entry e : Waypoints.current()) {
            if (e.tracked() && e.visible() && e.opacity() > 0) { point = e; break; }
        }
        if (point == null) return;
        String value;
        if (!point.dimension().equals(Waypoints.dimension())) {
            value = point.name() + " - " + point.dimension().replace("minecraft:", "");
        } else {
            double dx = point.x() + .5 - c.player.x, dz = point.z() + .5 - c.player.z;
            double yaw = Math.toDegrees(Math.atan2(-dx, dz)) - c.player.yaw;
            yaw = ((yaw + 540) % 360 + 360) % 360 - 180;
            String direction = Math.abs(yaw) < 15 ? "^" : Math.abs(yaw) > 160 ? "v" : yaw > 0 ? ">" : "<";
            value = direction + " " + point.name() + " - " + distance(distance(c, point));
        }
        Window win = new Window(c);
        int width = c.textRenderer.getStringWidth(value);
        int x = ((int) win.getScaledWidth() - width) / 2;
        int y = Math.round((float) win.getScaledHeight() * VisualModules.number(mod, "navigationY", 14, 3, 90) / 100);
        if (HudConfig.bool(mod, "background", true)) HudRenderer.fillRounded(ctx, x - 7, y - 4, width + 14, 17, 5, 0x99000000);
        ctx.drawText(c.textRenderer, value, x, y, color(point), HudConfig.bool(mod, "shadow", true));
    }

    public static void minimap(DrawContext ctx, MinecraftClient c, int x, int z, int radius, int size) {
        JsonObject mod = VisualModules.settings("waypoints");
        if (!HudConfig.enabled(mod) || !HudConfig.bool(mod, "minimap", true)) return;
        for (WaypointStore.Entry point : Waypoints.current()) {
            if (point.visible() && point.opacity() > 0 && point.dimension().equals(Waypoints.dimension())) {
                int px = Math.round(size / 2f + (point.x() + .5f - x) * size / (radius * 2));
                int py = Math.round(size / 2f + (point.z() + .5f - z) * size / (radius * 2));
                if (px < 4 || py < 4 || px > size - 5 || py > size - 5) continue;
                ctx.fill(px - 3, py - 3, px + 4, py + 4, 0xFF101010);
                ctx.fill(px - 2, py - 2, px + 3, py + 3, color(point));
                if (point.tracked()) ctx.drawBorder(px - 4, py - 4, 9, 9, 0xFFFFFFFF);
            }
        }
    }
}
