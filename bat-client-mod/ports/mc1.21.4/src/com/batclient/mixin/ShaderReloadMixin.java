package com.batclient.mixin;
import net.minecraft.client.gl.ShaderLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ShaderLoader.class)
public abstract class ShaderReloadMixin {
 @Inject(method="apply(Lnet/minecraft/client/gl/ShaderLoader$Definitions;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",at=@At("RETURN"))
 private void bat$shaders(CallbackInfo ci){com.batclient.platform.mc1214.ShaderRegistry.loaded((ShaderLoader)(Object)this);}
}
