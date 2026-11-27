package com.batclient;

import com.batclient.hud.HealthValues;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.WeakHashMap;

/** Read-only client target, with brief retention and frame-independent bar easing. */
public final class TargetHud {
    public record Info(LivingEntity entity,String name,float health,float maximum,float absorption,float distance,int armor) { }
    private static WeakReference<LivingEntity> previous=new WeakReference<>(null);
    private static long seen;
    private static final WeakHashMap<LivingEntity,Smooth> BARS=new WeakHashMap<>();
    private static final class Smooth {float value;long time;Smooth(float value,long time){this.value=value;this.time=time;}}
    private TargetHud() { }
    public static Info read(MinecraftClient client,JsonObject mod,boolean preview) {
        LivingEntity entity=client.crosshairTarget instanceof EntityHitResult hit&&hit.getEntity() instanceof LivingEntity living?living:null;
        boolean playersOnly=HudConfig.bool(mod,"playersOnly",false);
        long now=System.nanoTime();
        if(entity!=null&&entity.isAlive()&&!entity.isInvisible()&&(!playersOnly||entity instanceof PlayerEntity)){previous=new WeakReference<>(entity);seen=now;}
        else {
            entity=previous.get();
            float hold=Math.max(0,Math.min(3,HudConfig.setNumber(mod,"holdTime",.8f)));
            if(entity==null||now-seen>hold*1_000_000_000L)entity=null;
        }
        if(entity!=null&&(client.world==null||entity.getWorld()!=client.world||!entity.isAlive()||entity.isRemoved()||entity.isInvisible()||(playersOnly&&!(entity instanceof PlayerEntity))))entity=null;
        if(entity==null)return preview?new Info(null,"Zombie",14,20,2,3.2f,2):null;
        return new Info(entity,entity.getName().getString(),HealthValues.health(entity.getHealth(),entity.getMaxHealth()),HealthValues.max(entity.getMaxHealth()),
                HealthValues.absorption(entity.getAbsorptionAmount()),client.player==null?0:client.player.distanceTo(entity),entity.getArmor());
    }
    public static float bar(Info info,boolean smooth) {
        float ratio=HealthValues.ratio(info.health,info.maximum);if(!smooth||info.entity==null)return ratio;
        long now=System.nanoTime();Smooth state=BARS.computeIfAbsent(info.entity,key->new Smooth(ratio,now));
        double elapsed=Math.max(0,Math.min(.25,(now-state.time)/1_000_000_000d));state.time=now;
        state.value+=(ratio-state.value)*(float)(1-Math.exp(-elapsed/.12));return state.value;
    }
    public static int width(JsonObject mod){return Math.round(Math.max(110,Math.min(240,HudConfig.setNumber(mod,"width",140))));}
    public static int height(JsonObject mod){return HudConfig.bool(mod,"armor",true)?43:32;}
    public static String health(Info info,JsonObject mod) {
        return switch(HudConfig.str(mod,"healthFormat","Hearts")) {
            case "Percent" -> Math.round(HealthValues.ratio(info.health,info.maximum)*100)+"%";
            case "Health" -> String.format(Locale.ROOT,"%.1f / %.1f",info.health,info.maximum);
            default -> HealthValues.hearts(info.health,info.maximum,true);
        };
    }
}
