package com.batclient.mixin;

import com.batclient.platform.fabric.FabricClientHooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class NofapiNetworkPayloadMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void bat$nofapiPayload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        if (FabricClientHooks.onPayload(packet.getChannel(), ((NofapiCustomPayloadAccessor) packet).bat$getData(), client)) {
            ci.cancel();
        }
    }
}
