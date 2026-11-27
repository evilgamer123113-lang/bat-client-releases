package com.batclient;

import com.batclient.platform.BlockTarget;
import com.batclient.platform.ClientHookServices;
import com.batclient.platform.WorldFrame;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.Projectile;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

public final class WorldVisualModules {
    private WorldVisualModules() { }

    public static void init() {
        ClientHookServices.get().blockOutline(WorldVisualModules::block);
        ClientHookServices.get().afterEntities(WorldVisualModules::hitboxes);
    }

    private static boolean visible(WorldFrame context) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && MinecraftClient.isHudEnabled() && client.currentScreen == null;
    }

    private static int color(JsonObject mod, String key, String fallback) {
        return HudRenderer.parseColor(HudConfig.str(mod, key, fallback), 0xFFFFFFFF);
    }

    private static boolean block(WorldFrame context, BlockTarget target) {
        JsonObject mod = VisualModules.settings("blockoutline");
        if (!HudConfig.enabled(mod) || !visible(context)) return true;
        BlockPos pos = target.blockPos();
        Block blk = target.blockState().getBlock();
        blk.setBoundingBox(context.world(), pos);
        Box raw = blk.getSelectionBox(context.world(), pos);
        if (raw == null) return true;
        Vec3d camera = context.cameraPos();
        Box b = raw.expand(0.002, 0.002, 0.002).offset(-camera.x, -camera.y, -camera.z);
        int color = color(mod, "color", "#FFFFFFFF");
        VisualLayers.beginLineState(VisualModules.number(mod, "width", 2, 1, 4));
        WorldRenderer.drawBox(b, (color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255);
        VisualLayers.endLineState();
        if (HudConfig.bool(mod, "fill", false)) {
            int tint = color(mod, "fillColor", "#FFFFFF33");
            VisualLayers.beginPlate(false);
            fillBox(b, tint);
            VisualLayers.endPlate();
        }
        return false;
    }

    private static void hitboxes(WorldFrame context) {
        JsonObject mod = VisualModules.settings("hitboxes");
        if (!HudConfig.enabled(mod) || !visible(context)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d camera = context.cameraPos();
        float delta = context.tickDelta();
        float range = VisualModules.number(mod, "range", 32, 4, 64);
        int drawn = 0;
        VisualLayers.beginLineState(VisualModules.number(mod, "width", 1.5f, 1, 4));
        for (Entity entity : context.world().getLoadedEntities()) {
            if (entity == client.player || !entity.isAlive() || entity.isInvisibleTo(client.player)) continue;
            if (entity.squaredDistanceTo(camera.x, camera.y, camera.z) > range * range) continue;
            boolean player = entity instanceof PlayerEntity;
            boolean mob = entity instanceof LivingEntity && !player;
            boolean projectile = entity instanceof Projectile;
            if (!(player && HudConfig.bool(mod, "players", true)
                || mob && HudConfig.bool(mod, "mobs", false)
                || projectile && HudConfig.bool(mod, "projectiles", true))) continue;
            double lx = entity.prevTickX + (entity.x - entity.prevTickX) * delta - entity.x;
            double ly = entity.prevTickY + (entity.y - entity.prevTickY) * delta - entity.y;
            double lz = entity.prevTickZ + (entity.z - entity.prevTickZ) * delta - entity.z;
            Box box = entity.getBoundingBox().offset(lx - camera.x, ly - camera.y, lz - camera.z);
            int tint = color(mod, player ? "playerColor" : mob ? "mobColor" : "projectileColor",
                player ? "#FFFFFFFF" : mob ? "#55FF55FF" : "#FFAA00FF");
            WorldRenderer.drawBox(box, (tint >> 16) & 255, (tint >> 8) & 255, tint & 255, (tint >>> 24) & 255);
            if (++drawn >= 128) break;
        }
        VisualLayers.endLineState();
    }

    private static void fillBox(Box b, int color) {
        int a = (color >>> 24) & 255, r = (color >> 16) & 255, g = (color >> 8) & 255, bl = color & 255;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
        quad(buf, r, g, bl, a, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.minX, b.maxY, b.minZ);
        quad(buf, r, g, bl, a, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.minY, b.maxZ);
        quad(buf, r, g, bl, a, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, b.minX, b.maxY, b.maxZ, b.minX, b.minY, b.maxZ);
        quad(buf, r, g, bl, a, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.maxY, b.minZ);
        quad(buf, r, g, bl, a, b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ, b.maxX, b.minY, b.minZ);
        quad(buf, r, g, bl, a, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ);
        tess.draw();
    }

    private static void quad(BufferBuilder buf, int r, int g, int b, int a, double... xyz) {
        for (int i = 0; i < xyz.length; i += 3) {
            buf.vertex(xyz[i], xyz[i + 1], xyz[i + 2]).color(r, g, b, a).next();
        }
    }
}
