package com.batclient.mixin;

import com.batclient.HudConfig;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HeldItemRenderer.class)
public class LowFireMixin {
    @Redirect(method = "renderFireOverlay", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;translate(FFF)V"))
    private void bat$lowerFire(float x, float y, float z) {
        JsonObject mod = HudConfig.module(HudConfig.snapshot(), "lowfire");
        if (HudConfig.enabled(mod)) {
            float height = HudConfig.setNumber(mod, "height", .5f);
            if (!Float.isFinite(height)) height = .5f;
            y -= (1 - MathHelper.clamp(height, .1f, 1f)) * .9f;
        }
        GlStateManager.translate(x, y, z);
    }
}
