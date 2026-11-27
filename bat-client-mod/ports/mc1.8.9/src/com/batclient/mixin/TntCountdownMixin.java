package com.batclient.mixin;

import com.batclient.TntCountdown;
import com.batclient.platform.mc189.MatrixStack;
import net.minecraft.client.render.entity.TntEntityRenderer;
import net.minecraft.entity.TntEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TntEntityRenderer.class)
public abstract class TntCountdownMixin {
    @Inject(method = "render(Lnet/minecraft/entity/TntEntity;DDDFF)V", at = @At("RETURN"))
    private void bat$countdown(TntEntity entity, double x, double y, double z, float yaw, float delta, CallbackInfo ci) {
        MatrixStack matrices = new MatrixStack();
        matrices.push();
        matrices.translate((float) x, (float) y, (float) z);
        TntCountdown.render(entity, delta, matrices);
        matrices.pop();
    }
}
