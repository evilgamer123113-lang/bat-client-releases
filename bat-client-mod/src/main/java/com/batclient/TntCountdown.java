package com.batclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.TntEntity;
import net.minecraft.text.Text;
import java.util.Locale;

/** Entity-relative hook follows the native TNT render pass, rather than a second world scan. */
public final class TntCountdown {
    private TntCountdown() { }
    public static String text(float fuse,boolean ticks){return ticks?Math.max(0,(int)Math.ceil(fuse))+" ticks":String.format(Locale.ROOT,"%.1fs",Math.max(0,fuse)/20f);}
    public static void render(TntEntity entity,float delta,MatrixStack matrices,VertexConsumerProvider vertices){
        var c=MinecraftClient.getInstance();var mod=VisualModules.settings("tntcountdown");
        if(!HudConfig.enabled(mod)||c.player==null||!MinecraftClient.isHudEnabled()||c.currentScreen!=null)return;
        float range=VisualModules.number(mod,"range",32,4,64);if(c.player.squaredDistanceTo(entity)>range*range)return;
        float fuse=Math.max(0,entity.getFuse()-delta);
        int color=HudRenderer.parseColor(HudConfig.str(mod,fuse<=20?"urgentColor":"color",fuse<=20?"#FF5555FF":"#FFFFFFFF"),0xFFFFFFFF);
        matrices.push();try{
            matrices.translate(0,entity.getHeight()+.45,0);matrices.multiply(c.getEntityRenderDispatcher().getRotation());
            float scale=VisualModules.number(mod,"scale",1,.5f,2);
            // Same handedness as EntityRenderer.renderLabelIfPresent. Negative X culls the glyph face.
            matrices.scale(.025f*scale,-.025f*scale,.025f*scale);
            draw(matrices,vertices,text(fuse,HudConfig.bool(mod,"ticks",false)),color,HudConfig.bool(mod,"shadow",true),HudConfig.bool(mod,"background",true),HudConfig.bool(mod,"throughWalls",false));
        }finally{matrices.pop();}
    }
    public static void draw(MatrixStack matrices,VertexConsumerProvider vertices,String value,int color,boolean shadow,boolean background,boolean through){
        var renderer=MinecraftClient.getInstance().textRenderer;float x=-renderer.getWidth(value)/2f;var text=Text.literal(value);
        renderer.draw(text,x,0,0x40FFFFFF,false,matrices.peek().getPositionMatrix(),vertices,through?TextRenderer.TextLayerType.SEE_THROUGH:TextRenderer.TextLayerType.NORMAL,background?0x70000000:0,LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.push();try{matrices.translate(0,0,.05);renderer.draw(text,x,0,color,shadow,matrices.peek().getPositionMatrix(),vertices,TextRenderer.TextLayerType.NORMAL,0,LightmapTextureManager.MAX_LIGHT_COORDINATE);}finally{matrices.pop();}
    }
}
