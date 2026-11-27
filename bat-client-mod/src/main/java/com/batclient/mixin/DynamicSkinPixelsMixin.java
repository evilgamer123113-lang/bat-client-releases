package com.batclient.mixin;

import com.batclient.SkinLayers3D;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NativeImageBackedTexture.class)
public abstract class DynamicSkinPixelsMixin {
    @Shadow public abstract NativeImage getImage();
    @Inject(method="upload",at=@At("HEAD"))
    private void bat$dynamicPixels(CallbackInfo callback) {
        SkinLayers3D.refreshDynamicPixels(this,getImage());
    }
}
