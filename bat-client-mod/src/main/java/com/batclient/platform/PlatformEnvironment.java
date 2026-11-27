package com.batclient.platform;

import java.nio.file.Path;

/** Loader services only. Game/render/input/network adapters remain version-specific. */
public interface PlatformEnvironment {
    String loaderId();
    Path getGameDir();
    Path getConfigDir();
    boolean isModLoaded(String id);
    boolean isDevelopmentEnvironment();
}
