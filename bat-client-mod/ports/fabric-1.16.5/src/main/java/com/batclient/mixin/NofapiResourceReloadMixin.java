package com.batclient.mixin;

import com.batclient.platform.fabric.FabricClientResources;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

@Mixin(MinecraftClient.class)
public abstract class NofapiResourceReloadMixin {
    @Inject(method = "reloadResources()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
    private void bat$nofapiReload(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        FabricClientResources.onReload();
    }
}
