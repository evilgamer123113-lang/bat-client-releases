package com.batclient;

import com.google.gson.JsonObject;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import org.joml.Matrix4f;
import net.minecraft.client.gui.DrawContext;
import com.mojang.blaze3d.systems.RenderSystem;

public final class NativeNametags {
    public static final Identifier LOGO = Identifier.of("batclient", "textures/gui/bat-logo.png");
    public static final String BADGE_SPACE = "      ";
    public static final int BADGE_GAP = 5;
    public static JsonObject settings() { return HudConfig.module(HudConfig.snapshot(), "nametags"); }
    public static boolean enabled() { JsonObject mod = settings(); return mod == null || HudConfig.enabled(mod); }
    public static Text withLogo(Text name) {
        return Text.empty().setStyle(Style.EMPTY.withInsertion("batclient:badge")).append(BADGE_SPACE).append(name);
    }
    public static Text ownName(Text name) { return HudConfig.bool(settings(), "logo", true) ? withLogo(name) : name; }
    public static boolean hasLogo(Text text) { return "batclient:badge".equals(text.getStyle().getInsertion()); }

    /** Direct texture vertices avoid font-atlas size limits and missing glyphs. */
    public static void drawLogo(Text text, float x, float y, int color, Matrix4f matrix,
                                VertexConsumerProvider vertices, TextRenderer.TextLayerType layer, int light) {
        if (!hasLogo(text)) return;
        float width = Math.max(1, Math.min(16, MinecraftClient.getInstance().textRenderer.getWidth(BADGE_SPACE) - 2 - BADGE_GAP));
        var buffer = vertices.getBuffer(layer == TextRenderer.TextLayerType.SEE_THROUGH
                ? RenderLayer.getTextSeeThrough(LOGO) : RenderLayer.getText(LOGO));
        float left = x + 2, right = left + width, top = y, bottom = y + 8;
        // Vanilla's nameplate writes depth at z=.01; text shadows advance to z=.03.
        // The separate logo layer must sit in front of both, not at z=0 behind them.
        buffer.vertex(matrix, left, top, .05f).color(color).texture(.10f, .31f).light(light);
        buffer.vertex(matrix, left, bottom, .05f).color(color).texture(.10f, .69f).light(light);
        buffer.vertex(matrix, right, bottom, .05f).color(color).texture(.90f, .69f).light(light);
        buffer.vertex(matrix, right, top, .05f).color(color).texture(.90f, .31f).light(light);
    }

    public static void drawGuiLogo(DrawContext ctx, Text name, int x, int y) {
        if (!hasLogo(name)) return;
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        ctx.drawTexture(LOGO, x + 2, y, Math.max(1, Math.min(16, MinecraftClient.getInstance().textRenderer.getWidth(BADGE_SPACE) - 2 - BADGE_GAP)),
                8, 51f, 159f, 410, 194, 512, 512);
        RenderSystem.disableBlend();
    }
}
