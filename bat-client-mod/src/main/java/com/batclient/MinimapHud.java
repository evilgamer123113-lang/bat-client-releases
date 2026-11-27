package com.batclient;

import com.batclient.hud.TerrainMapRaster;
import com.batclient.hud.FrameWorkBudget;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.block.GrassBlock;
import net.minecraft.block.LeavesBlock;
import com.mojang.blaze3d.systems.RenderSystem;

/** Surface terrain from loaded client chunks only; no entities, cave scan or network requests. */
public final class MinimapHud {
    private static final int RES=TerrainMapRaster.SIZE;
    private static final TerrainMapRaster RASTER=new TerrainMapRaster();
    private static final FrameWorkBudget SAMPLE_BUDGET=new FrameWorkBudget();
    private static final Identifier LIVE=Identifier.of("batclient","dynamic/minimap_live"),PREVIEW=Identifier.of("batclient","dynamic/minimap_preview");
    private static NativeImageBackedTexture liveTexture,previewTexture;
    private static Object world;
    private static long next;
    private static final BlockPos.Mutable POS=new BlockPos.Mutable();
    private MinimapHud() { }
    public static void tick(MinecraftClient client){
        var mod=VisualModules.settings("minimap");
        if(world!=client.world||client.world==null||client.player==null||!HudConfig.enabled(mod)){
            RASTER.clear();world=client.world;next=0;
            if(client.world==null||client.player==null||!HudConfig.enabled(mod))return;
        }
        int radius=(int)VisualModules.number(mod,"range",48,16,96),x=client.player.getBlockX(),z=client.player.getBlockZ();long now=Util.getMeasuringTimeMs();
        var frame=RASTER.frame();
        if(!RASTER.building()&&(frame==null||frame.radius()!=radius||Math.abs(frame.x()-x)>2||Math.abs(frame.z()-z)>2||now>=next))RASTER.begin(x,z,radius);
        int budget=switch(HudConfig.str(mod,"updates","Balanced")){case "Low"->512;case "Fast"->2048;default->1024;};
        SAMPLE_BUDGET.begin(System.nanoTime(),.75f);
        if(RASTER.step(budget,(wx,wz)->sample(client,mod,wx,wz),SAMPLE_BUDGET)){
            if(liveTexture==null)liveTexture=create(client,LIVE);
            int[] pixels=RASTER.frame().pixels();for(int i=0;i<pixels.length;i++)liveTexture.getImage().setColor(i%RES,i/RES,TerrainMapRaster.abgr(pixels[i]));
            liveTexture.upload();next=now+250;
        }
    }
    private static int elevation(MinecraftClient c,int x,int z,int fallback){var chunk=c.world.getChunkManager().getChunk(x>>4,z>>4,ChunkStatus.FULL,false);return chunk==null||chunk.isEmpty()?fallback:chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE,x&15,z&15);}
    private static int sample(MinecraftClient client,JsonObject mod,int x,int z){
        var chunk=client.world.getChunkManager().getChunk(x>>4,z>>4,ChunkStatus.FULL,false);
        if(chunk==null||chunk.isEmpty())return 0xFF20252B;
        int y=chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE,x&15,z&15);
        if(y<client.world.getBottomY())return 0xFF20252B;
        POS.set(x,y,z);var state=chunk.getBlockState(POS);
        int rgb=state.getMapColor(client.world,POS).color;
        boolean water=state.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.WATER);
        if(HudConfig.bool(mod,"biomeColors",true)){
            if(water)rgb=BiomeColors.getWaterColor(client.world,POS);
            else if(state.getBlock() instanceof GrassBlock||state.getBlock() instanceof LeavesBlock){int tint=client.getBlockColors().getColor(state,client.world,POS,0);if(tint>=0)rgb=tint;}
        }
        float shade=1;
        if(HudConfig.bool(mod,"relief",true)){
            int north=elevation(client,x,z-1,y),west=elevation(client,x-1,z,y);
            shade=(float)Math.clamp(.96+(north-y)*.095+(west-y)*.065,.62,1.28);
            if(HudConfig.bool(mod,"contours",false)&&Math.floorDiv(y,8)!=Math.floorDiv(north,8))shade*=.72f;
        }
        if(water){int depth=0;for(int down=1;down<=8&&y-down>=client.world.getBottomY();down++){POS.set(x,y-down,z);if(chunk.getBlockState(POS).getFluidState().isEmpty())break;depth++;}shade*=1-depth*.035f;}
        int r=Math.min(255,(int)(((rgb>>16)&255)*shade)),g=Math.min(255,(int)(((rgb>>8)&255)*shade)),b=Math.min(255,(int)((rgb&255)*shade));
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }
    private static NativeImageBackedTexture create(MinecraftClient client,Identifier id){var texture=new NativeImageBackedTexture(RES,RES,false);texture.setFilter(false,false);client.getTextureManager().registerTexture(id,texture);return texture;}
    private static void preparePreview(MinecraftClient client){
        if(previewTexture!=null)return;previewTexture=create(client,PREVIEW);
        var raster=new TerrainMapRaster();raster.begin(0,0,32);
        while(!raster.step(2048,MinimapHud::previewColor)) { }
        int[] pixels=raster.frame().pixels();for(int i=0;i<pixels.length;i++)previewTexture.getImage().setColor(i%RES,i/RES,TerrainMapRaster.abgr(pixels[i]));previewTexture.upload();
    }
    private static int previewColor(int x,int z){
        double river=Math.abs(x-8*Math.sin(z*.065)-3*Math.sin(z*.17));
        if(river<4)return river<2?0xFF285C91:0xFF447FA9;if(river<5)return 0xFFCEC394;
        int cx=Math.floorDiv(x,7),cz=Math.floorDiv(z,7),seed=(cx*73428767)^(cz*912931);seed^=seed>>>13;
        double dx=x-(cx*7+2+Math.floorMod(seed,3)),dz=z-(cz*7+2+Math.floorMod(seed>>5,3));
        boolean tree=Math.floorMod(seed,5)<2&&dx*dx+dz*dz<6;
        int rgb=tree?0x315C37:0x7BA651;float shade=(float)(.90+.1*Math.sin(x*.17)*Math.cos(z*.12)+.05*Math.sin((x+z)*.37));
        return 0xFF000000|((int)(((rgb>>16)&255)*shade)<<16)|((int)(((rgb>>8)&255)*shade)<<8)|(int)((rgb&255)*shade);
    }
    public static int size(JsonObject mod){return (int)VisualModules.number(mod,"size",128,80,224);}
    public static int height(JsonObject mod){return size(mod)+(HudConfig.bool(mod,"coords",true)?14:0)+(HudConfig.bool(mod,"biome",false)?12:0);}
    public static void draw(DrawContext ctx,MinecraftClient client,JsonObject mod,boolean preview){
        int size=size(mod);var frame=RASTER.frame();
        if(preview)preparePreview(client);
        boolean ready=preview||liveTexture!=null&&frame!=null;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        if(ready)ctx.drawTexture(preview?PREVIEW:LIVE,0,0,size,size,0,0,RES,RES,RES,RES);
        else {ctx.fill(0,0,size,size,0xFF20252B);ctx.drawText(client.textRenderer,"Mapping…",6,size/2-4,0xFFAAAAAA,true);}
        if(HudConfig.bool(mod,"grid",false))for(int n=1;n<4;n++){int p=n*size/4;ctx.fill(p,0,p+1,size,0x22FFFFFF);ctx.fill(0,p,size,p+1,0x22FFFFFF);}
        if(HudConfig.bool(mod,"directions",true)){
            ctx.drawCenteredTextWithShadow(client.textRenderer,"N",size/2,3,0xFFFFFFFF);ctx.drawCenteredTextWithShadow(client.textRenderer,"S",size/2,size-11,0xFFFFFFFF);
            ctx.drawText(client.textRenderer,"W",3,size/2-4,0xFFFFFFFF,true);ctx.drawText(client.textRenderer,"E",size-9,size/2-4,0xFFFFFFFF,true);
        }
        if(!preview&&frame!=null)WaypointRenderer.minimap(ctx,client,frame.x(),frame.z(),frame.radius(),size);
        float px=size/2f,pz=size/2f;
        if(!preview&&frame!=null&&client.player!=null){px+=(float)(client.player.getX()-frame.x())*size/(frame.radius()*2);pz+=(float)(client.player.getZ()-frame.z())*size/(frame.radius()*2);}
        px=Math.clamp(px,10,size-10);pz=Math.clamp(pz,10,size-10);
        ctx.getMatrices().push();try{
            ctx.getMatrices().translate(px,pz,10);ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees((client.player==null?0:client.player.getYaw())+180));
            RenderSystem.setShaderColor(0,0,0,.85f);ctx.drawTexture(NativeNametags.LOGO,-9,-3,20,10,51f,159f,410,194,512,512);
            RenderSystem.setShaderColor(1,1,1,1);ctx.drawTexture(NativeNametags.LOGO,-10,-4,20,10,51f,159f,410,194,512,512);
        }finally{RenderSystem.setShaderColor(1,1,1,1);ctx.getMatrices().pop();}
        if(HudConfig.bool(mod,"coords",true)){
            String coords=client.player==null?"X: 0 Y: 64 Z: 0":"X: "+client.player.getBlockX()+" Y: "+client.player.getBlockY()+" Z: "+client.player.getBlockZ();
            ctx.drawText(client.textRenderer,client.textRenderer.trimToWidth(coords,size),0,size+4,HudRenderer.parseColor(HudConfig.str(mod,"color","#FFFFFFFF"),0xFFFFFFFF),HudConfig.bool(mod,"shadow",true));
        }
        if(HudConfig.bool(mod,"biome",false)){
            String biome=preview||client.player==null?"Forest":client.world.getBiome(client.player.getBlockPos()).getKey().map(k->k.getValue().getPath().replace('_',' ')).orElse("Unknown");
            ctx.drawText(client.textRenderer,client.textRenderer.trimToWidth(biome,size),0,size+4+(HudConfig.bool(mod,"coords",true)?12:0),0xFFBBBBBB,HudConfig.bool(mod,"shadow",true));
        }
    }
    public static void close(MinecraftClient client){if(liveTexture!=null){client.getTextureManager().destroyTexture(LIVE);liveTexture=null;}if(previewTexture!=null){client.getTextureManager().destroyTexture(PREVIEW);previewTexture=null;}RASTER.clear();world=null;}
}
