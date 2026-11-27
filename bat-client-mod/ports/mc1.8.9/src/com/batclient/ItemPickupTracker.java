package com.batclient;

import com.batclient.hud.InventoryDeltaTracker;
import com.batclient.platform.mc1194.DrawContext;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.MathHelper;

import java.util.*;

public final class ItemPickupTracker {
    private static final class Key {
        final Item item;
        final int meta;

        Key(Item item, int meta) { this.item = item; this.meta = meta; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return meta == k.meta && item == k.item;
        }

        @Override
        public int hashCode() { return 31 * System.identityHashCode(item) + meta; }
    }

    private static final class Row {
        final ItemStack icon;
        final int delta;
        final long expires;

        Row(ItemStack icon, int delta, long expires) { this.icon = icon; this.delta = delta; this.expires = expires; }
        ItemStack icon() { return icon; }
        int delta() { return delta; }
        long expires() { return expires; }
    }

    private static final InventoryDeltaTracker<Key> TRACKER = new InventoryDeltaTracker<>();
    private static final Map<Key, ItemStack> ICONS = new HashMap<>();
    private static Object world, player;
    private static int ticks;

    private ItemPickupTracker() { }

    public static void tick(MinecraftClient client) {
        JsonObject mod = VisualModules.settings("itemtracker");
        if (world != client.world || player != client.player || client.player == null || client.player.removed || !HudConfig.enabled(mod)) {
            TRACKER.reset();
            ICONS.clear();
            ticks = -10;
            world = client.world;
            player = client.player;
            if (client.player == null || client.player.removed || !HudConfig.enabled(mod)) return;
        }
        if (ticks++ < 0 || ticks % 5 != 0) return;
        Map<Key, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : client.player.inventory.main) add(counts, stack);
        for (ItemStack stack : client.player.inventory.armor) add(counts, stack);
        long now = MinecraftClient.getTime();
        TRACKER.sample(counts, now, (long) (VisualModules.number(mod, "duration", 4, 1, 10) * 1000),
            HudConfig.bool(mod, "gains", true), HudConfig.bool(mod, "losses", true));
        Set<Key> live = new HashSet<>(counts.keySet());
        for (InventoryDeltaTracker.Change<Key> entry : TRACKER.entries(now)) live.add(entry.key());
        ICONS.keySet().retainAll(live);
    }

    private static void add(Map<Key, Integer> counts, ItemStack stack) {
        if (stack == null || stack.getItem() == null) return;
        ItemStack normalized = stack.copy();
        normalized.count = 1;
        if (normalized.isDamageable()) normalized.setDamage(0);
        Key key = new Key(normalized.getItem(), normalized.isDamageable() ? 0 : normalized.getData());
        counts.merge(key, stack.count, Integer::sum);
        ICONS.put(key, normalized);
    }

    private static List<Row> rows(JsonObject mod, boolean preview) {
        if (preview) {
            return Arrays.asList(
                new Row(new ItemStack(net.minecraft.item.Item.fromBlock(net.minecraft.block.Blocks.STONE)), 32, Long.MAX_VALUE),
                new Row(new ItemStack(Items.DIAMOND), -1, Long.MAX_VALUE),
                new Row(new ItemStack(Items.ENDER_PEARL), 4, Long.MAX_VALUE)
            );
        }
        List<Row> out = new ArrayList<>();
        int limit = (int) VisualModules.number(mod, "rows", 4, 1, 8);
        for (InventoryDeltaTracker.Change<Key> entry : TRACKER.entries(MinecraftClient.getTime())) {
            if (entry.delta() > 0 && !HudConfig.bool(mod, "gains", true) || entry.delta() < 0 && !HudConfig.bool(mod, "losses", true)) continue;
            ItemStack icon = ICONS.get(entry.key());
            if (icon != null) out.add(new Row(icon, entry.delta(), entry.expires()));
            if (out.size() >= limit) break;
        }
        return out;
    }

    public static int width(JsonObject mod) {
        return (int) VisualModules.number(mod, "width", 180, 100, 280);
    }

    public static int height(JsonObject mod, boolean preview) {
        return rows(mod, preview).size() * 22;
    }

    public static void draw(DrawContext ctx, JsonObject mod, boolean preview) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int y = 0, width = width(mod);
        boolean icons = HudConfig.bool(mod, "icons", true), shadow = HudConfig.bool(mod, "shadow", true);
        for (Row row : rows(mod, preview)) {
            int tint = HudRenderer.parseColor(HudConfig.str(mod, row.delta() > 0 ? "gainColor" : "lossColor",
                row.delta() > 0 ? "#55FF55FF" : "#FF5555FF"), row.delta() > 0 ? 0xFF55FF55 : 0xFFFF5555);
            if (HudConfig.bool(mod, "fade", true) && !preview) {
                int alpha = (int) (255 * MathHelper.clamp((row.expires() - MinecraftClient.getTime()) / 500f, 0, 1));
                tint = (tint & 0xFFFFFF) | (alpha << 24);
            }
            int x = icons ? 22 : 0;
            String amount = (row.delta() > 0 ? "+" : "") + row.delta();
            int amountWidth = renderer.getStringWidth(amount);
            if (icons) ctx.drawItem(row.icon(), 0, y);
            String name = new TranslatableText(row.icon().getTranslationKey() + ".name").asUnformattedString();
            ctx.drawText(renderer, renderer.trimToWidth(name, Math.max(1, width - x - amountWidth - 8)), x, y + 4, tint, shadow);
            ctx.drawText(renderer, amount, width - amountWidth, y + 4, tint, shadow);
            y += 22;
        }
    }
}
