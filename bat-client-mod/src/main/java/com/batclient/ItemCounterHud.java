package com.batclient;

import com.batclient.hud.ItemCounter;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Main inventory/hotbar and optional offhand only; never counts armor or container slots. */
public final class ItemCounterHud {
    private ItemCounterHud() { }
    public static int count(MinecraftClient client, String id, JsonObject mod, boolean preview) {
        if (preview) return id.equals("totems") ? 3 : 64;
        if (client.player == null) return 0;
        boolean hotbar = "Hotbar".equals(HudConfig.str(mod,"scope","Inventory"));
        int mainSlots = hotbar ? 9 : 36;
        boolean offhand = HudConfig.bool(mod,"offhand",true);
        return ItemCounter.count(mainSlots+(offhand?1:0),
            slot -> matches(slot < mainSlots ? client.player.getInventory().getStack(slot) : client.player.getOffHandStack(),id,mod),
            slot -> (slot < mainSlots ? client.player.getInventory().getStack(slot) : client.player.getOffHandStack()).getCount());
    }
    private static boolean matches(ItemStack stack,String id,JsonObject mod) {
        if (stack.isEmpty()) return false;
        if (id.equals("totems")) return stack.isOf(Items.TOTEM_OF_UNDYING);
        return stack.isOf(Items.ARROW) || HudConfig.bool(mod,"specialArrows",true) &&
            (stack.isOf(Items.SPECTRAL_ARROW) || stack.isOf(Items.TIPPED_ARROW));
    }
    public static ItemStack icon(String id) {
        return new ItemStack(id.equals("totems") ? Items.TOTEM_OF_UNDYING : Items.ARROW);
    }
    public static String text(String id,JsonObject mod,int count) {
        return (HudConfig.bool(mod,"label",true) ? id.equals("totems") ? "Totems: " : "Arrows: " : "") + count;
    }
    public static boolean warning(String id,JsonObject mod,int count) {
        int fallback = id.equals("totems") ? 1 : 16;
        return ItemCounter.warning(count,HudConfig.bool(mod,"lowWarning",true),HudConfig.setNumber(mod,"warningCount",fallback),fallback);
    }
}
