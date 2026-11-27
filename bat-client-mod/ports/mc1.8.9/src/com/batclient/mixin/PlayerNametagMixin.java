package com.batclient.mixin;

import com.batclient.ClientPresence;
import com.batclient.HudConfig;
import com.batclient.NativeNametags;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.text.LiteralText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderer.class)
public abstract class PlayerNametagMixin {
    @ModifyVariable(method = "renderLabelIfPresent", at = @At("HEAD"), argsOnly = true)
    private String bat$badge(String name, Entity entity, String original, double x, double y, double z, int maxDistance) {
        if (entity instanceof AbstractClientPlayerEntity player
                && NativeNametags.enabled()
                && HudConfig.bool(NativeNametags.settings(), "logo", true)
                && ClientPresence.isBatPlayer(player.getUuid())) {
            return NativeNametags.withLogo(new LiteralText(name)).asFormattedString();
        }
        return name;
    }
}
