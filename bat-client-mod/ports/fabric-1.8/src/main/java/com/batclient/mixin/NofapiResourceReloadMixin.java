package com.batclient.mixin;

import com.batclient.platform.fabric.FabricClientResources;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class NofapiResourceReloadMixin {
    @Inject(method = "reloadResources()V", at = @At("HEAD"))
    private void bat$nofapiReload(CallbackInfo ci) {
        FabricClientResources.onReload();
    }
}
