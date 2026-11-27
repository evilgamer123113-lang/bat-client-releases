package com.batclient;

import java.io.IOException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import com.batclient.hud.LoadingLayout;
import net.minecraft.client.render.RenderLayer;

/** Bootstrap-safe branding: classpath image, independent of web engine and font reloads. */
public final class LoadingBrand {
    public static final Identifier TEXTURE=Identifier.of("batclient","dynamic/loading_brand");
    private static int textureWidth,textureHeight;
    private LoadingBrand() { }
    public static void init(MinecraftClient client){
        if(textureWidth!=0)return;
        try(var stream=LoadingBrand.class.getResourceAsStream("/assets/batclient/textures/gui/loading-brand.png")){
            if(stream==null)throw new IOException("Missing packaged loading brand");
            var image=NativeImage.read(stream);textureWidth=image.getWidth();textureHeight=image.getHeight();
            var texture=new NativeImageBackedTexture(image);texture.setFilter(true,false);
            client.getTextureManager().registerTexture(TEXTURE,texture);
            BatClientMod.LOGGER.info("[boot] Native monochrome Bat Client loading brand ready");
        }catch(IOException error){throw new IllegalStateException("Unable to load Bat Client loading brand",error);}
    }
    public static void draw(DrawContext context,int left,int top,int halfWidth,int originalHeight){
        if(textureWidth==0)init(MinecraftClient.getInstance());
        var layout=layout(context);
        context.drawTexture(TEXTURE,layout.left(),layout.top(),layout.width(),layout.height(),0f,0f,textureWidth,textureHeight,textureWidth,textureHeight);
    }
    private static LoadingLayout layout(DrawContext context){return LoadingLayout.of(context.getScaledWindowWidth(),context.getScaledWindowHeight(),MinecraftClient.getInstance().getWindow().getScaleFactor(),textureWidth,textureHeight);}
    public static void progress(DrawContext context,float progress,float opacity){
        var layout=layout(context);int alpha=Math.round(255*Math.clamp(opacity,0,1));
        context.fill(RenderLayer.getGuiOverlay(),layout.barLeft(),layout.barTop(),layout.barLeft()+layout.barWidth(),layout.barTop()+layout.barHeight(),(alpha<<24)|0x303030);
        int filled=(int)(layout.barWidth()*Math.clamp(progress,0,1));
        if(filled>0)context.fill(RenderLayer.getGuiOverlay(),layout.barLeft(),layout.barTop(),layout.barLeft()+filled,layout.barTop()+layout.barHeight(),(alpha<<24)|0xFFFFFF);
    }
}
