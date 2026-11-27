package com.batclient;

import com.batclient.platform.mc1194.DrawContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

/** Native nametag badge helper for 1.8 / 1.8.9. */
public final class NativeNametags {
    public static final Identifier LOGO = new Identifier("batclient", "textures/gui/bat-logo.png");
    public static final String BADGE_SPACE = "      ";
    public static final int BADGE_GAP = 5;

    private NativeNametags() {}

    public static JsonObject settings() { return HudConfig.module(HudConfig.snapshot(), "nametags"); }
    public static boolean enabled() { JsonObject mod = settings(); return mod == null || HudConfig.enabled(mod); }

    public static Text withLogo(Text name) {
        return new LiteralText("").setStyle(new Style().setInsertion("batclient:badge")).append(BADGE_SPACE).append(name);
    }

    public static Text ownName(Text name) {
        return HudConfig.bool(settings(), "logo", true) ? withLogo(name) : name;
    }

    public static boolean hasLogo(Text text) {
        return text != null && text.getStyle() != null && "batclient:badge".equals(text.getStyle().getInsertion());
    }

    public static void drawLogo(Text text, float x, float y, int color, boolean seeThrough) {
        if (!hasLogo(text)) return;
        float width = Math.max(1, Math.min(16, MinecraftClient.getInstance().textRenderer.getStringWidth(BADGE_SPACE) - 2 - BADGE_GAP));
        float left = x + 2, right = left + width, top = y, bottom = y + 8;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF, a = (color >>> 24) & 0xFF;
        MinecraftClient.getInstance().getTextureManager().bindTexture(LOGO);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        if (seeThrough) GlStateManager.disableDepthTest();
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buf.vertex(left, top, -0.05).texture(.10f, .31f).color(r, g, b, a).next();
        buf.vertex(left, bottom, -0.05).texture(.10f, .69f).color(r, g, b, a).next();
        buf.vertex(right, bottom, -0.05).texture(.90f, .69f).color(r, g, b, a).next();
        buf.vertex(right, top, -0.05).texture(.90f, .31f).color(r, g, b, a).next();
        Tessellator.getInstance().draw();
        if (seeThrough) GlStateManager.enableDepthTest();
        GlStateManager.disableBlend();
    }

    public static void drawGuiLogo(DrawContext ctx, Text name, int x, int y) {
        if (!hasLogo(name)) return;
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        ctx.drawTexture(LOGO, x + 2, y, Math.max(1, Math.min(16, MinecraftClient.getInstance().textRenderer.getStringWidth(BADGE_SPACE) - 2 - BADGE_GAP)),
            8, 51f, 159f, 410, 194, 512, 512);
        GlStateManager.disableBlend();
    }
}
