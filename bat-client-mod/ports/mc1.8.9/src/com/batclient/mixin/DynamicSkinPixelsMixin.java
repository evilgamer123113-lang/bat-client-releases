package com.batclient.mixin;

import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NativeImageBackedTexture.class)
public class DynamicSkinPixelsMixin {
    @Inject(method = "upload()V", at = @At("HEAD"))
    private void bat$noopUpload(CallbackInfo ci) {
    }
}
