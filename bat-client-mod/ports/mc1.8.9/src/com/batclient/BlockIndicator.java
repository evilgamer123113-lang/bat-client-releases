package com.batclient;

import com.batclient.mixin.BlockBreakingAccessor;
import com.google.gson.JsonObject;
import java.util.Objects;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/** Read-only block information for 1.8 / 1.8.9. */
public final class BlockIndicator {
    public static final class Info {
        private final String name;
        private final String id;
        private final ItemStack icon;
        private final int breakingPercent;
        public Info(String name, String id, ItemStack icon, int breakingPercent) {
            this.name = name;
            this.id = id;
            this.icon = icon;
            this.breakingPercent = breakingPercent;
        }
        public String name() { return this.name; }
        public String id() { return this.id; }
        public ItemStack icon() { return this.icon; }
        public int breakingPercent() { return this.breakingPercent; }
        @Override public boolean equals(Object obj) { if (this == obj) return true; if (!(obj instanceof Info)) return false; Info o = (Info) obj; return Objects.equals(this.name, o.name) && Objects.equals(this.id, o.id) && Objects.equals(this.icon, o.icon) && this.breakingPercent == o.breakingPercent; }
        @Override public int hashCode() { return Objects.hash(name, id, icon, breakingPercent); }
    }

    private static final com.batclient.hud.RenderPassCache<Info> RENDER_QUERY = new com.batclient.hud.RenderPassCache<>();
    public static void beginRender() { RENDER_QUERY.begin(); }
    public static void endRender() { RENDER_QUERY.end(); }

    public static Info read(MinecraftClient client, JsonObject mod, boolean preview) {
        if (RENDER_QUERY.matches(client, mod, preview)) return RENDER_QUERY.value();
        Info info = readUncached(client, mod, preview);
        RENDER_QUERY.remember(client, mod, preview, info);
        return info;
    }

    private static Info readUncached(MinecraftClient client, JsonObject mod, boolean preview) {
        if (client.world != null && client.getCameraEntity() != null) {
            double range = 12;
            try { range = Double.parseDouble(HudConfig.str(mod, "range", "12")); }
            catch (NumberFormatException ignored) {}
            if (!Double.isFinite(range)) range = 12;
            range = MathHelper.clamp(range, 1, 32);
            BlockHitResult hit = client.getCameraEntity().rayTrace(range, 1f);
            if (hit != null && hit.type == BlockHitResult.Type.BLOCK) {
                BlockPos pos = hit.getBlockPos();
                BlockState state = client.world.getBlockState(pos);
                Block block = state.getBlock();
                if (block != Blocks.AIR) {
                    int progress = -1;
                    if (HudConfig.bool(mod, "showBreaking", true) && client.interactionManager != null
                            && client.interactionManager.isBreakingBlock()) {
                        BlockBreakingAccessor breaking = (BlockBreakingAccessor) client.interactionManager;
                        if (pos.equals(breaking.bat$getBreakingPos())) {
                            progress = MathHelper.clamp(Math.round(breaking.bat$getBreakingProgress() * 100), 0, 100);
                        }
                    }
                    Object id = Block.REGISTRY.getIdentifier(block);
                    return new Info(block.getTranslatedName(), String.valueOf(id), new ItemStack(block), progress);
                }
            }
        }
        return preview ? new Info(Blocks.DIRT.getTranslatedName(), "minecraft:dirt", new ItemStack(Blocks.DIRT), -1) : null;
    }

    public static String title(Info info) {
        return info.name() + (info.breakingPercent() >= 0 ? " (" + info.breakingPercent() + "%)" : "");
    }
}
