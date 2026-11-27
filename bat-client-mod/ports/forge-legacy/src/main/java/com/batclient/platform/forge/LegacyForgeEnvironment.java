package com.batclient.platform.forge;

import com.batclient.platform.PlatformEnvironment;
import java.nio.file.Path;
import net.minecraftforge.fml.common.Loader;
import net.minecraft.launchwrapper.Launch;

/** Forge 1.8/1.8.9 services. Uses the actual launched game directory, not user.dir. */
public final class LegacyForgeEnvironment implements PlatformEnvironment {
    @Override public String loaderId() { return "forge"; }
    @Override public Path getGameDir() {
        if (Launch.minecraftHome == null) throw new IllegalStateException("Forge game directory is not initialized");
        return Launch.minecraftHome.toPath();
    }
    @Override public Path getConfigDir() { return Loader.instance().getConfigDir().toPath(); }
    @Override public boolean isModLoaded(String id) { return Loader.isModLoaded(id); }
    @Override public boolean isDevelopmentEnvironment() {
        return Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment"));
    }
}
