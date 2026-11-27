package com.batclient;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL11;

/** Bounded native GL state helpers for 1.8 / 1.8.9 world-space lines and plates. */
public final class VisualLayers {
    private VisualLayers() {}

    public static void beginLineState(float width) {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GL11.glLineWidth(Math.max(1.0F, Math.min(4.0F, width)));
    }

    public static void endLineState() {
        GL11.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture();
        GlStateManager.popMatrix();
    }

    public static BufferBuilder beginLines(float width) {
        beginLineState(width);
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);
        return buf;
    }

    public static void endLines() {
        Tessellator.getInstance().draw();
        endLineState();
    }

    public static void beginPlate(boolean through) {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        if (through) GlStateManager.disableDepthTest();
        else GlStateManager.enableDepthTest();
    }

    public static void endPlate() {
        GlStateManager.enableDepthTest();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture();
        GlStateManager.popMatrix();
    }
}
