package com.batclient.platform.forge;

import com.batclient.platform.PlatformEnvironment;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Loader;

public final class ForgeEnvironment implements PlatformEnvironment {
    @Override
    public String loaderId() {
        return "forge";
    }

    @Override
    public Path getGameDir() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.mcDataDir != null ? mc.mcDataDir.toPath() : new java.io.File(".").toPath();
    }

    @Override
    public Path getConfigDir() {
        return getGameDir().resolve("config");
    }

    @Override
    public boolean isModLoaded(String id) {
        return Loader.isModLoaded(id);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        Object flag = Launch.blackboard != null ? Launch.blackboard.get("fml.deobfuscatedEnvironment") : null;
        return Boolean.TRUE.equals(flag);
    }
}
