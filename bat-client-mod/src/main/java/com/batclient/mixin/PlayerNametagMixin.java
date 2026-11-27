package com.batclient.mixin;

import com.batclient.ClientPresence;
import com.batclient.HudConfig;
import com.batclient.NativeNametags;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerNametagMixin {
    @ModifyVariable(method = "renderLabelIfPresent(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IF)V", at = @At("HEAD"), argsOnly = true)
    private Text bat$badge(Text name, AbstractClientPlayerEntity player, Text original, MatrixStack matrices,
                           VertexConsumerProvider vertices, int light, float delta) {
        return NativeNametags.enabled() && HudConfig.bool(NativeNametags.settings(), "logo", true)
                && ClientPresence.isBatPlayer(player.getUuid()) ? NativeNametags.withLogo(name) : name;
    }
}
