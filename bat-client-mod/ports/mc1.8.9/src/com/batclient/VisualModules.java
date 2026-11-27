package com.batclient;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.MathHelper;

/** Small native render controls for 1.8 / 1.8.9. */
public final class VisualModules {
    public static Object glintShader;
    private VisualModules() {}

    public static JsonObject settings(String id) { return HudConfig.module(HudConfig.snapshot(), id); }
    public static boolean enabled(String id) { return HudConfig.enabled(settings(id)); }
    public static float number(JsonObject mod, String key, float fallback, float min, float max) {
        float v = HudConfig.setNumber(mod, key, fallback);
        return Float.isFinite(v) ? MathHelper.clamp(v, min, max) : fallback;
    }

    public static float hurtAngle(float angle) {
        JsonObject mod = settings("hurtcamera");
        return HudConfig.enabled(mod) ? angle * number(mod, "intensity", 0, 0, 100) / 100 : angle;
    }

    public static float itemHeight(ItemEntity entity, float original) {
        JsonObject mod = settings("itemphysics");
        if (!HudConfig.enabled(mod) || (entity.isTouchingWater() && HudConfig.bool(mod, "water", true))) return original;
        return entity.getItemStack().getItem() instanceof BlockItem ? .13f : .045f;
    }

    public static void rotateItem(ItemEntity entity, float delta) {
        JsonObject mod = settings("itemphysics");
        if (!HudConfig.enabled(mod) || (entity.isTouchingWater() && HudConfig.bool(mod, "water", true))) return;
        float seed = (entity.getEntityId() * 137.508f) % 360;
        GlStateManager.rotate(seed, 0.0F, 1.0F, 0.0F);
        float spin = (entity.getAge() + (MinecraftClient.getInstance().isPaused() ? 0 : delta)) * number(mod, "speed", 1, 0, 3) * 18;
        GlStateManager.rotate(entity.onGround ? 90f : spin % 360, 1.0F, 0.0F, 0.0F);
    }

    public static Object glint(Object vanilla) {
        JsonObject mod = settings("glintcolor");
        if (!HudConfig.enabled(mod) || glintShader == null) return vanilla;
        return glintShader;
    }
}
