package com.batclient.mixin;
import com.batclient.LoadingBrand;
import java.util.function.IntSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=SplashOverlay.class,priority=1500)
public abstract class LoadingBrandMixin {
    @Shadow private float progress;
    @Inject(method="init",at=@At("RETURN"))
    private static void bat$init(MinecraftClient client,CallbackInfo ci){LoadingBrand.init(client);}
    @Redirect(method="render",at=@At(value="INVOKE",target="Ljava/util/function/IntSupplier;getAsInt()I"),require=0)
    private int bat$black(IntSupplier original){return 0xFF000000;}
    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIFFIIII)V"),require=0)
    private void bat$brand(DrawContext context,Identifier vanilla,int x,int y,int width,int height,float u,float v,int regionWidth,int regionHeight,int textureWidth,int textureHeight){
        // Vanilla draws two halves. Replace the first with one complete wordmark.
        if(u<0)LoadingBrand.draw(context,x,y,width,height);
    }
    @Inject(method="renderProgressBar",at=@At("HEAD"),cancellable=true,require=0)
    private void bat$progress(DrawContext context,int minX,int minY,int maxX,int maxY,float opacity,CallbackInfo ci){LoadingBrand.progress(context,progress,opacity);ci.cancel();}
}
