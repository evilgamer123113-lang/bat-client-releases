package com.batclient;

import com.batclient.platform.WorldFrame;
import com.batclient.platform.BlockTarget;
import com.batclient.platform.ClientHookServices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public final class AdventureWorld {
    private AdventureWorld() { }
    public static void init(){ClientHookServices.get().afterEntities(AdventureWorld::render);}
    private static void render(WorldFrame ctx){
        var c=MinecraftClient.getInstance();if(c.player==null||c.world==null||!MinecraftClient.isHudEnabled()||c.currentScreen!=null||ctx.matrixStack()==null||ctx.consumers()==null)return;
        var death=VisualModules.settings("deathcoords");var last=DeathCoordinates.last(c,false);
        if(!(VisualModules.enabled("waypoints")&&HudConfig.bool(VisualModules.settings("waypoints"),"death",true))&&HudConfig.enabled(death)&&HudConfig.bool(death,"waypoint",true)&&last!=null&&last.dimension().equals(c.world.getRegistryKey().getValue().toString())){
            Vec3d target=new Vec3d(last.x()+.5,last.y()+1.5,last.z()+.5);double distance=c.player.getPos().distanceTo(target);
            // Direction marker is bounded to 64 blocks from camera to avoid far-plane clipping.
            Vec3d camera=ctx.camera().getPos(),delta=target.subtract(camera);if(delta.length()>64)target=camera.add(delta.normalize().multiply(64));
            label(ctx,target,"Death · "+Math.round(distance)+"m",0xFFFF6666,1);
        }
    }
    private static void label(WorldFrame ctx,Vec3d world,String text,int color,float scale){
        var c=MinecraftClient.getInstance();var matrices=ctx.matrixStack();var camera=ctx.camera().getPos();matrices.push();
        try{matrices.translate(world.x-camera.x,world.y-camera.y,world.z-camera.z);matrices.multiply(c.getEntityRenderDispatcher().getRotation());matrices.scale(.025f*scale,-.025f*scale,.025f*scale);
            // Separate background pass prevents a shadow plate covering the glyph foreground.
            float x=-c.textRenderer.getWidth(text)/2f;
            c.textRenderer.draw(Text.literal(text),x,0,0x40FFFFFF,false,matrices.peek().getPositionMatrix(),ctx.consumers(),TextRenderer.TextLayerType.NORMAL,0x55000000,LightmapTextureManager.MAX_LIGHT_COORDINATE);
            matrices.translate(0,0,.05);c.textRenderer.draw(Text.literal(text),x,0,color,true,matrices.peek().getPositionMatrix(),ctx.consumers(),TextRenderer.TextLayerType.NORMAL,0,LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }finally{matrices.pop();}
    }
}
