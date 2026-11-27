package com.batclient;

import com.batclient.hud.ItemCounter;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Main inventory/hotbar only; never counts armor or container slots. */
public final class ItemCounterHud {
    private ItemCounterHud() { }

    public static int count(MinecraftClient client, String id, JsonObject mod, boolean preview) {
        if (preview) return id.equals("totems") ? 3 : 64;
        if (client.player == null) return 0;
        boolean hotbar = "Hotbar".equals(HudConfig.str(mod, "scope", "Inventory"));
        int mainSlots = hotbar ? 9 : 36;
        return ItemCounter.count(mainSlots,
            slot -> matches(client.player.inventory.main[slot], id, mod),
            slot -> {
                ItemStack st = client.player.inventory.main[slot];
                return st == null ? 0 : st.count;
            });
    }

    private static boolean matches(ItemStack stack, String id, JsonObject mod) {
        if (stack == null || stack.getItem() == null) return false;
        if (id.equals("totems")) return false;
        return stack.getItem() == Items.ARROW;
    }

    public static ItemStack icon(String id) {
        return new ItemStack(id.equals("totems") ? Items.GOLDEN_APPLE : Items.ARROW);
    }

    public static String text(String id, JsonObject mod, int count) {
        return (HudConfig.bool(mod, "label", true) ? (id.equals("totems") ? "Totems: " : "Arrows: ") : "") + count;
    }

    public static boolean warning(String id, JsonObject mod, int count) {
        int fallback = id.equals("totems") ? 1 : 16;
        return ItemCounter.warning(count, HudConfig.bool(mod, "lowWarning", true), HudConfig.setNumber(mod, "warningCount", fallback), fallback);
    }
}
