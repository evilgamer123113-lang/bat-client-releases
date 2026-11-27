package com.batclient;

import com.batclient.platform.mc1194.DrawContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.HorseBaseEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.FoodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Live, native HUD data. No changes to food, arrows or mount movement. */
public final class AdventureHud {
    private AdventureHud() { }

    public static ItemStack food(MinecraftClient c, JsonObject mod, boolean preview) {
        if (preview) return new ItemStack(Items.COOKED_BEEF);
        if (c.player == null) return null;
        ItemStack main = c.player.getMainHandStack();
        return main != null && main.getItem() instanceof FoodItem ? main : null;
    }

    public static float charge(MinecraftClient c, boolean preview) {
        if (preview) return .75f;
        if (c.player == null || !c.player.isUsingItem()) return -1;
        ItemStack active = c.player.getUsedItem();
        if (active == null || !(active.getItem() instanceof BowItem)) return -1;
        int ticks = c.player.getItemUseTicks();
        float f = ticks / 20.0f;
        f = (f * f + f * 2.0f) / 3.0f;
        return Math.min(1.0f, f);
    }

    public static List<String> lines(MinecraftClient c, String id, JsonObject mod, boolean preview) {
        List<String> lines = new ArrayList<>();
        switch (id) {
            case "foodpreview" -> {
                ItemStack stack = food(c, mod, preview);
                if (stack == null || !(stack.getItem() instanceof FoodItem food)) return lines;
                int hunger = preview ? 12 : c.player.getHungerManager().getFoodLevel();
                int nutrition = food.getHungerPoints(stack);
                int restored = Math.min(nutrition, 20 - hunger);
                lines.add(new net.minecraft.text.TranslatableText(stack.getTranslationKey() + ".name").asUnformattedString());
                lines.add("Hunger: +" + nutrition + " (" + restored + " usable)");
                if (HudConfig.bool(mod, "saturation", true)) {
                    lines.add(String.format(Locale.ROOT, "Saturation: +%.1f", nutrition * food.getSaturation(stack) * 2.0f));
                }
            }
            case "bowcharge" -> {
                float charge = charge(c, preview);
                if (charge < 0) return lines;
                lines.add(charge >= 1 && HudConfig.bool(mod, "ready", true) ? "Bow: Ready!" : "Bow: " + Math.round(charge * 100) + "%");
                if (HudConfig.bool(mod, "drawTime", false)) {
                    lines.add(String.format(Locale.ROOT, "Draw: %.2fs", preview ? .75 : c.player.getItemUseTicks() / 20.0));
                }
            }
            case "mounthud" -> {
                HorseBaseEntity horse = c.player != null && c.player.vehicle instanceof HorseBaseEntity h ? h : null;
                if (!preview && horse == null) return lines;
                lines.add(preview ? "Horse" : horse.getName().asUnformattedString());
                if (HudConfig.bool(mod, "health", true)) {
                    lines.add(String.format(Locale.ROOT, "Health: %.1f / %.1f", preview ? 24f : horse.getHealth(), preview ? 30f : horse.getMaxHealth()));
                }
                if (HudConfig.bool(mod, "speed", true)) {
                    double speed = preview ? 7.5 : horse.initializeAttribute(EntityAttributes.GENERIC_MOVEMENT_SPEED).getValue() * 42.157787;
                    lines.add(String.format(Locale.ROOT, "Max speed: ~%.2f blocks/s", speed));
                }
                if (HudConfig.bool(mod, "currentSpeed", true)) {
                    lines.add(String.format(Locale.ROOT, "Current: %.2f blocks/s",
                        preview ? 4.2 : Math.hypot(horse.x - horse.prevX, horse.z - horse.prevZ) * 20));
                }
                if (HudConfig.bool(mod, "jump", true)) {
                    double j = preview ? .7 : horse.getJumpStrength();
                    double blocks = -.1817584952 * j * j * j + 3.689713992 * j * j + 2.128599134 * j - .343930367;
                    lines.add(String.format(Locale.ROOT, "Jump: ~%.2f blocks", Math.max(0, blocks)));
                }
            }
            case "deathcoords" -> {
                DeathCoordinates.Death death = DeathCoordinates.last(c, preview);
                if (death == null) return lines;
                lines.add("Last death");
                lines.add(death.x() + ", " + death.y() + ", " + death.z());
                if (HudConfig.bool(mod, "dimension", true)) lines.add(death.dimension().replace("minecraft:", ""));
                if (HudConfig.bool(mod, "distance", true)) {
                    if (preview) lines.add("120m - NE");
                    else if (c.player != null && c.world != null && death.dimension().equals(String.valueOf(c.world.dimension.getType()))) {
                        double dx = death.x() + .5 - c.player.x, dz = death.z() + .5 - c.player.z;
                        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
                        int bearing = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45), 8);
                        long dist = Math.round(new Vec3d(c.player.x, c.player.y, c.player.z).distanceTo(new Vec3d(death.x() + .5, death.y(), death.z() + .5)));
                        lines.add(dist + "m - " + directions[bearing]);
                    } else lines.add("Different dimension");
                }
            }
        }
        return lines;
    }

    private static int barWidth(JsonObject mod) { return (int) VisualModules.number(mod, "barWidth", 110, 90, 180); }

    private static boolean bar(String id, JsonObject mod) {
        return id.equals("bowcharge")
            || id.equals("foodpreview") && HudConfig.bool(mod, "hungerBar", true)
            || id.equals("mounthud") && HudConfig.bool(mod, "healthBar", true);
    }

    public static int width(MinecraftClient c, String id, JsonObject mod, boolean preview) {
        int width = 0;
        for (String line : lines(c, id, mod, preview)) width = Math.max(width, c.textRenderer.getStringWidth(line));
        if (HudConfig.bool(mod, "icons", true)) width += 22;
        return bar(id, mod) ? Math.max(id.equals("foodpreview") ? 90 : barWidth(mod), width) : width;
    }

    public static int height(MinecraftClient c, String id, JsonObject mod, boolean preview) {
        List<String> lines = lines(c, id, mod, preview);
        return lines.isEmpty() ? 0 : Math.max(HudConfig.bool(mod, "icons", true) ? 16 : 0, lines.size() * 11) + (bar(id, mod) ? 13 : 0);
    }

    public static void draw(DrawContext ctx, MinecraftClient c, String id, JsonObject mod, boolean preview) {
        List<String> lines = lines(c, id, mod, preview);
        int color = HudRenderer.parseColor(HudConfig.str(mod, "color", "#FFFFFFFF"), 0xFFFFFFFF);
        if (lines.isEmpty()) return;
        int textX = HudConfig.bool(mod, "icons", true) ? 22 : 0;
        if (textX > 0) {
            ItemStack icon = "foodpreview".equals(id) ? food(c, mod, preview)
                : ("bowcharge".equals(id) ? new ItemStack(Items.BOW)
                : ("mounthud".equals(id) ? new ItemStack(Items.SADDLE) : new ItemStack(Items.COMPASS)));
            if (icon != null) ctx.drawItem(icon, 0, 0);
        }
        for (int i = 0; i < lines.size(); i++) {
            ctx.drawText(c.textRenderer, lines.get(i), textX, i * 11, color, HudConfig.bool(mod, "shadow", true));
        }
        int barY = Math.max(textX > 0 ? 16 : 0, lines.size() * 11) + 3, barWidth = barWidth(mod);
        if (id.equals("bowcharge")) {
            float charge = charge(c, preview);
            ctx.fill(0, barY, barWidth, barY + 5, 0x88333333);
            int fill = charge >= 1 ? 0xFF55FF55 : HudRenderer.parseColor(HudConfig.str(mod, "chargeColor", "#FFCC55FF"), 0xFFFFCC55);
            ctx.fill(0, barY, Math.round(barWidth * charge), barY + 5, fill);
            for (int n = 1; n < 4; n++) ctx.fill(n * barWidth / 4, barY, n * barWidth / 4 + 1, barY + 5, 0x66000000);
        }
        if (id.equals("mounthud") && HudConfig.bool(mod, "healthBar", true)) {
            HorseBaseEntity horse = c.player != null && c.player.vehicle instanceof HorseBaseEntity h ? h : null;
            float ratio = preview ? .8f : horse == null ? 0 : horse.getHealth() / Math.max(1, horse.getMaxHealth());
            ctx.fill(0, barY, barWidth, barY + 5, 0x88333333);
            ctx.fill(0, barY, Math.round(barWidth * MathHelper.clamp(ratio, 0, 1)), barY + 5, ratio < .25 ? 0xFFFF5555 : 0xFF55DD77);
        }
        if (id.equals("foodpreview") && HudConfig.bool(mod, "hungerBar", true)) {
            int hunger = preview ? 12 : c.player.getHungerManager().getFoodLevel();
            ItemStack st = food(c, mod, preview);
            int nutrition = st != null && st.getItem() instanceof FoodItem f ? f.getHungerPoints(st) : 0;
            int after = Math.min(20, hunger + nutrition);
            c.getTextureManager().bindTexture(DrawableHelper.GUI_ICONS_TEXTURE);
            GlStateManager.enableBlend();
            for (int i = 0; i < 10; i++) {
                int before = MathHelper.clamp(hunger - i * 2, 0, 2);
                int later = MathHelper.clamp(after - i * 2, 0, 2);
                ctx.drawTexture(DrawableHelper.GUI_ICONS_TEXTURE, i * 9, barY, 16, 27, 9, 9);
                if (later > before) {
                    GlStateManager.color(.45f, 1f, .45f, .85f);
                    ctx.drawTexture(DrawableHelper.GUI_ICONS_TEXTURE, i * 9, barY, later == 1 ? 61 : 52, 27, 9, 9);
                    GlStateManager.color(1f, 1f, 1f, 1f);
                }
                if (before > 0) ctx.drawTexture(DrawableHelper.GUI_ICONS_TEXTURE, i * 9, barY, before == 1 ? 61 : 52, 27, 9, 9);
            }
        }
    }
}
