package com.batclient.platform.forge;

import com.batclient.platform.PlatformEnvironment;
import java.nio.file.Path;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;

/** Modern Forge environment adapter. Not a complete game/rendering port or mod entrypoint. */
public final class ForgeEnvironment implements PlatformEnvironment {
    @Override public String loaderId() { return "forge"; }
    @Override public Path getGameDir() { return FMLPaths.GAMEDIR.get(); }
    @Override public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }
    @Override public boolean isModLoaded(String id) { return ModList.get().isLoaded(id); }
    @Override public boolean isDevelopmentEnvironment() { return !FMLLoader.isProduction(); }
}
