package com.batclient.mixin;

import com.batclient.platform.fabric.FabricClientHooks;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class NofapiInGameHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void bat$nofapiHud(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        FabricClientHooks.onHud(matrices);
    }
}
