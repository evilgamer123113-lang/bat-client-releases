package com.batclient.platform.forge;

import com.batclient.BatClientMod;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Internal full-source integration. No public compatibility claim. */
@Mod("batclient")
public final class ForgePortEntrypoint {
    public ForgePortEntrypoint(FMLJavaModLoadingContext context){
        var bus=context.getModBusGroup();
        ForgeClientResources.bootstrap(bus);
        // Resource/player renderer hooks must be installed before the first reload.
        BatClientMod.initializeResources();
        FMLClientSetupEvent.getBus(bus).addListener(event->event.enqueueWork(()->{
            new BatClientMod().onInitializeClient();
            if(Boolean.getBoolean("batclient.internalSmoke"))ForgeFullSmoke.install();
        }));
    }
}
