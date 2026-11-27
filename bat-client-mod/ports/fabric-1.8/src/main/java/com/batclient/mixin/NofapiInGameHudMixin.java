package com.batclient.mixin;

import com.batclient.platform.fabric.FabricClientHooks;
import com.batclient.platform.mc189.MatrixStack;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class NofapiInGameHudMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void bat$onHudRender(float tickDelta, CallbackInfo ci) {
        FabricClientHooks.onHud(new MatrixStack());
    }
}
