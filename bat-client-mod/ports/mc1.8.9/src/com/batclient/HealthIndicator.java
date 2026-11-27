package com.batclient;

import com.batclient.hud.HealthValues;
import com.batclient.platform.mc1194.DrawContext;
import com.batclient.platform.mc189.MatrixStack;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

/** Vanilla heart sprites and billboard labels, using only client-known health. */
public final class HealthIndicator {
    private HealthIndicator() { }

    public static JsonObject settings() {
        return HudConfig.module(HudConfig.snapshot(), "healthindicator");
    }

    public static String label(float health, float maximum, float absorption, JsonObject mod) {
        String value = HudConfig.str(mod, "style", "Heart value").equals("Heart row")
            ? HealthValues.row(health, maximum)
            : HealthValues.hearts(health, maximum, HudConfig.bool(mod, "showMax", true));
        if (HudConfig.bool(mod, "absorption", true) && HealthValues.absorption(absorption) > 0) {
            value += " +" + String.format(Locale.ROOT, "%.1f", HealthValues.absorption(absorption) / 2) + " ♥";
        }
        return value;
    }

    public static void render(LivingEntity entity, boolean normalLabel, double x, double y, double z) {
        JsonObject mod = settings();
        if (!HudConfig.enabled(mod)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.currentScreen != null || !MinecraftClient.isHudEnabled()
            || !entity.isAlive() || entity.isInvisibleTo(client.player)) return;
        boolean player = entity instanceof PlayerEntity, own = entity == client.player;
        if (player ? !HudConfig.bool(mod, "players", true) : !HudConfig.bool(mod, "mobs", true)) return;
        if (own && (!HudConfig.bool(mod, "showOwn", false) || client.options.perspective == 0)) return;
        if (player && !own && !normalLabel) return;
        float range = Math.max(4, Math.min(64, HudConfig.setNumber(mod, "range", 24)));
        if (client.player.squaredDistanceTo(entity) > range * range) return;
        double scoreboardLift = player && !own && client.player.squaredDistanceTo(entity) < 100
            && entity.getWorld().getScoreboard().getObjectiveForSlot(2) != null ? .25875 : 0;
        float scale = .025f * Math.max(.5f, Math.min(2, HudConfig.setNumber(mod, "scale", 1)));

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((float) x, (float) (y + entity.height + .5 + scoreboardLift), (float) z);
            GlStateManager.rotate(-client.getEntityRenderManager().yaw, 0f, 1f, 0f);
            GlStateManager.rotate(client.getEntityRenderManager().pitch, 1f, 0f, 0f);
            GlStateManager.scale(-scale, -scale, scale);
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            boolean occluded = HudConfig.bool(mod, "occluded", true);
            if (occluded) GlStateManager.disableDepthTest();

            int color = HudRenderer.parseColor(HudConfig.str(mod, "color", "#FFFFFFFF"), 0xFFFFFFFF);
            if (HudConfig.bool(mod, "lowWarning", true) && HealthValues.ratio(entity.getHealth(), entity.getMaxHealth()) <= .25f) {
                color = 0xFFFF5555;
            }
            int background = HudConfig.bool(mod, "background", true) ? 0x55000000 : 0;
            boolean ownNamePresent = own && NativeNametags.enabled() && HudConfig.bool(NativeNametags.settings(), "showOwn", true);
            if ((!player && !normalLabel && HudConfig.bool(mod, "mobNames", true)) || (own && !ownNamePresent)) {
                drawText(entity.getName().asFormattedString(), 0, 0xFFFFFFFF, mod, background);
            }
            int count = heartCount(entity.getMaxHealth(), entity.getAbsorption(), mod);
            int rows = (count + 9) / 10;
            int top = -3 - rows * 10 - (HudConfig.bool(mod, "showValue", false) ? 11 : 0);
            drawHearts(entity.getHealth(), entity.getMaxHealth(), entity.getAbsorption(), mod, top);
            if (HudConfig.bool(mod, "showValue", false)) {
                drawText(label(entity.getHealth(), entity.getMaxHealth(), entity.getAbsorption(), mod), -12, color, mod, background);
            }
            if (occluded) GlStateManager.enableDepthTest();
            GlStateManager.enableLighting();
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static void drawText(String text, int y, int color, JsonObject mod, int background) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int x = Math.round(-renderer.getStringWidth(text) / 2f);
        if (background != 0) {
            GlStateManager.disableTexture();
            int w = renderer.getStringWidth(text);
            int a = (background >>> 24) & 255, r = (background >> 16) & 255, g = (background >> 8) & 255, b = background & 255;
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuffer();
            buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
            buf.vertex(x - 1, y - 1, 0).color(r, g, b, a).next();
            buf.vertex(x - 1, y + 9, 0).color(r, g, b, a).next();
            buf.vertex(x + w + 1, y + 9, 0).color(r, g, b, a).next();
            buf.vertex(x + w + 1, y - 1, 0).color(r, g, b, a).next();
            tess.draw();
            GlStateManager.enableTexture();
        }
        renderer.draw(text, (float) x, (float) y, color, HudConfig.bool(mod, "shadow", true));
    }

    private static int heartCount(float maximum, float absorption, JsonObject mod) {
        return HealthValues.slots(maximum) + (HudConfig.bool(mod, "absorption", true) ? Math.min(10, (int) Math.ceil(HealthValues.absorption(absorption) / 2)) : 0);
    }

    public static int previewWidth(JsonObject mod) {
        return Math.max(81, HudConfig.bool(mod, "showValue", false) ? MinecraftClient.getInstance().textRenderer.getStringWidth(label(14, 20, 2, mod)) : 0);
    }

    public static int previewHeight(JsonObject mod) {
        return 33 + (HudConfig.bool(mod, "showValue", false) ? 11 : 0);
    }

    private static int heartU(float health, float maximum, int slot, boolean gold) {
        int fill = HealthValues.fill(health, maximum, slot);
        return fill == 0 ? -1 : (gold ? (fill == 1 ? 169 : 160) : (fill == 1 ? 61 : 52));
    }

    private static void drawHearts(float health, float maximum, float absorption, JsonObject mod, int top) {
        MinecraftClient.getInstance().getTextureManager().bindTexture(DrawableHelper.GUI_ICONS_TEXTURE);
        GlStateManager.color(1f, 1f, 1f, 1f);
        int base = HealthValues.slots(maximum), count = heartCount(maximum, absorption, mod);
        for (int i = 0; i < count; i++) {
            int columns = Math.min(10, count - (i / 10) * 10);
            float x = (i % 10) * 8 - (columns * 8 + 1) / 2f, y = top + (i / 10) * 10;
            int u = i < base ? heartU(health, maximum, i, false)
                : heartU(absorption, Math.max(2, (float) Math.ceil(absorption / 2) * 2), i - base, true);
            quad(x, y, 16, 0);
            if (u >= 0) quad(x, y, u, 0);
        }
    }

    private static void quad(float x, float y, int u, int v) {
        float f = 1f / 256f;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE);
        buf.vertex(x, y, 0).texture(u * f, v * f).next();
        buf.vertex(x, y + 9, 0).texture(u * f, (v + 9) * f).next();
        buf.vertex(x + 9, y + 9, 0).texture((u + 9) * f, (v + 9) * f).next();
        buf.vertex(x + 9, y, 0).texture((u + 9) * f, v * f).next();
        tess.draw();
    }

    public static void drawPreview(DrawContext ctx, JsonObject mod) {
        int width = previewWidth(mod), count = heartCount(20, 2, mod);
        MinecraftClient.getInstance().getTextureManager().bindTexture(DrawableHelper.GUI_ICONS_TEXTURE);
        for (int i = 0; i < count; i++) {
            int columns = Math.min(10, count - (i / 10) * 10);
            int x = (width - (columns * 8 + 1)) / 2 + (i % 10) * 8, y = (i / 10) * 10;
            ctx.drawTexture(DrawableHelper.GUI_ICONS_TEXTURE, x, y, 16, 0, 9, 9);
            int u = i < 10 ? heartU(14, 20, i, false) : 160;
            if (u >= 0) ctx.drawTexture(DrawableHelper.GUI_ICONS_TEXTURE, x, y, u, 0, 9, 9);
        }
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int y = 22;
        if (HudConfig.bool(mod, "showValue", false)) {
            String value = label(14, 20, 2, mod);
            ctx.drawText(renderer, value, (width - renderer.getStringWidth(value)) / 2, y, 0xFFFFFFFF, HudConfig.bool(mod, "shadow", true));
            y += 11;
        }
        ctx.drawText(renderer, "Player", (width - renderer.getStringWidth("Player")) / 2, y, 0xFFFFFFFF, HudConfig.bool(mod, "shadow", true));
    }
}
