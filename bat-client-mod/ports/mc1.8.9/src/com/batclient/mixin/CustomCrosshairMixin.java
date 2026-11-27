package com.batclient.mixin;

import com.batclient.HudConfig;
import com.batclient.HudRenderer;
import com.batclient.platform.mc1194.DrawContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InGameHud.class)
public abstract class CustomCrosshairMixin {
    @Shadow protected abstract boolean showCrosshair();

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;showCrosshair()Z"))
    private boolean bat$crosshair(InGameHud hud) {
        if (!showCrosshair()) return false;
        JsonObject mod = HudConfig.module(HudConfig.snapshot(), "crosshair");
        if (!HudConfig.enabled(mod)) return true;
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        HudRenderer.drawCustomCrosshair(new DrawContext(new com.batclient.platform.mc189.MatrixStack()));
        return false;
    }
}
