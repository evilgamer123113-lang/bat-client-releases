package com.batclient;

import com.batclient.platform.ClientHookServices;
import com.batclient.platform.WorldFrame;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/** Death waypoint world label for 1.8 / 1.8.9. */
public final class AdventureWorld {
    private AdventureWorld() {}

    public static void init() {
        ClientHookServices.get().afterEntities(AdventureWorld::render);
    }

    private static void render(WorldFrame ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.world == null || !MinecraftClient.isHudEnabled() || c.currentScreen != null) return;
        JsonObject death = VisualModules.settings("deathcoords");
        DeathCoordinates.Death last = DeathCoordinates.last(c, false);
        String dim = String.valueOf(c.world.dimension.getType());
        if (!(VisualModules.enabled("waypoints") && HudConfig.bool(VisualModules.settings("waypoints"), "death", true))
                && HudConfig.enabled(death) && HudConfig.bool(death, "waypoint", true)
                && last != null && last.dimension().equals(dim)) {
            Vec3d target = new Vec3d(last.x() + .5, last.y() + 1.5, last.z() + .5);
            Vec3d playerPos = new Vec3d(c.player.x, c.player.y, c.player.z);
            double distance = playerPos.distanceTo(target);
            Vec3d camera = ctx.cameraPos(), delta = target.subtract(camera);
            if (delta.length() > 64) {
                Vec3d dir = delta.normalize();
                target = camera.add(dir.x * 64, dir.y * 64, dir.z * 64);
            }
            label(ctx, target, "Death · " + Math.round(distance) + "m", 0xFFFF6666, 1);
        }
    }

    private static void label(WorldFrame ctx, Vec3d world, String text, int color, float scale) {
        MinecraftClient c = MinecraftClient.getInstance();
        Vec3d camera = ctx.cameraPos();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(world.x - camera.x, world.y - camera.y, world.z - camera.z);
            GlStateManager.rotate(-c.getEntityRenderManager().yaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(c.getEntityRenderManager().pitch, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-.025f * scale, -.025f * scale, .025f * scale);
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            GlStateManager.disableDepthTest();
            GlStateManager.enableBlend();
            float x = -c.textRenderer.getStringWidth(text) / 2f;
            c.textRenderer.drawWithShadow(text, x, 0, color);
            GlStateManager.enableDepthTest();
            GlStateManager.depthMask(true);
            GlStateManager.enableLighting();
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
