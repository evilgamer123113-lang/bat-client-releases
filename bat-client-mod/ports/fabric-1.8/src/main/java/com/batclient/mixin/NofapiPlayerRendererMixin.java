package com.batclient.mixin;

import com.batclient.platform.fabric.FabricClientResources;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class NofapiPlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayerEntity> {
    protected NofapiPlayerRendererMixin(EntityRenderDispatcher dispatcher, EntityModel model, float shadowSize) {
        super(dispatcher, model, shadowSize);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRenderDispatcher;Z)V", at = @At("RETURN"))
    private void bat$onPlayerRendererInit(EntityRenderDispatcher dispatcher, boolean slim, CallbackInfo ci) {
        FabricClientResources.onPlayerLayers((PlayerEntityRenderer) (Object) this,
            (FeatureRenderer<AbstractClientPlayerEntity> feature) -> this.addFeature(feature));
    }
}
