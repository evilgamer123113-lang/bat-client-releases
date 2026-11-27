package com.batclient.mixin;

import com.batclient.ClientMods;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererZoomMixin {
    @Inject(method = "getFov(FZ)F", at = @At("RETURN"), cancellable = true)
    private void bat$zoom(float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (changingFov) cir.setReturnValue((float) ClientMods.zoomedFov(cir.getReturnValue()));
    }
}
