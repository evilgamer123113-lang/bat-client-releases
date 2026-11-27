package com.batclient.mixin;

import com.batclient.HitColor;
import com.batclient.VisualModules;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.nio.FloatBuffer;

@Mixin(LivingEntityRenderer.class)
public abstract class HitColorMixin<T extends LivingEntity> {
    @Shadow
    protected FloatBuffer buffer;

    @Inject(
        method = "method_10252(Lnet/minecraft/entity/LivingEntity;FZ)Z",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexEnv(IILjava/nio/FloatBuffer;)V", ordinal = 0)
    )
    private void bat$hitColor(T entity, float tickDelta, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        if (VisualModules.enabled("hitcolor") && (entity.hurtTime > 0 || entity.deathTime > 0)) {
            int px = HitColor.overlayPixel();
            float r = (px & 0xFF) / 255f;
            float g = ((px >>> 8) & 0xFF) / 255f;
            float b = ((px >>> 16) & 0xFF) / 255f;
            float strength = 1f - ((px >>> 24) & 0xFF) / 255f;
            this.buffer.position(0);
            this.buffer.put(r);
            this.buffer.put(g);
            this.buffer.put(b);
            this.buffer.put(strength);
            this.buffer.position(0);
        }
    }
}
