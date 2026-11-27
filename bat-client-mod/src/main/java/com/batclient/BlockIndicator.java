package com.batclient;

import com.batclient.mixin.BlockBreakingAccessor;
import com.google.gson.JsonObject;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;

/** Read-only block information; never changes interaction reach or world state. */
public final class BlockIndicator {
    public record Info(String name, String id, ItemStack icon, int breakingPercent) {}
    private static final com.batclient.hud.RenderPassCache<Info> RENDER_QUERY=new com.batclient.hud.RenderPassCache<>();
    public static void beginRender(){RENDER_QUERY.begin();}
    public static void endRender(){RENDER_QUERY.end();}
    public static Info read(MinecraftClient client, JsonObject mod, boolean preview) {
        if(RENDER_QUERY.matches(client,mod,preview))return RENDER_QUERY.value();
        Info info=readUncached(client,mod,preview);
        RENDER_QUERY.remember(client,mod,preview,info);
        return info;
    }
    private static Info readUncached(MinecraftClient client, JsonObject mod, boolean preview) {
        if (client.world != null && client.getCameraEntity() != null) {
            double range = 12;
            try { range = Double.parseDouble(HudConfig.str(mod, "range", "12")); }
            catch (NumberFormatException ignored) {}
            if (!Double.isFinite(range)) range = 12;
            range = MathHelper.clamp(range, 1, 32);
            HitResult hit = client.getCameraEntity().raycast(range, 1f, false);
            if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
                var pos = blockHit.getBlockPos();
                var state = client.world.getBlockState(pos);
                if (!state.isAir()) {
                    int progress = -1;
                    if (HudConfig.bool(mod, "showBreaking", true) && client.interactionManager != null
                            && client.interactionManager.isBreakingBlock()) {
                        var breaking = (BlockBreakingAccessor)client.interactionManager;
                        if (pos.equals(breaking.bat$getBreakingPos()))
                            progress = MathHelper.clamp(Math.round(breaking.bat$getBreakingProgress() * 100), 0, 100);
                    }
                    return new Info(state.getBlock().getName().getString(), Registries.BLOCK.getId(state.getBlock()).toString(),
                        new ItemStack(state.getBlock()), progress);
                }
            }
        }
        return preview ? new Info(Blocks.DIRT.getName().getString(), "minecraft:dirt", new ItemStack(Blocks.DIRT), -1) : null;
    }
    public static String title(Info info) { return info.name() + (info.breakingPercent() >= 0 ? " (" + info.breakingPercent() + "%)" : ""); }
}
