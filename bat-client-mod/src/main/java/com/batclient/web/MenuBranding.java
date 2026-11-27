package com.batclient.web;

import net.minecraft.client.gui.DrawContext;

/** A real web-rendered wordmark over native world/multiplayer screens. */
public final class MenuBranding {
    private static WebWindow badge;
    private static WebWindow icons;
    private static final UiFrameClock frameClock = new UiFrameClock();
    private static void ensure() {
        var controller = WebController.INSTANCE;
        if (!controller.isReady()) return;
        if (badge == null) {
            badge = new WebWindow(controller.getRenderer(), 272, 56);
            badge.loadUrl(WebAssetManager.webRoot().resolve("brand.html").toUri().toString());
            icons = new WebWindow(controller.getRenderer(), 128, 32);
            icons.loadUrl(WebAssetManager.webRoot().resolve("list-icons.html").toUri().toString());
        }
        boolean loading = !badge.hasPainted() || !icons.hasPainted() || badge.view().isLoading() || icons.view().isLoading();
        // Fonts/SVGs can paint after the first transparent frame. Keep pumping;
        // dirty-region uploads become no-ops once these static views settle.
        if (frameClock.shouldRender(System.nanoTime(), loading)) {
            controller.updateAndRender(); badge.renderAndUpload(); icons.renderAndUpload();
        }
    }
    public static void drawIcon(DrawContext ctx, int x, int y, int index) {
        ensure(); if (icons == null || !icons.hasPainted()) return;
        ctx.draw();
        ctx.getMatrices().push(); ctx.getMatrices().translate(0, 0, 200);
        icons.drawRegion(ctx, x, y, 16, 16, index / 4f, 0, (index + 1) / 4f, 1);
        ctx.getMatrices().pop();
    }
    public static void draw(DrawContext ctx) {
        ensure(); if (badge == null || !badge.hasPainted()) return;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        boolean narrow = sw < 620;
        float width = narrow ? 116 : 136;
        ctx.draw();
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 200);
        badge.draw(ctx, sw - width - 8, narrow ? 3 : sh - 34, width, width * 56 / 272);
        ctx.getMatrices().pop();
    }
    public static void close() {
        if (badge != null) { badge.close(); badge = null; }
        if (icons != null) { icons.close(); icons = null; }
        frameClock.invalidate();
    }
}
