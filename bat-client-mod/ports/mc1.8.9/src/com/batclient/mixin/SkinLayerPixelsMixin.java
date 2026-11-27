package com.batclient.mixin;

import net.minecraft.client.texture.PlayerSkinTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.awt.image.BufferedImage;

@Mixin(PlayerSkinTexture.class)
public class SkinLayerPixelsMixin {
    @Inject(method = "method_6994(Ljava/awt/image/BufferedImage;)V", at = @At("HEAD"))
    private void bat$skinPixels(BufferedImage image, CallbackInfo ci) {
    }
}
