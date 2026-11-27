package com.batclient;

import com.batclient.hud.CombatTracker;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.Locale;

public final class CombatHud {
    private static final CombatTracker tracker=new CombatTracker();
    private static Object world;
    private static Object player;
    private static boolean pendingPlayer;
    private static double liveReach=Double.NaN;
    private CombatHud() { }
    private static void synchronize(MinecraftClient client) {
        if(world!=client.world||player!=client.player||client.player==null||client.player.isDead()){
            tracker.reset();world=client.world;player=client.player;pendingPlayer=false;liveReach=Double.NaN;
        }
    }
    public static void tick(MinecraftClient client){
        synchronize(client);liveReach=Double.NaN;
        var mod=HudConfig.module(HudConfig.snapshot(),"reach");
        if(client.player==null||client.world==null||client.player.isDead()||!HudConfig.enabled(mod))return;
        float configured=HudConfig.setNumber(mod,"range",32);
        double range=Float.isFinite(configured)?Math.clamp(configured,4,64):32;
        Vec3d eye=client.player.getEyePos(),direction=client.player.getRotationVec(1).multiply(range),end=eye.add(direction);
        var block=client.world.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,client.player));
        double limit=eye.squaredDistanceTo(block.getPos());
        boolean playersOnly=HudConfig.bool(mod,"playersOnly",false);
        var hit=ProjectileUtil.raycast(client.player,eye,end,client.player.getBoundingBox().stretch(direction).expand(1),
            entity->entity instanceof LivingEntity&&entity.isAlive()&&!entity.isSpectator()&&(!playersOnly||entity instanceof PlayerEntity),limit);
        if(hit!=null)liveReach=client.player.distanceTo(hit.getEntity());
    }
    public static void attack(PlayerEntity attacker,Entity entity) {
        var client=MinecraftClient.getInstance();synchronize(client);
        if(attacker!=client.player||attacker.isSpectator()||!(entity instanceof LivingEntity))return;
        com.batclient.performance.BuiltinPerformance.combat();
        JsonObject cfg=HudConfig.snapshot();
        if(!HudConfig.enabled(HudConfig.module(cfg,"combo")))return;
        Vec3d eye=attacker.getEyePos(),point;
        if(client.crosshairTarget instanceof EntityHitResult hit&&hit.getEntity()==entity)point=hit.getPos();
        else {var box=entity.getBoundingBox();point=new Vec3d(MathHelper.clamp(eye.x,box.minX,box.maxX),MathHelper.clamp(eye.y,box.minY,box.maxY),MathHelper.clamp(eye.z,box.minZ,box.maxZ));}
        pendingPlayer=entity instanceof PlayerEntity;
        tracker.attack(entity.getId(),eye.distanceTo(point),System.nanoTime()/1_000_000);
    }
    public static void damage(EntityDamageS2CPacket packet) {
        var client=MinecraftClient.getInstance();synchronize(client);if(client.player==null)return;
        int local=client.player.getId();
        if(packet.entityId()==local){tracker.damaged();return;}
        if(packet.sourceCauseId()!=local||packet.sourceDirectId()!=local)return; // Melee only, no projectile/sweep guesses.
        var cfg=HudConfig.snapshot();var combo=HudConfig.module(cfg,"combo");
        tracker.confirm(packet.entityId(),System.nanoTime()/1_000_000,timeout(combo,3),
            HudConfig.enabled(combo)&&(!HudConfig.bool(combo,"playersOnly",false)||pendingPlayer),false);
    }
    private static long timeout(JsonObject mod,float fallback){float n=HudConfig.setNumber(mod,"timeout",fallback);return (long)((Float.isFinite(n)?Math.clamp(n,1,10):fallback)*1000);}
    public static String text(String id,JsonObject mod,boolean preview) {
        long now=System.nanoTime()/1_000_000;
        if(id.equals("combo"))return "Combo: "+(preview?3:tracker.combo(now,timeout(mod,3)));
        double value=preview?2.85:liveReach;
        int digits=(int)Math.clamp(HudConfig.setNumber(mod,"decimals",2),1,3);
        return Double.isFinite(value)?String.format(Locale.ROOT,"Reach: %."+digits+"f blocks",value):"Reach: --";
    }
    public static boolean empty(String id,JsonObject mod){long now=System.nanoTime()/1_000_000;return id.equals("combo")?tracker.combo(now,timeout(mod,3))==0:!Double.isFinite(liveReach);}
}
