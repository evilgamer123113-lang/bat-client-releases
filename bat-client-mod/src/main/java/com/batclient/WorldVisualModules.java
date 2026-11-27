package com.batclient;
import com.google.gson.JsonObject;
import com.batclient.platform.ClientHookServices;
import com.batclient.platform.WorldFrame;
import com.batclient.platform.BlockTarget;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Box;
import org.joml.Vector3f;

public final class WorldVisualModules {
    private WorldVisualModules() { }
    public static void init(){ClientHookServices.get().blockOutline(WorldVisualModules::block);ClientHookServices.get().afterEntities(WorldVisualModules::hitboxes);}
    private static boolean visible(WorldFrame context){var client=MinecraftClient.getInstance();return client.player!=null&&MinecraftClient.isHudEnabled()&&client.currentScreen==null&&context.matrixStack()!=null&&context.consumers()!=null;}
    private static int color(JsonObject mod,String key,String fallback){return HudRenderer.parseColor(HudConfig.str(mod,key,fallback),0xFFFFFFFF);}
    private static boolean block(WorldFrame context,com.batclient.platform.BlockTarget target){
        var mod=VisualModules.settings("blockoutline");if(!HudConfig.enabled(mod)||!visible(context))return true;
        var shape=target.blockState().getOutlineShape(context.world(),target.blockPos(),ShapeContext.of(target.entity()));
        var camera=context.camera().getPos();var pos=target.blockPos();var matrices=context.matrixStack();
        int color=color(mod,"color","#FFFFFFFF");
        int second=color(mod,"gradientColor","#55AAFFFF");
        var bounds=shape.isEmpty()?null:shape.getBoundingBox();
        boolean gradient=HudConfig.bool(mod,"gradient",false)&&bounds!=null;
        var buffer=context.consumers().getBuffer(VisualLayers.lines(VisualModules.number(mod,"width",2,1,4)));
        shape.forEachEdge((x1,y1,z1,x2,y2,z2)->line(matrices,buffer,x1+pos.getX()-camera.x,y1+pos.getY()-camera.y,z1+pos.getZ()-camera.z,x2+pos.getX()-camera.x,y2+pos.getY()-camera.y,z2+pos.getZ()-camera.z,
                gradient?blend(color,second,(float)((y1-bounds.minY)/Math.max(.001,bounds.maxY-bounds.minY))):color,
                gradient?blend(color,second,(float)((y2-bounds.minY)/Math.max(.001,bounds.maxY-bounds.minY))):color));
        if(HudConfig.bool(mod,"fill",false)){
            var fill=context.consumers().getBuffer(RenderLayer.getDebugQuads());
            int tint=color(mod,"fillColor","#FFFFFF33");
            shape.forEachBox((x1,y1,z1,x2,y2,z2)->fill(matrices,fill,new Box(x1+pos.getX()-camera.x,y1+pos.getY()-camera.y,z1+pos.getZ()-camera.z,x2+pos.getX()-camera.x,y2+pos.getY()-camera.y,z2+pos.getZ()-camera.z).expand(.002),tint));
        }
        return false;
    }
    private static void hitboxes(WorldFrame context){
        var mod=VisualModules.settings("hitboxes");if(!HudConfig.enabled(mod)||!visible(context))return;
        var client=MinecraftClient.getInstance();var camera=context.camera().getPos();float delta=context.tickDelta();
        float range=VisualModules.number(mod,"range",32,4,64);int drawn=0;
        var buffer=context.consumers().getBuffer(VisualLayers.lines(VisualModules.number(mod,"width",1.5f,1,4)));
        for(var entity:context.world().getEntities()){
            if(entity==client.player||!entity.isAlive()||entity.isSpectator()||entity.isInvisibleTo(client.player)||entity.squaredDistanceTo(camera)>range*range)continue;
            boolean player=entity instanceof PlayerEntity,mob=entity instanceof LivingEntity&&!player,projectile=entity instanceof ProjectileEntity;
            if(!(player&&HudConfig.bool(mod,"players",true)||mob&&HudConfig.bool(mod,"mobs",false)||projectile&&HudConfig.bool(mod,"projectiles",true)))continue;
            var position=entity.getLerpedPos(delta);var box=entity.getBoundingBox().offset(position.subtract(entity.getPos()));
            if(context.frustum()!=null&&!context.frustum().isVisible(box))continue;
            int tint=color(mod,player?"playerColor":mob?"mobColor":"projectileColor",player?"#FFFFFFFF":mob?"#55FF55FF":"#FFAA00FF");
            WorldRenderer.drawBox(context.matrixStack(),buffer,box.offset(camera.negate()),((tint>>16)&255)/255f,((tint>>8)&255)/255f,(tint&255)/255f,(tint>>>24)/255f);
            if(++drawn>=128)break;
        }
    }
    private static int blend(int first,int second,float amount){int out=0;float t=Math.clamp(amount,0,1);for(int shift=0;shift<32;shift+=8)out|=Math.round(((first>>>shift)&255)*(1-t)+((second>>>shift)&255)*t)<<shift;return out;}
    private static void line(MatrixStack matrices,VertexConsumer vertices,double x1,double y1,double z1,double x2,double y2,double z2,int first,int second){
        var normal=new Vector3f((float)(x2-x1),(float)(y2-y1),(float)(z2-z1));if(normal.lengthSquared()==0)return;normal.normalize();var entry=matrices.peek();
        vertices.vertex(entry,(float)x1,(float)y1,(float)z1).color(first).normal(entry,normal.x,normal.y,normal.z);
        vertices.vertex(entry,(float)x2,(float)y2,(float)z2).color(second).normal(entry,normal.x,normal.y,normal.z);
    }
    private static void fill(MatrixStack matrices,VertexConsumer vertices,Box b,int color){
        face(matrices,vertices,color,b.minX,b.minY,b.minZ,b.maxX,b.minY,b.minZ,b.maxX,b.maxY,b.minZ,b.minX,b.maxY,b.minZ);
        face(matrices,vertices,color,b.minX,b.minY,b.maxZ,b.minX,b.maxY,b.maxZ,b.maxX,b.maxY,b.maxZ,b.maxX,b.minY,b.maxZ);
        face(matrices,vertices,color,b.minX,b.minY,b.minZ,b.minX,b.maxY,b.minZ,b.minX,b.maxY,b.maxZ,b.minX,b.minY,b.maxZ);
        face(matrices,vertices,color,b.maxX,b.minY,b.minZ,b.maxX,b.minY,b.maxZ,b.maxX,b.maxY,b.maxZ,b.maxX,b.maxY,b.minZ);
        face(matrices,vertices,color,b.minX,b.minY,b.minZ,b.minX,b.minY,b.maxZ,b.maxX,b.minY,b.maxZ,b.maxX,b.minY,b.minZ);
        face(matrices,vertices,color,b.minX,b.maxY,b.minZ,b.maxX,b.maxY,b.minZ,b.maxX,b.maxY,b.maxZ,b.minX,b.maxY,b.maxZ);
    }
    private static void face(MatrixStack matrices,VertexConsumer vertices,int color,double... xyz){for(int i=0;i<xyz.length;i+=3)vertices.vertex(matrices.peek(),(float)xyz[i],(float)xyz[i+1],(float)xyz[i+2]).color(color);}
}
