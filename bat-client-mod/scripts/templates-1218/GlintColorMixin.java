package com.batclient.mixin;

import com.batclient.VisualModules;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.render.RenderLayer;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Apply the configured glint tint through the draw's dynamic uniforms. */
@Mixin(RenderLayer.MultiPhase.class)
public abstract class GlintColorMixin {
    @Shadow @Final private RenderPipeline pipeline;

    @ModifyArg(method = "draw", index = 1, at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/gl/DynamicUniforms;write(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;F)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private Vector4fc bat$glintTint(Vector4fc color) {
        var id = pipeline.getLocation();
        if (id.getNamespace().equals("minecraft") && id.getPath().contains("glint")) {
            return VisualModules.glintTint(color);
        }
        return color;
    }
}
