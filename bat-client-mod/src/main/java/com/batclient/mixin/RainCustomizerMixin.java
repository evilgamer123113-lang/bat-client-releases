package com.batclient.mixin;
import com.batclient.*;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(WorldRenderer.class)
public abstract class RainCustomizerMixin {
    @Unique private boolean bat$snow;
    @Unique private float bat$rainAlpha=1,bat$snowAlpha=1;
    @Inject(method="renderWeather",at=@At("HEAD"))
    private void bat$settings(net.minecraft.client.render.LightmapTextureManager light,float delta,double x,double y,double z,CallbackInfo ci){
        var mod=VisualModules.settings("raincustomizer");bat$rainAlpha=HudConfig.enabled(mod)?VisualModules.number(mod,"opacity",40,0,100)/100:1;bat$snowAlpha=HudConfig.bool(mod,"snow",false)?bat$rainAlpha:1;
    }
    @ModifyArg(method="renderWeather",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/util/Identifier;)V"),index=1)
    private Identifier bat$texture(Identifier id){bat$snow=id.getPath().contains("snow");return id;}
    @ModifyArgs(method="renderWeather",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/VertexConsumer;color(FFFF)Lnet/minecraft/client/render/VertexConsumer;"))
    private void bat$opacity(Args args){args.set(3,(Float)args.get(3)*(bat$snow?bat$snowAlpha:bat$rainAlpha));}
}
