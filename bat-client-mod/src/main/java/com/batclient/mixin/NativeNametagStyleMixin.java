package com.batclient.mixin;

import com.batclient.HudConfig;
import com.batclient.NativeNametags;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(EntityRenderer.class)
public abstract class NativeNametagStyleMixin {
    @ModifyArgs(method = "renderLabelIfPresent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V"))
    private void bat$scale(Args args, Entity player, Text text, MatrixStack matrices, VertexConsumerProvider vertices, int light, float delta) {
        if (!(player instanceof PlayerEntity) || !NativeNametags.enabled()) return;
        float size = Math.max(.5f, Math.min(2, HudConfig.setNumber(NativeNametags.settings(), "scale", 1)));
        for (int i = 0; i < 3; i++) args.set(i, (float)args.get(i) * size);
    }

    @ModifyArgs(method = "renderLabelIfPresent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I"))
    private void bat$textStyle(Args args, Entity player, Text text, MatrixStack matrices, VertexConsumerProvider vertices, int light, float delta) {
        if (!(player instanceof PlayerEntity) || !NativeNametags.enabled()) return;
        args.set(4, HudConfig.bool(NativeNametags.settings(), "shadow", true));
        if (!HudConfig.bool(NativeNametags.settings(), "background", true)) args.set(8, 0);
        NativeNametags.drawLogo((Text) args.get(0), (float) args.get(1), (float) args.get(2), (int) args.get(3),
                args.get(5), args.get(6), args.get(7), (int) args.get(9));
    }
}
