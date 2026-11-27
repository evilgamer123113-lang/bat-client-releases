package com.batclient;

import com.batclient.hud.CombatTracker;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Combat reach & combo tracker for 1.8 / 1.8.9. */
public final class CombatHud {
    private static final CombatTracker tracker = new CombatTracker();
    private static Object world;
    private static Object player;
    private static boolean pendingPlayer;
    private static double liveReach = Double.NaN;

    private CombatHud() {}

    private static void synchronize(MinecraftClient client) {
        if (world != client.world || player != client.player || client.player == null || client.player.removed || client.player.getHealth() <= 0) {
            tracker.reset();
            world = client.world;
            player = client.player;
            pendingPlayer = false;
            liveReach = Double.NaN;
        }
    }

    public static void tick(MinecraftClient client) {
        synchronize(client);
        liveReach = Double.NaN;
        JsonObject mod = HudConfig.module(HudConfig.snapshot(), "reach");
        if (client.player == null || client.world == null || client.player.removed || !HudConfig.enabled(mod)) return;
        float configured = HudConfig.setNumber(mod, "range", 32);
        double range = Float.isFinite(configured) ? MathHelper.clamp(configured, 4, 64) : 32;
        Vec3d eye = new Vec3d(client.player.x, client.player.y + client.player.getEyeHeight(), client.player.z);
        Vec3d look = client.player.getRotationVector(1.0F);
        Vec3d direction = new Vec3d(look.x * range, look.y * range, look.z * range);
        Vec3d end = eye.add(direction);
        BlockHitResult block = client.world.rayTrace(eye, end, false, false, false);
        double bestDistSq = block != null && block.pos != null ? eye.squaredDistanceTo(block.pos) : range * range;
        boolean playersOnly = HudConfig.bool(mod, "playersOnly", false);
        Box searchBox = client.player.getBoundingBox().stretch(direction.x, direction.y, direction.z).expand(1.0, 1.0, 1.0);
        List<Entity> candidates = client.world.getEntitiesIn(client.player, searchBox);
        Entity closest = null;
        for (Entity entity : candidates) {
            if (!(entity instanceof LivingEntity) || !entity.isAlive() || (playersOnly && !(entity instanceof PlayerEntity))) continue;
            if (entity instanceof PlayerEntity && ((PlayerEntity) entity).isSpectator()) continue;
            float margin = entity.getTargetingMargin();
            Box box = entity.getBoundingBox().expand(margin, margin, margin);
            Vec3d intercept = rayTraceBox(box, eye, end);
            if (box.contains(eye)) {
                closest = entity;
                bestDistSq = 0.0;
            } else if (intercept != null) {
                double d = eye.squaredDistanceTo(intercept);
                if (d < bestDistSq) {
                    bestDistSq = d;
                    closest = entity;
                }
            }
        }
        if (closest != null) liveReach = client.player.distanceTo(closest);
    }

    private static Vec3d rayTraceBox(Box box, Vec3d start, Vec3d end) {
        double dx = end.x - start.x, dy = end.y - start.y, dz = end.z - start.z;
        double tMin = 0.0, tMax = 1.0;
        double[] s = {start.x, start.y, start.z}, d = {dx, dy, dz};
        double[] mn = {box.minX, box.minY, box.minZ}, mx = {box.maxX, box.maxY, box.maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9) {
                if (s[i] < mn[i] || s[i] > mx[i]) return null;
            } else {
                double t1 = (mn[i] - s[i]) / d[i], t2 = (mx[i] - s[i]) / d[i];
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) return null;
            }
        }
        return new Vec3d(start.x + dx * tMin, start.y + dy * tMin, start.z + dz * tMin);
    }

    public static void attack(PlayerEntity attacker, Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        synchronize(client);
        if (attacker != client.player || attacker.isSpectator() || !(entity instanceof LivingEntity)) return;
        com.batclient.performance.BuiltinPerformance.combat();
        JsonObject cfg = HudConfig.snapshot();
        if (!HudConfig.enabled(HudConfig.module(cfg, "combo"))) return;
        Vec3d eye = new Vec3d(attacker.x, attacker.y + attacker.getEyeHeight(), attacker.z);
        Vec3d point;
        if (client.result != null && client.result.entity == entity && client.result.pos != null) {
            point = client.result.pos;
        } else {
            Box box = entity.getBoundingBox();
            point = new Vec3d(MathHelper.clamp(eye.x, box.minX, box.maxX), MathHelper.clamp(eye.y, box.minY, box.maxY), MathHelper.clamp(eye.z, box.minZ, box.maxZ));
        }
        pendingPlayer = entity instanceof PlayerEntity;
        tracker.attack(entity.getEntityId(), eye.distanceTo(point), System.nanoTime() / 1_000_000L);
    }

    public static void damage(EntityStatusS2CPacket packet) {
        if (packet.getStatus() != 2) return;
        MinecraftClient client = MinecraftClient.getInstance();
        synchronize(client);
        if (client.player == null || client.world == null) return;
        Entity entity = packet.getEntity(client.world);
        if (entity == null) return;
        if (entity == client.player) { tracker.damaged(); return; }
        JsonObject cfg = HudConfig.snapshot();
        JsonObject combo = HudConfig.module(cfg, "combo");
        tracker.confirm(entity.getEntityId(), System.nanoTime() / 1_000_000L, timeout(combo, 3),
            HudConfig.enabled(combo) && (!HudConfig.bool(combo, "playersOnly", false) || pendingPlayer), false);
    }

    private static long timeout(JsonObject mod, float fallback) {
        float n = HudConfig.setNumber(mod, "timeout", fallback);
        return (long) ((Float.isFinite(n) ? MathHelper.clamp(n, 1, 10) : fallback) * 1000);
    }

    public static String text(String id, JsonObject mod, boolean preview) {
        long now = System.nanoTime() / 1_000_000L;
        if (id.equals("combo")) return "Combo: " + (preview ? 3 : tracker.combo(now, timeout(mod, 3)));
        double value = preview ? 2.85 : liveReach;
        int digits = (int) MathHelper.clamp(HudConfig.setNumber(mod, "decimals", 2), 1, 3);
        return Double.isFinite(value) ? String.format(Locale.ROOT, "Reach: %." + digits + "f blocks", value) : "Reach: --";
    }

    public static boolean empty(String id, JsonObject mod) {
        long now = System.nanoTime() / 1_000_000L;
        return id.equals("combo") ? tracker.combo(now, timeout(mod, 3)) == 0 : !Double.isFinite(liveReach);
    }
}
