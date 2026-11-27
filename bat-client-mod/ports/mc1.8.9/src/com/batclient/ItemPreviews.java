package com.batclient;

import com.batclient.platform.ClientHookServices;
import com.batclient.platform.mc1194.DrawContext;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.attribute.AttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.inventory.slot.Slot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Read-only previews from item data already supplied by Minecraft. */
public final class ItemPreviews {
    private ItemPreviews() { }

    public static void init() {
        ClientHookServices.get().itemTooltip((stack, lines) -> {
            JsonObject mod = VisualModules.settings("equipmentcomparison");
            net.minecraft.entity.player.ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (!HudConfig.enabled(mod) || player == null || (HudConfig.bool(mod, "shift", true) && !Screen.hasShiftDown())) return;
            if (stack == null || stack.getItem() == null) return;
            if (stack.getItem() instanceof ArmorItem armor) {
                int armorIdx = 3 - armor.slot;
                ItemStack equipped = armorIdx >= 0 && armorIdx < 4 ? player.inventory.armor[armorIdx] : null;
                if (sameStack(stack, equipped)) return;
                lines.add(new LiteralText("Compared with equipped armor").setStyle(new Style().setFormatting(Formatting.GRAY)));
                compareArmor(lines, armor, stack, equipped, HudConfig.bool(mod, "durability", true));
            } else if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof ToolItem) {
                ItemStack equipped = player.getMainHandStack();
                if (sameStack(stack, equipped)) return;
                lines.add(new LiteralText("Compared with main hand").setStyle(new Style().setFormatting(Formatting.GRAY)));
                compareWeapon(lines, stack, equipped, HudConfig.bool(mod, "durability", true));
            }
        });
    }

    private static void compareArmor(List<Text> lines, ArmorItem hovered, ItemStack stack, ItemStack equipped, boolean durability) {
        int eqProt = equipped != null && equipped.getItem() instanceof ArmorItem eq ? eq.protection : 0;
        delta(lines, "Armor", hovered.protection - eqProt);
        if (durability && stack.isDamageable()) {
            int eqDur = equipped != null && equipped.isDamageable() ? equipped.getMaxDamage() - equipped.getDamage() : 0;
            delta(lines, "Durability", (stack.getMaxDamage() - stack.getDamage()) - eqDur);
        }
        lines.add(new LiteralText("Base item stats; enchantments excluded").setStyle(new Style().setFormatting(Formatting.DARK_GRAY)));
    }

    private static void compareWeapon(List<Text> lines, ItemStack stack, ItemStack equipped, boolean durability) {
        double d1 = attackDamage(stack), d2 = attackDamage(equipped);
        delta(lines, "Attack damage", (float) (d1 - d2));
        if (durability && stack.isDamageable()) {
            int eqDur = equipped != null && equipped.isDamageable() ? equipped.getMaxDamage() - equipped.getDamage() : 0;
            delta(lines, "Durability", (stack.getMaxDamage() - stack.getDamage()) - eqDur);
        }
        lines.add(new LiteralText("Base item stats; enchantments excluded").setStyle(new Style().setFormatting(Formatting.DARK_GRAY)));
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getItem() == b.getItem() && a.getData() == b.getData();
    }

    private static double attackDamage(ItemStack stack) {
        if (stack == null) return 1.0;
        Collection<AttributeModifier> mods = stack.getAttributes().get(EntityAttributes.GENERIC_ATTACK_DAMAGE.getId());
        double base = 1.0;
        if (mods != null) {
            for (AttributeModifier m : mods) {
                if (m.getOperation() == 0) base += m.getAmount();
            }
        }
        return base;
    }

    private static void delta(List<Text> lines, String name, float value) {
        Formatting fmt = value > 0 ? Formatting.GREEN : (value < 0 ? Formatting.RED : Formatting.GRAY);
        lines.add(new LiteralText(String.format(Locale.ROOT, "%s: %+.1f", name, value)).setStyle(new Style().setFormatting(fmt)));
    }

    public static boolean inspect(DrawContext ctx, Slot slot, int mouseX, int mouseY) {
        return false;
    }
}
