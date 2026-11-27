package com.batclient.mixin;

import com.batclient.VisualModules;
import com.batclient.platform.InventoryNametagState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class OwnNametagMixin<T extends LivingEntity> {
    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void bat$ownNametag(T entity, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && entity == mc.player && !entity.isInvisible()
                && VisualModules.enabled("ownnametag")
                && !InventoryNametagState.inInventory) {
            cir.setReturnValue(true);
        }
    }
}
