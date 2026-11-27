package com.batclient;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Data shared by native HUD drawing, native measurements and editor previews. */
public final class ExtraHud {
    private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);
    private static final DateTimeFormatter H24S = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter H12S = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ROOT);
    private static Vec3d previous;
    private static Object world;
    private static double horizontalSpeed, totalSpeed;
    private ExtraHud() { }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.isPaused()) { previous = null; world = null; horizontalSpeed = totalSpeed = 0; return; }
        Vec3d current = client.player.getPos();
        if (previous != null && world == client.world) {
            Vec3d delta = current.subtract(previous);
            // A dimension change/teleport is not movement speed.
            horizontalSpeed = delta.lengthSquared() > 4096 ? 0 : Math.hypot(delta.x, delta.z) * 20;
            totalSpeed = delta.lengthSquared() > 4096 ? 0 : delta.length() * 20;
        } else horizontalSpeed = totalSpeed = 0;
        previous = current; world = client.world;
    }

    public static ItemStack held(MinecraftClient client, JsonObject mod, boolean preview) {
        ItemStack stack = client.player == null ? ItemStack.EMPTY : "Offhand".equals(HudConfig.str(mod,"hand","Main hand")) ? client.player.getOffHandStack() : client.player.getMainHandStack();
        return stack.isEmpty() && preview ? new ItemStack(Items.DIAMOND_SWORD) : stack;
    }

    public static double heading(MinecraftClient client) {
        double value = client.player == null ? 0 : client.player.getYaw() + 180;
        return ((value % 360) + 360) % 360;
    }

    public static List<String> lines(MinecraftClient client, String id, JsonObject mod, boolean preview) {
        if (id.equals("speedometer")) {
            double speed = preview ? 4.32 : HudConfig.bool(mod,"vertical",false) ? totalSpeed : horizontalSpeed;
            boolean km = "km/h".equals(HudConfig.str(mod,"unit","blocks/s"));
            int decimals = Math.max(0, Math.min(2, (int)HudConfig.setNumber(mod,"decimals",2)));
            return List.of(String.format(Locale.ROOT,"%." + decimals + "f %s",km ? speed * 3.6 : speed,km ? "km/h" : "blocks/s"));
        }
        if (id.equals("clock")) {
            if ("Minecraft".equals(HudConfig.str(mod,"source","Real time"))) {
                long seconds = client.world == null ? 43200 : Math.floorMod(client.world.getTimeOfDay() + 6000,24000) * 86400 / 24000;
                boolean twelve = "12-hour".equals(HudConfig.str(mod,"format","24-hour")), showSeconds = HudConfig.bool(mod,"seconds",false);
                return List.of(LocalTime.ofSecondOfDay(seconds).format(showSeconds ? twelve ? H12S : H24S : twelve ? H12 : H24));
            }
            boolean twelve = "12-hour".equals(HudConfig.str(mod,"format","24-hour")), seconds = HudConfig.bool(mod,"seconds",false);
            return List.of(LocalTime.now().format(seconds ? twelve ? H12S : H24S : twelve ? H12 : H24));
        }
        if (id.equals("compass")) {
            double angle = heading(client);
            String[] points = {"N","NE","E","SE","S","SW","W","NW"};
            return List.of(points[(int)Math.round(angle / 45) % 8] + (HudConfig.bool(mod,"degrees",true) ? "  " + Math.round(angle) % 360 + "°" : ""));
        }
        ItemStack stack = held(client,mod,preview);
        if (stack.isEmpty()) return List.of();
        List<String> lines = new ArrayList<>();
        lines.add(stack.getName().getString() + (HudConfig.bool(mod,"count",true) ? " x" + stack.getCount() : ""));
        if (HudConfig.bool(mod,"durability",true) && stack.isDamageable()) lines.add("Durability: " + Math.max(0,stack.getMaxDamage()-stack.getDamage()) + "/" + stack.getMaxDamage());
        if (HudConfig.bool(mod,"enchantments",true)) for (var entry : stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).getEnchantmentEntries()) {
            if (lines.size() >= 10) break;
            lines.add(Enchantment.getName(entry.getKey(),entry.getIntValue()).getString());
        }
        return lines.stream().map(line -> {
            String shortLine = client.textRenderer.trimToWidth(line,200);
            return shortLine.equals(line) ? line : client.textRenderer.trimToWidth(line,188) + "...";
        }).toList();
    }
}
