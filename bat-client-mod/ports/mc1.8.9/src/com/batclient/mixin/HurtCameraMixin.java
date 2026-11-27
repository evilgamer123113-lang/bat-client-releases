package com.batclient.mixin;

import com.batclient.VisualModules;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameRenderer.class)
public abstract class HurtCameraMixin {
    // Only the damage roll, preserving the death animation and yaw basis.
    @ModifyArg(
        method = "bobViewWhenHurt(F)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;rotate(FFFF)V", ordinal = 2),
        index = 0
    )
    private float bat$hurt(float angle) {
        return VisualModules.hurtAngle(angle);
    }
}
