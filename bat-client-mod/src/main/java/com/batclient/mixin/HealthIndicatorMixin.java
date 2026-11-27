package com.batclient.mixin;

import com.batclient.HealthIndicator;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.MinecraftClient;
import com.batclient.HudConfig;

@Mixin(EntityRenderer.class)
public abstract class HealthIndicatorMixin {
    @Shadow protected abstract boolean hasLabel(Entity entity);
    @Inject(method="shouldRender",at=@At("RETURN"),cancellable=true)
    private void bat$healthBounds(Entity entity,Frustum frustum,double x,double y,double z,CallbackInfoReturnable<Boolean> ci){
        if(ci.getReturnValueZ()||!(entity instanceof LivingEntity living))return;
        var mod=HealthIndicator.settings();var client=MinecraftClient.getInstance();
        if(!HudConfig.enabled(mod)||client.player==null||!living.isAlive()||living.isInvisibleTo(client.player))return;
        float range=Math.max(4,Math.min(64,HudConfig.setNumber(mod,"range",24)));
        if(client.player.squaredDistanceTo(entity)>range*range)return;
        // Vanilla culls the body box; the billboard can still be inside the view.
        double extent=2.5*Math.max(.5,Math.min(2,HudConfig.setNumber(mod,"scale",1)));
        if(frustum.isVisible(entity.getBoundingBox().expand(extent)))ci.setReturnValue(true);
    }
    @Inject(method="render",at=@At("RETURN"))
    private void bat$health(Entity entity,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci) {
        if(entity instanceof LivingEntity living)HealthIndicator.render(living,hasLabel(entity),delta,matrices,vertices,light);
    }
}
