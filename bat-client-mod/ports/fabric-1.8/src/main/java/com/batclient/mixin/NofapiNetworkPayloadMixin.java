package com.batclient.mixin;

import com.batclient.platform.fabric.FabricClientHooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.PacketByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class NofapiNetworkPayloadMixin {
    @Shadow private MinecraftClient client;

    @Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
    private void bat$onCustomPayload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        if (packet == null || packet.getChannel() == null || packet.getPayload() == null) return;
        Identifier id = new Identifier(packet.getChannel());
        PacketByteBuf buf = new PacketByteBuf(packet.getPayload().copy());
        if (FabricClientHooks.onPayload(id, buf, client)) {
            ci.cancel();
        }
    }
}
