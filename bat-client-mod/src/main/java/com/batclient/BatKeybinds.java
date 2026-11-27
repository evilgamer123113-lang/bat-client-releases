package com.batclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.batclient.platform.ClientHookServices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import java.util.HashMap;
import java.util.Map;

/** Physical keyboard/mouse polling keeps conflicting Minecraft bindings usable. */
public final class BatKeybinds {
    private static final Map<String,Boolean> previous = new HashMap<>();
    private static final Map<String,KeyBinding> controls = new HashMap<>();
    private BatKeybinds() { }
    public static void register(String action, KeyBinding binding) { controls.put(action,binding); }
    public static String key(String action) {
        JsonObject cfg = HudConfig.snapshot();
        if (action.equals("zoom") || action.equals("perspective")) {
            String value = HudConfig.str(HudConfig.module(cfg,action),"key","Minecraft Controls");
            if (!value.equals("Minecraft Controls")) return value.startsWith("key.") ? value : "key.keyboard." + value.toLowerCase(java.util.Locale.ROOT);
        } else if (cfg.has("bindings") && cfg.get("bindings").isJsonObject() && cfg.getAsJsonObject("bindings").has(action)) {
            return cfg.getAsJsonObject("bindings").get(action).getAsString();
        }
        return controls.containsKey(action) ? ClientHookServices.get().getBoundKey(controls.get(action)).getTranslationKey() : "key.keyboard.unknown";
    }
    public static boolean held(MinecraftClient client, String action) {
        if (!client.isWindowFocused()) return false;
        try {
            InputUtil.Key key = InputUtil.fromTranslationKey(key(action));
            if (key.getCode() < 0) return false;
            return key.getCategory() == InputUtil.Type.MOUSE
                    ? GLFW.glfwGetMouseButton(client.getWindow().getHandle(), key.getCode()) == GLFW.GLFW_PRESS
                    : key.getCategory() == InputUtil.Type.KEYSYM && InputUtil.isKeyPressed(client.getWindow().getHandle(), key.getCode());
        } catch (RuntimeException e) { return false; }
    }
    public static boolean pressed(MinecraftClient client, String action) {
        boolean down = held(client,action), was = previous.getOrDefault(action,false);
        previous.put(action,down); return down && !was;
    }
    public static void tick(MinecraftClient client) {
        JsonObject cfg = HudConfig.snapshot();
        if (!cfg.has("bindings") || !cfg.get("bindings").isJsonObject()) return;
        // Copy: toggling writes a new config snapshot.
        for (String action : cfg.getAsJsonObject("bindings").keySet().toArray(String[]::new)) {
            if (action.equals("chatanimations") || action.equals("chatheads") || action.equals("chatrepeats")) continue;
            if (controls.containsKey(action)) continue;
            boolean edge = pressed(client,action);
            if (!edge || client.currentScreen != null || client.player == null) continue;
            JsonObject mod = HudConfig.module(HudConfig.snapshot(),action);
            if (mod == null) continue;
            JsonObject patch = new JsonObject(); patch.addProperty("en",!HudConfig.enabled(mod));
            HudConfig.updateModule(action,patch.toString(),true);
        }
    }
    public static boolean save(String action, String key) {
        if ("chatanimations".equals(action) || "chatheads".equals(action) || "chatrepeats".equals(action)) return false;
        if (action == null || !action.matches("[a-z0-9_]{1,40}")) return false;
        if (key != null && !key.isEmpty()) {
            try { InputUtil.fromTranslationKey(key); } catch (RuntimeException e) { return false; }
        }
        previous.put(action,true); // Capturing the menu key must not immediately close the menu.
        if (action.equals("zoom") || action.equals("perspective")) {
            JsonObject patch = new JsonObject(), settings = new JsonObject();
            settings.addProperty("key",key == null || key.isEmpty() ? "Minecraft Controls" : key);
            patch.add("set",settings); return HudConfig.updateModule(action,patch.toString(),true);
        }
        return HudConfig.updateBinding(action,key);
    }
    public static void suppressHeld(MinecraftClient client) {
        for (String action : controls.keySet()) previous.put(action,held(client,action));
    }
    public static String describe(MinecraftClient client) {
        JsonObject result = new JsonObject(), bindings = new JsonObject();
        for (String action : controls.keySet()) bindings.addProperty(action,key(action));
        JsonObject cfg = HudConfig.snapshot();
        if (cfg.has("bindings") && cfg.get("bindings").isJsonObject())
            for (String action : cfg.getAsJsonObject("bindings").keySet()) bindings.addProperty(action,key(action));
        result.add("bindings",bindings);
        JsonArray minecraft = new JsonArray();
        for (KeyBinding binding : client.options.allKeys) {
            if (controls.containsValue(binding)) continue;
            JsonObject item = new JsonObject();
            item.addProperty("name",net.minecraft.text.Text.translatable(binding.getTranslationKey()).getString());
            item.addProperty("key",ClientHookServices.get().getBoundKey(binding).getTranslationKey()); minecraft.add(item);
        }
        result.add("controls",minecraft); return result.toString();
    }
}
