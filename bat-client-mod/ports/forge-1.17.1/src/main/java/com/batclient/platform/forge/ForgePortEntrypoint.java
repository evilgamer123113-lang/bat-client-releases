package com.batclient.platform.forge;

import com.batclient.BatClientMod;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Internal full-source integration. No public compatibility claim. */
@Mod("batclient")
public final class ForgePortEntrypoint {
    public ForgePortEntrypoint(){
        var bus=FMLJavaModLoadingContext.get().getModEventBus();
        ForgeClientResources.bootstrap(bus);
        // Resource/player renderer hooks must be installed before the first reload.
        BatClientMod.initializeResources();
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,FMLClientSetupEvent.class,event->event.enqueueWork(()->{
            net.minecraftforge.client.MinecraftForgeClient.registerTooltipComponentFactory(com.batclient.ItemPreviews.Contents.class,ForgeClientHooks::createTooltip);
            net.minecraftforge.client.MinecraftForgeClient.registerTooltipComponentFactory(com.batclient.ItemPreviews.Comparison.class,ForgeClientHooks::createTooltip);
            new BatClientMod().onInitializeClient();
            if(Boolean.getBoolean("batclient.internalSmoke"))ForgeFullSmoke.install();
        }));
    }
}
