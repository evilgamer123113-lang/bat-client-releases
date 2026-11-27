package com.batclient.mixin;
import com.batclient.HitColor;
import com.batclient.VisualModules;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.lwjgl.opengl.GL13;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OverlayTexture.class)
public abstract class HitColorMixin {
    @Shadow @Final private NativeImageBackedTexture texture;
    @Unique private NativeImageBackedTexture bat$hitTexture;
    @Unique private int bat$hitPixel;
    @Inject(method="setupOverlayColor",at=@At("HEAD"),cancellable=true)
    private void bat$hitColor(CallbackInfo ci){
        if(!VisualModules.enabled("hitcolor"))return;
        int pixel=HitColor.overlayPixel();
        boolean fresh=bat$hitTexture==null;
        if(fresh)bat$hitTexture=new NativeImageBackedTexture(16,16,false);
        if(fresh||pixel!=bat$hitPixel){
            var image=bat$hitTexture.getImage();var original=texture.getImage();
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setColor(x,y,y<8?pixel:original.getColor(x,y));
            RenderSystem.activeTexture(GL13.GL_TEXTURE1);
            bat$hitTexture.bindTexture();image.upload(0,0,0,0,0,16,16,false,true,false,false);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);bat$hitPixel=pixel;
        }
        RenderSystem.setupOverlayColor(bat$hitTexture.getGlId(),16);ci.cancel();
    }
    @Inject(method="close",at=@At("HEAD"))
    private void bat$close(CallbackInfo ci){if(bat$hitTexture!=null){bat$hitTexture.close();bat$hitTexture=null;}}
}
