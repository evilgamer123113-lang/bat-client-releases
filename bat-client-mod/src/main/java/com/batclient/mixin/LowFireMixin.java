package com.batclient.mixin;

import com.batclient.HudConfig;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InGameOverlayRenderer.class)
public class LowFireMixin {
    // Vanilla isolates each flame with push/pop; change only its vertical offset.
    @Redirect(method = "renderFireOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"))
    private static void bat$lowerFire(MatrixStack matrices, float x, float y, float z) {
        var mod = HudConfig.module(HudConfig.snapshot(), "lowfire");
        if (HudConfig.enabled(mod)) {
            float height = HudConfig.setNumber(mod, "height", .5f);
            if (!Float.isFinite(height)) height = .5f;
            y -= (1 - MathHelper.clamp(height, .1f, 1f)) * .9f;
        }
        matrices.translate(x, y, z);
    }
}
