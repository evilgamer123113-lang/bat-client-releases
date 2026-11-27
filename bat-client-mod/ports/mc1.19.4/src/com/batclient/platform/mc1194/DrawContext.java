package com.batclient.platform.mc1194;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Genuine 1.19.4 MatrixStack + DrawableHelper + ItemRenderer + Screen adapter. */
public final class DrawContext {
    private final MinecraftClient client;
    private final MatrixStack matrices;

    public DrawContext(MatrixStack matrices) {
        this.client = MinecraftClient.getInstance();
        this.matrices = matrices;
    }

    public DrawContext(MinecraftClient client, Object consumers) {
        this.client = client != null ? client : MinecraftClient.getInstance();
        this.matrices = new MatrixStack();
    }

    public MatrixStack getMatrices() {
        return matrices;
    }

    public int getScaledWindowWidth() {
        return client.getWindow().getScaledWidth();
    }

    public int getScaledWindowHeight() {
        return client.getWindow().getScaledHeight();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(matrices, x1, y1, x2, y2, color);
    }

    public void drawBorder(int x, int y, int width, int height, int color) {
        DrawableHelper.drawBorder(matrices, x, y, width, height, color);
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        DrawableHelper.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        DrawableHelper.disableScissor();
    }

    public void setShaderColor(float r, float g, float b, float a) {
        RenderSystem.setShaderColor(r, g, b, a);
    }

    public void draw() {
        client.getBufferBuilders().getEntityVertexConsumers().draw();
    }

    public int drawText(TextRenderer tr, String text, int x, int y, int color, boolean shadow) {
        if (text == null) return 0;
        return shadow ? tr.drawWithShadow(matrices, text, (float) x, (float) y, color) : tr.draw(matrices, text, (float) x, (float) y, color);
    }

    public int drawText(TextRenderer tr, Text text, int x, int y, int color, boolean shadow) {
        return shadow ? tr.drawWithShadow(matrices, text, (float) x, (float) y, color) : tr.draw(matrices, text, (float) x, (float) y, color);
    }

    public int drawText(TextRenderer tr, OrderedText text, int x, int y, int color, boolean shadow) {
        return shadow ? tr.drawWithShadow(matrices, text, (float) x, (float) y, color) : tr.draw(matrices, text, (float) x, (float) y, color);
    }

    public int drawTextWithShadow(TextRenderer tr, String text, int x, int y, int color) {
        if (text == null) return 0;
        return tr.drawWithShadow(matrices, text, (float) x, (float) y, color);
    }

    public int drawTextWithShadow(TextRenderer tr, Text text, int x, int y, int color) {
        return tr.drawWithShadow(matrices, text, (float) x, (float) y, color);
    }

    public int drawTextWithShadow(TextRenderer tr, OrderedText text, int x, int y, int color) {
        return tr.drawWithShadow(matrices, text, (float) x, (float) y, color);
    }

    public void drawCenteredTextWithShadow(TextRenderer tr, String text, int centerX, int y, int color) {
        DrawableHelper.drawCenteredTextWithShadow(matrices, tr, text, centerX, y, color);
    }

    public void drawCenteredTextWithShadow(TextRenderer tr, Text text, int centerX, int y, int color) {
        DrawableHelper.drawCenteredTextWithShadow(matrices, tr, text, centerX, y, color);
    }

    public void drawTexture(Identifier texture, int x, int y, int u, int v, int width, int height) {
        RenderSystem.setShaderTexture(0, texture);
        DrawableHelper.drawTexture(matrices, x, y, u, v, width, height);
    }

    public void drawTexture(Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        RenderSystem.setShaderTexture(0, texture);
        DrawableHelper.drawTexture(matrices, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    public void drawTexture(Identifier texture, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        RenderSystem.setShaderTexture(0, texture);
        DrawableHelper.drawTexture(matrices, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    public void drawSprite(int x, int y, int z, int width, int height, Sprite sprite) {
        RenderSystem.setShaderTexture(0, sprite.getAtlasId());
        DrawableHelper.drawSprite(matrices, x, y, z, width, height, sprite);
    }

    public void drawItem(ItemStack item, int x, int y) {
        client.getItemRenderer().renderInGuiWithOverrides(matrices, item, x, y);
    }

    public void drawItemInSlot(TextRenderer tr, ItemStack stack, int x, int y) {
        client.getItemRenderer().renderGuiItemOverlay(matrices, tr, stack, x, y);
    }

    public void drawItemInSlot(TextRenderer tr, ItemStack stack, int x, int y, String countOverride) {
        client.getItemRenderer().renderGuiItemOverlay(matrices, tr, stack, x, y, countOverride);
    }

    public void drawTooltip(TextRenderer tr, Text text, int x, int y) {
        if (client.currentScreen != null) {
            client.currentScreen.renderTooltip(matrices, text, x, y);
        }
    }

    public void drawTooltip(TextRenderer tr, List<Text> text, int x, int y) {
        if (client.currentScreen != null) {
            client.currentScreen.renderTooltip(matrices, text, x, y);
        }
    }

    public void drawItemTooltip(TextRenderer tr, ItemStack stack, int x, int y) {
        if (client.currentScreen != null) {
            client.currentScreen.renderTooltip(matrices, client.currentScreen.getTooltipFromItem(stack), stack.getTooltipData(), x, y);
        }
    }
}
