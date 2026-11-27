package com.batclient.platform.forge;

import com.batclient.BatClientMod;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = "batclient", name = "Bat Client", version = "0.1.0", clientSideOnly = true)
public final class ForgePortEntrypoint {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        BatClientMod.initializeResources();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        new BatClientMod().onInitializeClient();
        if (Boolean.getBoolean("batclient.internalSmoke")) {
            ForgeFullSmoke.install();
        }
    }
}
