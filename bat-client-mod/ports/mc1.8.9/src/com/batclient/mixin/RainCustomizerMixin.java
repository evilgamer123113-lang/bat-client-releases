package com.batclient.mixin;

import com.batclient.HudConfig;
import com.batclient.VisualModules;
import com.google.gson.JsonObject;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(GameRenderer.class)
public abstract class RainCustomizerMixin {
    @Unique private boolean bat$snow;
    @Unique private float bat$rainAlpha = 1, bat$snowAlpha = 1;

    @Inject(method = "renderWeather(F)V", at = @At("HEAD"))
    private void bat$settings(float delta, CallbackInfo ci) {
        JsonObject mod = VisualModules.settings("raincustomizer");
        bat$rainAlpha = HudConfig.enabled(mod) ? VisualModules.number(mod, "opacity", 40, 0, 100) / 100f : 1f;
        bat$snowAlpha = HudConfig.bool(mod, "snow", false) ? bat$rainAlpha : 1f;
    }

    @ModifyArg(method = "renderWeather(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/TextureManager;bindTexture(Lnet/minecraft/util/Identifier;)V"), index = 0)
    private Identifier bat$texture(Identifier id) {
        bat$snow = id.getPath().contains("snow");
        return id;
    }

    @ModifyArgs(method = "renderWeather(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/BufferBuilder;"))
    private void bat$opacity(Args args) {
        float a = (Float) args.get(3);
        args.set(3, a * (bat$snow ? bat$snowAlpha : bat$rainAlpha));
    }
}
