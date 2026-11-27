package com.batclient.platform.forge;

import com.batclient.platform.ClientHookServices;
import net.minecraft.client.MinecraftClient;

/** Internal project only: real native Forge world/menu fixture, never a release entrypoint. */
public final class ForgeFullSmoke {
    private ForgeFullSmoke() {}

    public static void install(){
        if (!Boolean.getBoolean("batclient.internalSmoke")) return;
        ClientHookServices.get().started(client -> {
            System.out.println("BAT_FORGE_189_STARTED");
        });
    }
}
