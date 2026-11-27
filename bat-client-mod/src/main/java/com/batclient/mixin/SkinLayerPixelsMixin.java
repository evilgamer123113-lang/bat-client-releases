package com.batclient.mixin;

import com.batclient.SkinLayers3D;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read alpha before vanilla uploads/releases the downloaded skin image. */
@Mixin(PlayerSkinTexture.class)
public class SkinLayerPixelsMixin {
    @Inject(method="uploadTexture",at=@At("HEAD"))
    private void bat$skinPixels(NativeImage image,CallbackInfo callback) {
        SkinLayers3D.capturePixels(this,image);
    }
}
