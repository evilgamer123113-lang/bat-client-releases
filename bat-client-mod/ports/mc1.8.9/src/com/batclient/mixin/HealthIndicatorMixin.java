package com.batclient.mixin;

import com.batclient.HealthIndicator;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class HealthIndicatorMixin {
    @Shadow protected abstract boolean hasLabel(Entity entity);

    @Inject(method = "render", at = @At("RETURN"))
    private void bat$health(Entity entity, double x, double y, double z, float yaw, float delta, CallbackInfo ci) {
        if (entity instanceof LivingEntity living) {
            HealthIndicator.render(living, hasLabel(entity), x, y, z);
        }
    }
}
