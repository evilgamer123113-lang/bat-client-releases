package com.batclient.mixin;

import com.batclient.VisualModules;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemPhysicsMixin {
    @Inject(method = "method_10221(Lnet/minecraft/entity/ItemEntity;DDDFLnet/minecraft/client/render/model/BakedModel;)I", at = @At("RETURN"))
    private void bat$itemPhysics(ItemEntity entity, double x, double y, double z, float tickDelta, BakedModel model, CallbackInfoReturnable<Integer> cir) {
        if (VisualModules.enabled("itemphysics") && entity.onGround) {
            GlStateManager.rotate(90f, 1f, 0f, 0f);
        }
    }
}
