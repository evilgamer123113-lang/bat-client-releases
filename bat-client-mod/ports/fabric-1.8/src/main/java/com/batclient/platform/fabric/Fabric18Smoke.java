package com.batclient.platform.fabric;

import com.batclient.BatClientMod;
import com.batclient.platform.ClientHookServices;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;

/** Internal project only: real native world/menu fixture, never a release entrypoint. */
public final class Fabric18Smoke implements ClientModInitializer, java.util.function.Consumer<MinecraftClient> {
    private int ticks, worldFrames;
    private boolean loading, done;
    private int frameLimit;
    private long began;

    @Override
    public void onInitializeClient(){
        if (!Boolean.getBoolean("batclient.internalSmoke")) return;
        ClientHookServices.get().started(client -> {
            if (Boolean.getBoolean("batclient.productionSmoke")) {
                net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();
                if (loader.isDevelopmentEnvironment() || !loader.getMappingResolver().getCurrentRuntimeNamespace().equals("intermediary"))
                    throw new AssertionError("Expected actual Fabric production intermediary runtime");
                System.out.println("BAT_FABRIC_189_PRODUCTION_NAMESPACE_OK: intermediary, development=false");
            }
            if (Boolean.getBoolean("batclient.compatAgent")) {
                try {
                    Class.forName("com.batclient.cosmetics.AgentMain", false, ClassLoader.getSystemClassLoader());
                } catch (ClassNotFoundException error) {
                    throw new AssertionError("Actual cosmetics premain absent", error);
                }
                if (java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().anyMatch(gc -> gc.getName().contains("G1")))
                    throw new AssertionError("Explicit custom SerialGC was replaced");
                System.out.println("BAT_FABRIC_189_AGENT_CUSTOM_JVM_OK: actual premain and selected SerialGC");
            }
            began = System.nanoTime();
            frameLimit = client.options.maxFramerate;
            ClientHookServices.get().afterEntities(frame -> worldFrames++);
            ClientHookServices.get().endTick(this);
        });
    }

    @Override
    public void accept(MinecraftClient client) {
        if (done) return;
        try {
            if (System.nanoTime() - began > 180_000_000_000L) throw new AssertionError("Internal Fabric world/menu timeout");
            if (!loading) {
                if (client.currentScreen == null) return;
                if (++ticks < 60) return;
                if (BatClientMod.getActiveScreen() != client.currentScreen)
                    throw new AssertionError("Bat Fabric menu missing: screen=" + client.currentScreen.getClass().getName());
                System.out.println("BAT_FABRIC_189_NATIVE_MENU_OK");
                client.options.pauseOnLostFocus = false;
                client.options.viewDistance = 4;
                loading = true;
                ticks = 0;
                began = System.nanoTime();
                net.minecraft.world.level.LevelInfo info = new net.minecraft.world.level.LevelInfo(
                    0L, net.minecraft.world.level.LevelInfo.GameMode.CREATIVE, true, false,
                    net.minecraft.world.level.LevelGeneratorType.FLAT);
                client.startIntegratedServer("BatFabricInternal-" + System.currentTimeMillis(), "Bat Fabric Internal Test", info);
                return;
            }
            if (client.world == null || client.player == null) return;
            if (client.currentScreen != null && ticks < 90) client.setScreen(null);
            if (++ticks == 90) BatClientMod.openWaypoints(client);
            if (ticks < 120) return;
            if (worldFrames < 10 || BatClientMod.cachedOverlay() == null || client.currentScreen == null)
                throw new AssertionError("Fabric world/HUD/waypoint menu missing");
            if (client.options.maxFramerate != frameLimit)
                throw new AssertionError("Saved FPS preference changed");
            finish(client, "BAT_FABRIC_189_INTERNAL_WORLD_OK: " + worldFrames + " frames; world/HUD/waypoint menu; NOT release certification");
        } catch (Throwable error) {
            error.printStackTrace();
            finish(client, "FAILED: " + error);
        }
    }

    private void finish(MinecraftClient client, String result) {
        done = true;
        try {
            com.batclient.platform.java8.J8.writeString(client.runDirectory.toPath().resolve("result.txt"), result);
        } catch (Exception failure) {
            failure.printStackTrace();
        }
        client.scheduleStop();
    }
}

