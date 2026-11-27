package com.batclient.platform.forge.mixin;

import com.batclient.platform.forge.ForgeClientHooks;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public final class ForgeWorldRenderMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void bat$start(CallbackInfo info){ForgeClientHooks.fireWorldStart();}
    @Inject(method="render",at=@At("RETURN"))
    private void bat$end(CallbackInfo info){ForgeClientHooks.fireWorldEnd();}
}
