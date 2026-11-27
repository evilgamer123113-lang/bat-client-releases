package com.batclient;
import com.batclient.hud.InventoryDeltaTracker;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.ComponentMap;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Util;
import java.util.*;

public final class ItemPickupTracker {
    private record Key(Item item,ComponentMap components) { }
    private record Row(ItemStack icon,int delta,long expires) { }
    private static final InventoryDeltaTracker<Key> TRACKER=new InventoryDeltaTracker<>();
    private static final Map<Key,ItemStack> ICONS=new HashMap<>();
    private static Object world,player;
    private static int ticks;
    private ItemPickupTracker() { }
    public static void tick(MinecraftClient client){
        var mod=VisualModules.settings("itemtracker");
        if(world!=client.world||player!=client.player||client.player==null||client.player.isDead()||!HudConfig.enabled(mod)){
            TRACKER.reset();ICONS.clear();ticks=-10;world=client.world;player=client.player;
            if(client.player==null||client.player.isDead()||!HudConfig.enabled(mod))return;
        }
        // Let the initial inventory packets arrive before taking the silent baseline.
        if(ticks++<0||ticks%5!=0)return;
        Map<Key,Integer> counts=new LinkedHashMap<>();
        for(var stack:client.player.getInventory().main)add(counts,stack);
        for(var stack:client.player.getInventory().armor)add(counts,stack);
        for(var stack:client.player.getInventory().offHand)add(counts,stack);
        long now=Util.getMeasuringTimeMs();
        TRACKER.sample(counts,now,(long)(VisualModules.number(mod,"duration",4,1,10)*1000),HudConfig.bool(mod,"gains",true),HudConfig.bool(mod,"losses",true));
        Set<Key> live=new HashSet<>(counts.keySet());for(var entry:TRACKER.entries(now))live.add(entry.key());ICONS.keySet().retainAll(live);
    }
    private static void add(Map<Key,Integer> counts,ItemStack stack){
        if(stack.isEmpty())return;
        var normalized=stack.copyWithCount(1);if(normalized.isDamageable())normalized.setDamage(0);
        Key key=new Key(normalized.getItem(),normalized.getComponents());counts.merge(key,stack.getCount(),Integer::sum);ICONS.put(key,normalized);
    }
    private static List<Row> rows(JsonObject mod,boolean preview){
        if(preview)return List.of(new Row(new ItemStack(Items.COBBLESTONE),32,Long.MAX_VALUE),new Row(new ItemStack(Items.DIAMOND),-1,Long.MAX_VALUE),new Row(new ItemStack(Items.ENDER_PEARL),4,Long.MAX_VALUE));
        var out=new ArrayList<Row>();int limit=(int)VisualModules.number(mod,"rows",4,1,8);
        for(var entry:TRACKER.entries(Util.getMeasuringTimeMs())){
            if(entry.delta()>0&&!HudConfig.bool(mod,"gains",true)||entry.delta()<0&&!HudConfig.bool(mod,"losses",true))continue;
            var icon=ICONS.get(entry.key());if(icon!=null)out.add(new Row(icon,entry.delta(),entry.expires()));if(out.size()>=limit)break;
        }
        return out;
    }
    public static int width(JsonObject mod){return (int)VisualModules.number(mod,"width",180,100,280);}
    public static int height(JsonObject mod,boolean preview){return rows(mod,preview).size()*22;}
    public static void draw(DrawContext ctx,JsonObject mod,boolean preview){
        var renderer=MinecraftClient.getInstance().textRenderer;int y=0,width=width(mod);boolean icons=HudConfig.bool(mod,"icons",true),shadow=HudConfig.bool(mod,"shadow",true);
        for(var row:rows(mod,preview)){
            int tint=HudRenderer.parseColor(HudConfig.str(mod,row.delta()>0?"gainColor":"lossColor",row.delta()>0?"#55FF55FF":"#FF5555FF"),row.delta()>0?0xFF55FF55:0xFFFF5555);
            if(HudConfig.bool(mod,"fade",true)&&!preview){int alpha=(int)(255*Math.clamp((row.expires()-Util.getMeasuringTimeMs())/500f,0,1));tint=(tint&0xFFFFFF)|(alpha<<24);}
            int x=icons?22:0;String amount=(row.delta()>0?"+":"")+row.delta();int amountWidth=renderer.getWidth(amount);
            if(icons)ctx.drawItem(row.icon(),0,y);
            ctx.drawText(renderer,renderer.trimToWidth(row.icon().getName().getString(),Math.max(1,width-x-amountWidth-8)),x,y+4,tint,shadow);
            ctx.drawText(renderer,amount,width-amountWidth,y+4,tint,shadow);y+=22;
        }
    }
}
