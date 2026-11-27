package com.batclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.batclient.platform.Platforms;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** Separate metadata; never rewrites Minecraft's servers.dat or skin/cape settings. */
public final class ServerPreferences {
    private static final Path FILE = Platforms.get().getConfigDir().resolve("batclient-servers.json");
    private static JsonObject data;
    public static String key(String address) { return address.trim().toLowerCase(Locale.ROOT); }
    private static void load() {
        if (data != null) return;
        try { data = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject(); }
        catch (Exception ignored) { data = new JsonObject(); }
    }
    public static synchronized JsonObject get(String address) {
        load(); var item = data.get(key(address));
        return item != null && item.isJsonObject() ? item.getAsJsonObject().deepCopy() : new JsonObject();
    }
    public static synchronized boolean flag(String address, String flag) {
        load();
        try {
            var item = data.get(key(address));
            return item != null && item.isJsonObject() && item.getAsJsonObject().has(flag)
                    && item.getAsJsonObject().get(flag).getAsBoolean();
        } catch (RuntimeException ignored) { return false; }
    }
    public static synchronized void toggle(String address, String flag) {
        JsonObject patch = new JsonObject(); patch.addProperty(flag, !flag(address, flag)); put(address, patch);
    }
    public static synchronized void put(String address, JsonObject patch) {
        load(); JsonObject next = data.deepCopy(), item = get(address);
        patch.entrySet().forEach(field -> item.add(field.getKey(), field.getValue()));
        next.add(key(address), item);
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, next.toString(), StandardCharsets.UTF_8);
            try { Files.move(tmp, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING); }
            data = next;
        } catch (Exception e) { BatClientMod.LOGGER.warn("Server preference save failed: {}", e.toString()); }
    }
}
