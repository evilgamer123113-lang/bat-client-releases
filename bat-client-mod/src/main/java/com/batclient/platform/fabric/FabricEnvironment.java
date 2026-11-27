package com.batclient.platform.fabric;

import com.batclient.platform.PlatformEnvironment;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Uses the same Loader API for official Fabric and Legacy Fabric. */
public final class FabricEnvironment implements PlatformEnvironment {
    @Override public String loaderId() { return "fabric"; }
    @Override public Path getGameDir() { return FabricLoader.getInstance().getGameDir(); }
    @Override public Path getConfigDir() { return FabricLoader.getInstance().getConfigDir(); }
    @Override public boolean isModLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    @Override public boolean isDevelopmentEnvironment() { return FabricLoader.getInstance().isDevelopmentEnvironment(); }
}
