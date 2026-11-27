package com.batclient;

import com.batclient.hud.FrameWorkBudget;
import com.batclient.hud.TerrainMapRaster;
import com.batclient.platform.mc1194.DrawContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.material.Material;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.Chunk;

/** Surface terrain from loaded client chunks only; no entities, cave scan or network requests. */
public final class MinimapHud {
    private static final int RES = TerrainMapRaster.SIZE;
    private static final TerrainMapRaster RASTER = new TerrainMapRaster();
    private static final FrameWorkBudget SAMPLE_BUDGET = new FrameWorkBudget();
    private static final Identifier LIVE = new Identifier("batclient", "dynamic/minimap_live");
    private static final Identifier PREVIEW = new Identifier("batclient", "dynamic/minimap_preview");
    private static NativeImageBackedTexture liveTexture, previewTexture;
    private static Object world;
    private static long next;

    private MinimapHud() { }

    public static void tick(MinecraftClient client) {
        JsonObject mod = VisualModules.settings("minimap");
        if (world != client.world || client.world == null || client.player == null || !HudConfig.enabled(mod)) {
            RASTER.clear();
            world = client.world;
            next = 0;
            if (client.world == null || client.player == null || !HudConfig.enabled(mod)) return;
        }
        int radius = (int) VisualModules.number(mod, "range", 48, 16, 96);
        int x = MathHelper.floor(client.player.x);
        int z = MathHelper.floor(client.player.z);
        long now = MinecraftClient.getTime();
        TerrainMapRaster.Frame frame = RASTER.frame();
        if (!RASTER.building() && (frame == null || frame.radius() != radius || Math.abs(frame.x() - x) > 2 || Math.abs(frame.z() - z) > 2 || now >= next)) {
            RASTER.begin(x, z, radius);
        }
        String upd = HudConfig.str(mod, "updates", "Balanced");
        int budget = "Low".equals(upd) ? 512 : ("Fast".equals(upd) ? 2048 : 1024);
        SAMPLE_BUDGET.begin(System.nanoTime(), .75f);
        if (RASTER.step(budget, (wx, wz) -> sample(client, mod, wx, wz), SAMPLE_BUDGET)) {
            if (liveTexture == null) liveTexture = create(client, LIVE);
            int[] pixels = RASTER.frame().pixels();
            int[] dst = liveTexture.getPixels();
            System.arraycopy(pixels, 0, dst, 0, Math.min(pixels.length, dst.length));
            liveTexture.upload();
            next = now + 250;
        }
    }

    private static int elevation(MinecraftClient c, int x, int z, int fallback) {
        Chunk chunk = c.world.getChunk(x >> 4, z >> 4);
        return chunk == null || chunk.isEmpty() ? fallback : chunk.getHighestBlockY(x & 15, z & 15);
    }

    private static int sample(MinecraftClient client, JsonObject mod, int x, int z) {
        Chunk chunk = client.world.getChunk(x >> 4, z >> 4);
        if (chunk == null || chunk.isEmpty()) return 0xFF20252B;
        int y = chunk.getHighestBlockY(x & 15, z & 15);
        if (y <= 0) return 0xFF20252B;
        BlockPos pos = new BlockPos(x, y - 1, z);
        BlockState state = client.world.getBlockState(pos);
        Block block = state.getBlock();
        int rgb = block.getMaterial().getColor().color;
        boolean water = block.getMaterial() == Material.WATER;
        if (HudConfig.bool(mod, "biomeColors", true)) {
            if (water) rgb = BiomeColors.getWaterColor(client.world, pos);
            else if (block == Blocks.GRASS || block instanceof LeavesBlock) {
                int tint = block.getBlockColor(client.world, pos, 0);
                if (tint >= 0) rgb = tint;
            }
        }
        float shade = 1f;
        if (HudConfig.bool(mod, "relief", true)) {
            int north = elevation(client, x, z - 1, y);
            int west = elevation(client, x - 1, z, y);
            shade = MathHelper.clamp((float) (.96 + (north - y) * .095 + (west - y) * .065), .62f, 1.28f);
            if (HudConfig.bool(mod, "contours", false) && Math.floorDiv(y, 8) != Math.floorDiv(north, 8)) shade *= .72f;
        }
        if (water) {
            int depth = 0;
            for (int down = 1; down <= 8 && y - 1 - down >= 0; down++) {
                if (client.world.getBlockState(new BlockPos(x, y - 1 - down, z)).getBlock().getMaterial() != Material.WATER) break;
                depth++;
            }
            shade *= 1f - depth * .035f;
        }
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * shade));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * shade));
        int b = Math.min(255, (int) ((rgb & 255) * shade));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static NativeImageBackedTexture create(MinecraftClient client, Identifier id) {
        NativeImageBackedTexture texture = new NativeImageBackedTexture(RES, RES);
        client.getTextureManager().loadTexture(id, texture);
        return texture;
    }

    private static void preparePreview(MinecraftClient client) {
        if (previewTexture != null) return;
        previewTexture = create(client, PREVIEW);
        TerrainMapRaster raster = new TerrainMapRaster();
        raster.begin(0, 0, 32);
        while (!raster.step(2048, MinimapHud::previewColor)) { }
        int[] pixels = raster.frame().pixels();
        int[] dst = previewTexture.getPixels();
        System.arraycopy(pixels, 0, dst, 0, Math.min(pixels.length, dst.length));
        previewTexture.upload();
    }

    private static int previewColor(int x, int z) {
        double river = Math.abs(x - 8 * Math.sin(z * .065) - 3 * Math.sin(z * .17));
        if (river < 4) return river < 2 ? 0xFF285C91 : 0xFF447FA9;
        if (river < 5) return 0xFFCEC394;
        int cx = Math.floorDiv(x, 7), cz = Math.floorDiv(z, 7), seed = (cx * 73428767) ^ (cz * 912931);
        seed ^= seed >>> 13;
        double dx = x - (cx * 7 + 2 + Math.floorMod(seed, 3)), dz = z - (cz * 7 + 2 + Math.floorMod(seed >> 5, 3));
        boolean tree = Math.floorMod(seed, 5) < 2 && dx * dx + dz * dz < 6;
        int rgb = tree ? 0x315C37 : 0x7BA651;
        float shade = (float) (.90 + .1 * Math.sin(x * .17) * Math.cos(z * .12) + .05 * Math.sin((x + z) * .37));
        return 0xFF000000 | ((int) (((rgb >> 16) & 255) * shade) << 16) | ((int) (((rgb >> 8) & 255) * shade) << 8) | (int) ((rgb & 255) * shade);
    }

    public static int size(JsonObject mod) {
        return (int) VisualModules.number(mod, "size", 128, 80, 224);
    }

    public static int height(JsonObject mod) {
        return size(mod) + (HudConfig.bool(mod, "coords", true) ? 14 : 0) + (HudConfig.bool(mod, "biome", false) ? 12 : 0);
    }

    public static void draw(DrawContext ctx, MinecraftClient client, JsonObject mod, boolean preview) {
        int size = size(mod);
        TerrainMapRaster.Frame frame = RASTER.frame();
        if (preview) preparePreview(client);
        boolean ready = preview || (liveTexture != null && frame != null);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        if (ready) ctx.drawTexture(preview ? PREVIEW : LIVE, 0, 0, size, size, 0, 0, RES, RES, RES, RES);
        else {
            ctx.fill(0, 0, size, size, 0xFF20252B);
            ctx.drawText(client.textRenderer, "Mapping...", 6, size / 2 - 4, 0xFFAAAAAA, true);
        }
        if (HudConfig.bool(mod, "grid", false)) {
            for (int n = 1; n < 4; n++) {
                int p = n * size / 4;
                ctx.fill(p, 0, p + 1, size, 0x22FFFFFF);
                ctx.fill(0, p, size, p + 1, 0x22FFFFFF);
            }
        }
        if (HudConfig.bool(mod, "directions", true)) {
            ctx.drawCenteredTextWithShadow(client.textRenderer, "N", size / 2, 3, 0xFFFFFFFF);
            ctx.drawCenteredTextWithShadow(client.textRenderer, "S", size / 2, size - 11, 0xFFFFFFFF);
            ctx.drawText(client.textRenderer, "W", 3, size / 2 - 4, 0xFFFFFFFF, true);
            ctx.drawText(client.textRenderer, "E", size - 9, size / 2 - 4, 0xFFFFFFFF, true);
        }
        if (!preview && frame != null) WaypointRenderer.minimap(ctx, client, frame.x(), frame.z(), frame.radius(), size);
        float px = size / 2f, pz = size / 2f;
        if (!preview && frame != null && client.player != null) {
            px += (float) (client.player.x - frame.x()) * size / (frame.radius() * 2);
            pz += (float) (client.player.z - frame.z()) * size / (frame.radius() * 2);
        }
        px = MathHelper.clamp(px, 10, size - 10);
        pz = MathHelper.clamp(pz, 10, size - 10);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(px, pz, 10f);
            GlStateManager.rotate((client.player == null ? 0 : client.player.yaw) + 180f, 0f, 0f, 1f);
            GlStateManager.color(0, 0, 0, .85f);
            ctx.drawTexture(NativeNametags.LOGO, -9, -3, 20, 10, 51f, 159f, 410, 194, 512, 512);
            GlStateManager.color(1, 1, 1, 1);
            ctx.drawTexture(NativeNametags.LOGO, -10, -4, 20, 10, 51f, 159f, 410, 194, 512, 512);
        } finally {
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.popMatrix();
        }
        if (HudConfig.bool(mod, "coords", true)) {
            String coords = client.player == null ? "X: 0 Y: 64 Z: 0"
                : "X: " + MathHelper.floor(client.player.x) + " Y: " + MathHelper.floor(client.player.y) + " Z: " + MathHelper.floor(client.player.z);
            ctx.drawText(client.textRenderer, client.textRenderer.trimToWidth(coords, size), 0, size + 4,
                HudRenderer.parseColor(HudConfig.str(mod, "color", "#FFFFFFFF"), 0xFFFFFFFF), HudConfig.bool(mod, "shadow", true));
        }
        if (HudConfig.bool(mod, "biome", false)) {
            String biome = preview || client.player == null ? "Forest"
                : client.world.getBiome(client.player.getBlockPos()).name;
            ctx.drawText(client.textRenderer, client.textRenderer.trimToWidth(biome, size), 0,
                size + 4 + (HudConfig.bool(mod, "coords", true) ? 12 : 0), 0xFFBBBBBB, HudConfig.bool(mod, "shadow", true));
        }
    }

    public static void close(MinecraftClient client) {
        if (liveTexture != null) { liveTexture.clearGlId(); liveTexture = null; }
        if (previewTexture != null) { previewTexture.clearGlId(); previewTexture = null; }
        RASTER.clear();
        world = null;
    }
}
