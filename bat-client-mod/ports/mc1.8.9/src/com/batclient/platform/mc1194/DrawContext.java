package com.batclient.platform.mc1194;

import com.batclient.platform.mc189.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

/** Genuine 1.8.9 DrawableHelper + ItemRenderer + GlStateManager adapter. */
public final class DrawContext {
    private static final DrawableHelper HELPER = new DrawableHelper() { };
    private final MinecraftClient client;
    private final MatrixStack matrices;

    public DrawContext(MatrixStack matrices) {
        this.client = MinecraftClient.getInstance();
        this.matrices = matrices != null ? matrices : new MatrixStack();
    }

    public DrawContext(MinecraftClient client, Object consumers) {
        this.client = client != null ? client : MinecraftClient.getInstance();
        this.matrices = new MatrixStack();
    }

    public MatrixStack getMatrices() {
        return matrices;
    }

    public int getScaledWindowWidth() {
        return (int) new Window(client).getScaledWidth();
    }

    public int getScaledWindowHeight() {
        return (int) new Window(client).getScaledHeight();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(x1, y1, x2, y2, color);
    }

    public void drawBorder(int x, int y, int width, int height, int color) {
        DrawableHelper.fill(x, y, x + width, y + 1, color);
        DrawableHelper.fill(x, y + height - 1, x + width, y + height, color);
        DrawableHelper.fill(x, y + 1, x + 1, y + height - 1, color);
        DrawableHelper.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        Window win = new Window(client);
        int scale = win.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
            x1 * scale,
            client.height - y2 * scale,
            Math.max(0, (x2 - x1) * scale),
            Math.max(0, (y2 - y1) * scale)
        );
    }

    public void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public void setShaderColor(float r, float g, float b, float a) {
        GlStateManager.color(r, g, b, a);
    }

    public void draw() {
        // Immediate-mode in 1.8.9
    }

    public int drawText(TextRenderer tr, String text, int x, int y, int color, boolean shadow) {
        if (text == null) return 0;
        return shadow ? tr.drawWithShadow(text, (float) x, (float) y, color) : tr.draw(text, x, y, color);
    }

    public int drawText(TextRenderer tr, Text text, int x, int y, int color, boolean shadow) {
        return drawText(tr, text == null ? "" : text.asFormattedString(), x, y, color, shadow);
    }

    public int drawTextWithShadow(TextRenderer tr, String text, int x, int y, int color) {
        if (text == null) return 0;
        return tr.drawWithShadow(text, (float) x, (float) y, color);
    }

    public int drawTextWithShadow(TextRenderer tr, Text text, int x, int y, int color) {
        return drawTextWithShadow(tr, text == null ? "" : text.asFormattedString(), x, y, color);
    }

    public void drawCenteredTextWithShadow(TextRenderer tr, String text, int centerX, int y, int color) {
        if (text == null) return;
        tr.drawWithShadow(text, (float) (centerX - tr.getStringWidth(text) / 2), (float) y, color);
    }

    public void drawCenteredTextWithShadow(TextRenderer tr, Text text, int centerX, int y, int color) {
        drawCenteredTextWithShadow(tr, text == null ? "" : text.asFormattedString(), centerX, y, color);
    }

    public void drawTexture(Identifier texture, int x, int y, int u, int v, int width, int height) {
        client.getTextureManager().bindTexture(texture);
        HELPER.drawTexture(x, y, u, v, width, height);
    }

    public void drawTexture(Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        client.getTextureManager().bindTexture(texture);
        DrawableHelper.drawTexture(x, y, u, v, width, height, (float) textureWidth, (float) textureHeight);
    }

    public void drawTexture(Identifier texture, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        client.getTextureManager().bindTexture(texture);
        DrawableHelper.drawTexture(x, y, u, v, regionWidth, regionHeight, width, height, (float) textureWidth, (float) textureHeight);
    }

    public void drawSprite(int x, int y, int z, int width, int height, Sprite sprite) {
        if (sprite == null) return;
        client.getTextureManager().bindTexture(new Identifier("textures/atlas/blocks.png"));
        HELPER.drawSprite(x, y, sprite, width, height);
    }

    public void drawItem(ItemStack item, int x, int y) {
        if (item != null) client.getItemRenderer().renderInGuiWithOverrides(item, x, y);
    }

    public void drawItemInSlot(TextRenderer tr, ItemStack stack, int x, int y) {
        if (stack != null) client.getItemRenderer().renderGuiItemOverlay(tr, stack, x, y);
    }

    public void drawItemInSlot(TextRenderer tr, ItemStack stack, int x, int y, String countOverride) {
        if (stack != null) client.getItemRenderer().renderGuiItemOverlay(tr, stack, x, y, countOverride);
    }

    public void drawTooltip(TextRenderer tr, Text text, int x, int y) {
        if (text == null) return;
        List<String> lines = new ArrayList<>();
        lines.add(text.asFormattedString());
        drawTooltipStrings(tr, lines, x, y);
    }

    public void drawTooltip(TextRenderer tr, List<Text> text, int x, int y) {
        if (text == null || text.isEmpty()) return;
        List<String> lines = new ArrayList<>(text.size());
        for (Text t : text) lines.add(t.asFormattedString());
        drawTooltipStrings(tr, lines, x, y);
    }

    public void drawItemTooltip(TextRenderer tr, ItemStack stack, int x, int y) {
        if (stack == null || client.player == null) return;
        List<String> lines = stack.getTooltip(client.player, client.options.advancedItemTooltips);
        drawTooltipStrings(tr, lines, x, y);
    }

    private void drawTooltipStrings(TextRenderer tr, List<String> lines, int x, int y) {
        if (lines.isEmpty()) return;
        int w = 0;
        for (String s : lines) {
            int sw = tr.getStringWidth(s);
            if (sw > w) w = sw;
        }
        int px = x + 12;
        int py = y - 12;
        int h = 8 + (lines.size() > 1 ? 2 + (lines.size() - 1) * 10 : 0);
        int sw = getScaledWindowWidth();
        int sh = getScaledWindowHeight();
        if (px + w > sw) px -= 28 + w;
        if (py + h + 6 > sh) py = sh - h - 6;
        GlStateManager.disableDepthTest();
        fill(px - 3, py - 4, px + w + 3, py - 3, 0xF0100010);
        fill(px - 3, py + h + 3, px + w + 3, py + h + 4, 0xF0100010);
        fill(px - 3, py - 3, px + w + 3, py + h + 3, 0xF0100010);
        fill(px - 4, py - 3, px - 3, py + h + 3, 0xF0100010);
        fill(px + w + 3, py - 3, px + w + 4, py + h + 3, 0xF0100010);
        int ty = py;
        for (int i = 0; i < lines.size(); i++) {
            tr.drawWithShadow(lines.get(i), (float) px, (float) ty, -1);
            if (i == 0) ty += 2;
            ty += 10;
        }
        GlStateManager.enableDepthTest();
    }
}
