package com.batclient.platform.forge.mixin;

import com.batclient.platform.forge.ForgeClientHooks;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public final class ForgeMinecraftLifecycleMixin {
    @Inject(method="stop",at=@At("HEAD"))
    private void bat$stopping(CallbackInfo info){ForgeClientHooks.fireStopping((MinecraftClient)(Object)this);}
}
