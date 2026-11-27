package com.batclient;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.batclient.platform.Platforms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

/** World folder names are stable even when the display name changes. */
public final class WorldFavorites {
    public interface Entry { String bat$worldFolder(); }
    private static final Path FILE = Platforms.get().getConfigDir().resolve("batclient-world-favorites.json");
    private static final Gson GSON = new Gson();
    private static Set<String> favorites;

    private static void load() {
        if (favorites != null) return;
        favorites = new HashSet<>();
        if (!Files.exists(FILE)) return;
        try {
            Set<String> saved = GSON.fromJson(Files.readString(FILE), new TypeToken<Set<String>>() {}.getType());
            if (saved != null) favorites.addAll(saved);
        } catch (Exception e) {
            BatClientMod.LOGGER.warn("Could not load world favorites: {}", e.toString());
        }
    }

    public static synchronized boolean contains(String folder) {
        load();
        return favorites.contains(folder);
    }

    public static synchronized boolean toggle(String folder) {
        load();
        Set<String> next = new HashSet<>(favorites);
        if (!next.remove(folder)) next.add(folder);
        try {
            Files.createDirectories(FILE.getParent());
            Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(next), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            favorites = next;
            return true;
        } catch (Exception e) {
            BatClientMod.LOGGER.warn("Could not save world favorite: {}", e.toString());
            return false;
        }
    }
}
