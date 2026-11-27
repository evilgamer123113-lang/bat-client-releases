package com.batclient.mixin;

import com.batclient.MotionBlur;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MotionBlurMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void bat$prepareBlur(RenderTickCounter counter, boolean tick, CallbackInfo ci) { MotionBlur.prepare(); }

    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void bat$blurWorld(RenderTickCounter counter, CallbackInfo ci) { MotionBlur.render(); }

    @Inject(method = "close", at = @At("HEAD"))
    private void bat$closeBlur(CallbackInfo ci) { MotionBlur.close(); }
}
