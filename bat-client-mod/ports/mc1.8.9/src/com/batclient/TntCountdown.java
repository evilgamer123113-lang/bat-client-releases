package com.batclient;

import com.batclient.platform.mc189.MatrixStack;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.TntEntity;

/** Entity-relative TNT countdown label for 1.8 / 1.8.9. */
public final class TntCountdown {
    private TntCountdown() {}

    public static String text(float fuse, boolean ticks) {
        return ticks ? Math.max(0, (int) Math.ceil(fuse)) + " ticks" : String.format(Locale.ROOT, "%.1fs", Math.max(0, fuse) / 20f);
    }

    public static void render(TntEntity entity, float delta, MatrixStack matrices) {
        MinecraftClient c = MinecraftClient.getInstance();
        JsonObject mod = VisualModules.settings("tntcountdown");
        if (!HudConfig.enabled(mod) || c.player == null || !MinecraftClient.isHudEnabled() || c.currentScreen != null) return;
        float range = VisualModules.number(mod, "range", 32, 4, 64);
        if (c.player.squaredDistanceTo(entity) > range * range) return;
        float fuse = Math.max(0, entity.fuseTimer - delta);
        int color = HudRenderer.parseColor(HudConfig.str(mod, fuse <= 20 ? "urgentColor" : "color", fuse <= 20 ? "#FF5555FF" : "#FFFFFFFF"), 0xFFFFFFFF);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(0, entity.height + .45, 0);
            GlStateManager.rotate(-c.getEntityRenderManager().yaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(c.getEntityRenderManager().pitch, 1.0F, 0.0F, 0.0F);
            float scale = VisualModules.number(mod, "scale", 1, .5f, 2);
            GlStateManager.scale(-.025f * scale, -.025f * scale, .025f * scale);
            GlStateManager.disableLighting();
            String value = text(fuse, HudConfig.bool(mod, "ticks", false));
            float x = -c.textRenderer.getStringWidth(value) / 2f;
            boolean through = HudConfig.bool(mod, "throughWalls", false);
            if (through) GlStateManager.disableDepthTest();
            c.textRenderer.draw(value, x, 0, color, HudConfig.bool(mod, "shadow", true));
            if (through) GlStateManager.enableDepthTest();
            GlStateManager.enableLighting();
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
