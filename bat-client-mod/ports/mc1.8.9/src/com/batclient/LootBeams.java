package com.batclient;

import com.batclient.platform.ClientHookServices;
import com.batclient.platform.WorldFrame;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.*;

/** Bounded display for already-loaded drops. No entity glow flags, packets or chunk queries. */
public final class LootBeams {
    private static List<ItemEntity> visible = Collections.emptyList();
    private static Object world;
    private static int ticks;

    private LootBeams() { }

    public static void init() {
        ClientHookServices.get().afterEntities(LootBeams::render);
    }

    public static boolean valuable(ItemStack stack) {
        if (stack == null) return false;
        Item i = stack.getItem();
        return stack.hasEnchantments() || stack.getRarity() != Rarity.COMMON
            || i == Items.DIAMOND || i == Items.EMERALD || i == Item.fromBlock(Blocks.DIAMOND_BLOCK);
    }

    public static boolean accepts(ItemStack stack, String filter, int minimum) {
        return stack != null && stack.count >= minimum
            && (!(filter.equals("Rare") || filter.equals("Uncommon+")) || stack.getRarity() != Rarity.COMMON)
            && (!filter.equals("Valuables") || valuable(stack));
    }

    public static void tick(MinecraftClient c) {
        JsonObject mod = VisualModules.settings("lootbeams");
        if (c.world == null || c.player == null || !HudConfig.enabled(mod)) {
            visible = Collections.emptyList();
            world = c.world;
            return;
        }
        if (world != c.world) {
            world = c.world;
            ticks = 0;
            visible = Collections.emptyList();
        }
        if (++ticks % 5 != 0) return;
        double range = VisualModules.number(mod, "range", 24, 8, 64);
        int limit = (int) VisualModules.number(mod, "limit", 32, 8, 64);
        int minimum = (int) VisualModules.number(mod, "minimum", 1, 1, 64);
        String filter = HudConfig.str(mod, "filter", "All");
        PriorityQueue<ItemEntity> nearest = new PriorityQueue<>(Comparator.comparingDouble((ItemEntity e) -> e.squaredDistanceTo(c.player)).reversed());
        int scanned = 0;
        for (Entity entity : c.world.getLoadedEntities()) {
            if (++scanned > 2048) break;
            if (!(entity instanceof ItemEntity) || entity.removed || entity.squaredDistanceTo(c.player) > range * range || !accepts(((ItemEntity) entity).getItemStack(), filter, minimum)) continue;
            nearest.add((ItemEntity) entity);
            if (nearest.size() > limit) nearest.poll();
        }
        List<ItemEntity> sorted = new ArrayList<>(nearest);
        sorted.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(c.player)));
        visible = sorted;
    }

    public static int selectedCount() { return visible.size(); }

    private static int tint(ItemStack stack, JsonObject mod) {
        if (!HudConfig.bool(mod, "rarityColors", true)) {
            return HudRenderer.parseColor(HudConfig.str(mod, "color", "#75CFFFFF"), 0xFF75CFFF);
        }
        Rarity r = stack.getRarity();
        if (r == Rarity.UNCOMMON) return 0xFFFFD36E;
        if (r == Rarity.RARE) return 0xFF75CFFF;
        if (r == Rarity.EPIC) return 0xFFC18CFF;
        return 0xFFEDEDED;
    }

    private static void render(WorldFrame ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        JsonObject mod = VisualModules.settings("lootbeams");
        if (!HudConfig.enabled(mod) || c.player == null || c.world != world || c.currentScreen != null || !MinecraftClient.isHudEnabled()) return;
        Vec3d camera = ctx.cameraPos();
        float delta = ctx.tickDelta();
        float height = VisualModules.number(mod, "height", 2, .5f, 6);
        float width = VisualModules.number(mod, "width", .12f, .06f, .4f);
        float opacity = VisualModules.number(mod, "opacity", 65, 10, 100) / 100;
        double range = VisualModules.number(mod, "range", 24, 8, 64);
        int minimum = (int) VisualModules.number(mod, "minimum", 1, 1, 64);
        String filter = HudConfig.str(mod, "filter", "All");

        for (ItemEntity item : visible) {
            if (item.removed || item.squaredDistanceTo(c.player) > range * range || !accepts(item.getItemStack(), filter, minimum)) continue;
            double lx = item.prevTickX + (item.x - item.prevTickX) * delta;
            double ly = item.prevTickY + (item.y - item.prevTickY) * delta;
            double lz = item.prevTickZ + (item.z - item.prevTickZ) * delta;
            int rgb = tint(item.getItemStack(), mod);
            float alpha = opacity * ((rgb >>> 24) / 255f);
            if (HudConfig.bool(mod, "pulse", true)) alpha *= .85f + .15f * (float) Math.sin((item.getAge() + delta) * .08);

            float x = (float) (lx - camera.x), y = (float) (ly - camera.y) + .03f, z = (float) (lz - camera.z);
            int aBot = Math.round(alpha * 150), aTop = Math.round(alpha * 18);
            int rCol = (rgb >> 16) & 255, gCol = (rgb >> 8) & 255, bCol = rgb & 255;

            VisualLayers.beginPlate(false);
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuffer();
            buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
            for (int side = 0; side < 4; side++) {
                double a = side * Math.PI / 2, b = (side + 1) * Math.PI / 2;
                float ax = x + (float) Math.cos(a) * width, az = z + (float) Math.sin(a) * width;
                float bx = x + (float) Math.cos(b) * width, bz = z + (float) Math.sin(b) * width;
                buf.vertex(ax, y, az).color(rCol, gCol, bCol, aBot).next();
                buf.vertex(bx, y, bz).color(rCol, gCol, bCol, aBot).next();
                buf.vertex(bx, y + height, bz).color(rCol, gCol, bCol, aTop).next();
                buf.vertex(ax, y + height, az).color(rCol, gCol, bCol, aTop).next();
            }
            if (HudConfig.bool(mod, "groundGlow", true)) {
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI / 12, b = (i + 1) * Math.PI / 12;
                    float outer = width * 2, inner = outer * .75f;
                    buf.vertex(x + (float) Math.cos(a) * outer, y, z + (float) Math.sin(a) * outer).color(rCol, gCol, bCol, aBot).next();
                    buf.vertex(x + (float) Math.cos(b) * outer, y, z + (float) Math.sin(b) * outer).color(rCol, gCol, bCol, aBot).next();
                    buf.vertex(x + (float) Math.cos(b) * inner, y, z + (float) Math.sin(b) * inner).color(rCol, gCol, bCol, aBot).next();
                    buf.vertex(x + (float) Math.cos(a) * inner, y, z + (float) Math.sin(a) * inner).color(rCol, gCol, bCol, aBot).next();
                }
            }
            tess.draw();
            VisualLayers.endPlate();

            if (HudConfig.bool(mod, "labels", false)) {
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.translate(x, y + .45f, z);
                    GlStateManager.rotate(-c.getEntityRenderManager().yaw, 0f, 1f, 0f);
                    GlStateManager.rotate(c.getEntityRenderManager().pitch, 1f, 0f, 0f);
                    GlStateManager.scale(-.02f, -.02f, .02f);
                    String itemName = new TranslatableText(item.getItemStack().getTranslationKey() + ".name").asUnformattedString();
                    String text = c.textRenderer.trimToWidth(itemName, 110) + " x" + item.getItemStack().count;
                    c.textRenderer.draw(text, -c.textRenderer.getStringWidth(text) / 2f, 0f, 0xFFFFFFFF, true);
                } finally {
                    GlStateManager.popMatrix();
                }
            }
        }
    }
}
