package net.fabricmc.loader.api;

import java.nio.file.Path;

/** Test-only config directory. Never packaged in the mod. */
public interface FabricLoader {
    static FabricLoader getInstance() { return new FabricLoader() {}; }
    default Path getConfigDir() {
        String path = System.getProperty("bat.test.config");
        if (path == null) throw new IllegalStateException("Test config directory required");
        return Path.of(path);
    }
    default Path getGameDir() {
        String path = System.getProperty("bat.test.game");
        if (path == null) path = Path.of(System.getProperty("bat.test.config")).getParent().toString();
        return Path.of(path);
    }
    default boolean isModLoaded(String id) {return false;}
    default boolean isDevelopmentEnvironment() {return true;}
}
