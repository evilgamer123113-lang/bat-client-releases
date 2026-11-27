package com.batclient;

import com.batclient.platform.WorldFrame;
import com.batclient.platform.ClientHookServices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Bounded display for already-loaded drops. No entity glow flags, packets or chunk queries. */
public final class LootBeams {
    private static List<ItemEntity> visible=List.of();
    private static Object world;
    private static int ticks;
    private LootBeams(){}
    public static void init(){ClientHookServices.get().afterEntities(LootBeams::render);}
    public static boolean valuable(ItemStack stack){var i=stack.getItem();return stack.hasEnchantments()||stack.getRarity()!=Rarity.COMMON||i==Items.DIAMOND||i==Items.EMERALD||i==Items.NETHERITE_INGOT||i==Items.ANCIENT_DEBRIS||i==Items.DIAMOND_BLOCK||i==Items.NETHERITE_BLOCK||i==Items.TOTEM_OF_UNDYING||i==Items.ELYTRA;}
    public static boolean accepts(ItemStack stack,String filter,int minimum){return !stack.isEmpty()&&stack.getCount()>=minimum&&(!(filter.equals("Rare")||filter.equals("Uncommon+"))||stack.getRarity()!=Rarity.COMMON)&&(!filter.equals("Valuables")||valuable(stack));}
    public static void tick(MinecraftClient c){
        var mod=VisualModules.settings("lootbeams");if(c.world==null||c.player==null||!HudConfig.enabled(mod)){visible=List.of();world=c.world;return;}
        if(world!=c.world){world=c.world;ticks=0;visible=List.of();}if(++ticks%5!=0)return;
        double range=VisualModules.number(mod,"range",24,8,64);int limit=(int)VisualModules.number(mod,"limit",32,8,64),minimum=(int)VisualModules.number(mod,"minimum",1,1,64);String filter=HudConfig.str(mod,"filter","All");
        var nearest=new PriorityQueue<ItemEntity>(Comparator.comparingDouble((ItemEntity e)->e.squaredDistanceTo(c.player)).reversed());int scanned=0;
        for(var entity:c.world.getEntities()){if(++scanned>2048)break;if(!(entity instanceof ItemEntity item)||item.isRemoved()||item.squaredDistanceTo(c.player)>range*range||!accepts(item.getStack(),filter,minimum))continue;nearest.add(item);if(nearest.size()>limit)nearest.poll();}
        visible=nearest.stream().sorted(Comparator.comparingDouble(e->e.squaredDistanceTo(c.player))).toList();
    }
    public static int selectedCount(){return visible.size();}
    private static int tint(ItemStack stack,com.google.gson.JsonObject mod){if(!HudConfig.bool(mod,"rarityColors",true))return HudRenderer.parseColor(HudConfig.str(mod,"color","#75CFFFFF"),0xFF75CFFF);return switch(stack.getRarity()){case UNCOMMON->0xFFFFD36E;case RARE->0xFF75CFFF;case EPIC->0xFFC18CFF;default->0xFFEDEDED;};}
    private static void render(WorldFrame ctx){
        var c=MinecraftClient.getInstance();var mod=VisualModules.settings("lootbeams");if(!HudConfig.enabled(mod)||c.player==null||c.world!=world||c.currentScreen!=null||!MinecraftClient.isHudEnabled()||ctx.matrixStack()==null||ctx.consumers()==null)return;
        var camera=ctx.camera().getPos();float delta=ctx.tickDelta(),height=VisualModules.number(mod,"height",2,.5f,6),width=VisualModules.number(mod,"width",.12f,.06f,.4f),opacity=VisualModules.number(mod,"opacity",65,10,100)/100;
        double range=VisualModules.number(mod,"range",24,8,64);int minimum=(int)VisualModules.number(mod,"minimum",1,1,64);String filter=HudConfig.str(mod,"filter","All");
        for(var item:visible){if(item.isRemoved()||item.squaredDistanceTo(c.player)>range*range||!accepts(item.getStack(),filter,minimum))continue;
            var pos=item.getLerpedPos(delta);var bounds=item.getBoundingBox().expand(.5,height,.5);if(ctx.frustum()!=null&&!ctx.frustum().isVisible(bounds))continue;
            int rgb=tint(item.getStack(),mod);float alpha=opacity*((rgb>>>24)/255f);if(HudConfig.bool(mod,"pulse",true))alpha*=.85f+.15f*(float)Math.sin((item.age+delta)*.08);
            var matrix=ctx.matrixStack().peek();var vertices=ctx.consumers().getBuffer(RenderLayer.getDebugQuads());float x=(float)(pos.x-camera.x),y=(float)(pos.y-camera.y)+.03f,z=(float)(pos.z-camera.z);
            int bottom=(Math.round(alpha*150)<<24)|(rgb&0xFFFFFF),top=(Math.round(alpha*18)<<24)|(rgb&0xFFFFFF);
            for(int side=0;side<4;side++){double a=side*Math.PI/2,b=(side+1)*Math.PI/2;float ax=x+(float)Math.cos(a)*width,az=z+(float)Math.sin(a)*width,bx=x+(float)Math.cos(b)*width,bz=z+(float)Math.sin(b)*width;vertices.vertex(matrix,ax,y,az).color(bottom);vertices.vertex(matrix,bx,y,bz).color(bottom);vertices.vertex(matrix,bx,y+height,bz).color(top);vertices.vertex(matrix,ax,y+height,az).color(top);}
            if(HudConfig.bool(mod,"groundGlow",true))for(int i=0;i<24;i++){double a=i*Math.PI/12,b=(i+1)*Math.PI/12;float outer=width*2,inner=outer*.75f;vertices.vertex(matrix,x+(float)Math.cos(a)*outer,y,z+(float)Math.sin(a)*outer).color(bottom);vertices.vertex(matrix,x+(float)Math.cos(b)*outer,y,z+(float)Math.sin(b)*outer).color(bottom);vertices.vertex(matrix,x+(float)Math.cos(b)*inner,y,z+(float)Math.sin(b)*inner).color(bottom);vertices.vertex(matrix,x+(float)Math.cos(a)*inner,y,z+(float)Math.sin(a)*inner).color(bottom);}
            if(HudConfig.bool(mod,"labels",false)){var ms=ctx.matrixStack();ms.push();try{ms.translate(x,y+.45,z);ms.multiply(ctx.camera().getRotation());ms.scale(.02f,-.02f,.02f);String text=c.textRenderer.trimToWidth(item.getStack().getName().getString(),110)+" x"+item.getStack().getCount();c.textRenderer.draw(text,-c.textRenderer.getWidth(text)/2f,0,0xFFFFFFFF,true,ms.peek().getPositionMatrix(),ctx.consumers(),net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL,0,LightmapTextureManager.MAX_LIGHT_COORDINATE);}finally{ms.pop();}}
        }
    }
}
