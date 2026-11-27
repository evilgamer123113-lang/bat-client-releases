package com.batclient.platform.fabric;

import com.batclient.BatClientMod;
import net.fabricmc.api.ClientModInitializer;

public final class BatFabricEntrypoint implements ClientModInitializer {
    @Override public void onInitializeClient(){new BatClientMod().onInitializeClient();}
}
