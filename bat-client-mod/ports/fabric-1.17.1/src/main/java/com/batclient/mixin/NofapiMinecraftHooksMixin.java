package com.batclient.mixin;

import com.batclient.platform.fabric.FabricClientHooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class NofapiMinecraftHooksMixin {
    @Inject(method = "run", at = @At("HEAD"))
    private void bat$nofapiStarted(CallbackInfo ci) {
        FabricClientHooks.onStarted((MinecraftClient) (Object) this);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void bat$nofapiStopping(CallbackInfo ci) {
        FabricClientHooks.onStopping((MinecraftClient) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void bat$nofapiEndTick(CallbackInfo ci) {
        FabricClientHooks.onEndTick((MinecraftClient) (Object) this);
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("TAIL"))
    private void bat$nofapiDisconnect(Screen screen, CallbackInfo ci) {
        FabricClientHooks.onDisconnect();
    }
}
