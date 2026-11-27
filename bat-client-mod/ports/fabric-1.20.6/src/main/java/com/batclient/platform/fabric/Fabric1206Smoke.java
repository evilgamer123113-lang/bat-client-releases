package com.batclient.platform.fabric;

import com.batclient.BatClientMod;
import com.batclient.platform.ClientHookServices;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;

/** Internal project only: real native world/menu fixture, never a release entrypoint. */
public final class Fabric1206Smoke implements ClientModInitializer,java.util.function.Consumer<MinecraftClient> {
    private int ticks,worldFrames;
    private boolean loading,done,rawMouse;
    private int frameLimit;
    private long began;
    @Override public void onInitializeClient(){
        if(!Boolean.getBoolean("batclient.internalSmoke"))return;
        ClientLifecycleEvents.CLIENT_STARTED.register(client->{
            if(Boolean.getBoolean("batclient.productionSmoke")){
                var loader=net.fabricmc.loader.api.FabricLoader.getInstance();
                if(loader.isDevelopmentEnvironment()||!loader.getMappingResolver().getCurrentRuntimeNamespace().equals("intermediary"))
                    throw new AssertionError("Expected actual Fabric production intermediary runtime");
                System.out.println("BAT_FABRIC_1206_PRODUCTION_NAMESPACE_OK: intermediary, development=false");
            }
            if(Boolean.getBoolean("batclient.compatAgent")){
                try{Class.forName("com.batclient.cosmetics.AgentMain",false,ClassLoader.getSystemClassLoader());}
                catch(ClassNotFoundException error){throw new AssertionError("Actual cosmetics premain absent",error);}
                if(java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().anyMatch(gc->gc.getName().contains("G1")))
                    throw new AssertionError("Explicit custom SerialGC was replaced");
                System.out.println("BAT_FABRIC_1206_AGENT_CUSTOM_JVM_OK: actual premain and selected SerialGC");
            }
            if(Boolean.getBoolean("batclient.protectedSmoke")){
                String source=BatClientMod.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
                if(!source.contains("protected-runs"))throw new AssertionError("Readable sources masked protected Fabric port: "+source);
                for(Class<?> dependency:new Class<?>[]{io.github.nilsen84.ultralight.Ultralight.class,kotlin.jvm.internal.Intrinsics.class}){
                    String location=dependency.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
                    if(location.contains("modules-2"))throw new AssertionError("Loose runtime masked nested Fabric dependency: "+location);
                }
                System.out.println("BAT_FABRIC_1206_PROTECTED_NESTED_RUNTIME_OK");
            }
            began=System.nanoTime();
            rawMouse=client.options.getRawMouseInput().getValue();
            frameLimit=client.options.getMaxFps().getValue();
            ClientHookServices.get().afterEntities(frame->worldFrames++);
            ClientHookServices.get().endTick(this);
        });
    }
    @Override public void accept(MinecraftClient client){
        if(done)return;
        try{
            if(System.nanoTime()-began>180_000_000_000L)throw new AssertionError("Internal Fabric world/menu timeout");
            if(!loading){
                if(client.getOverlay()!=null||client.currentScreen==null)return;
                if(client.currentScreen.getClass().getSimpleName().contains("AccessibilityOnboarding")){
                    client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());ticks=0;return;
                }
                if(++ticks<60)return;
                if(BatClientMod.getActiveScreen()!=client.currentScreen||com.batclient.web.BatClientShaders.bgraPositionTextureShader==null)
                    throw new AssertionError("Bat Fabric menu/shader missing");
                System.out.println("BAT_FABRIC_1206_NATIVE_MENU_OK");
                client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(4);
                loading=true;ticks=0;began=System.nanoTime();
                var info=new net.minecraft.world.level.LevelInfo("Bat Fabric Internal Test",net.minecraft.world.GameMode.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.GameRules(),net.minecraft.resource.DataConfiguration.SAFE_MODE);
                client.createIntegratedServerLoader().createAndStart("BatFabricInternal-"+System.currentTimeMillis(),info,new net.minecraft.world.gen.GeneratorOptions(12345L,false,false),registry->registry.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).get(net.minecraft.world.gen.WorldPresets.FLAT).createDimensionsRegistryHolder(),client.currentScreen);
                return;
            }
            if(client.world==null||client.player==null)return;
            if(client.currentScreen!=null&&ticks<90)client.setScreen(null);
            if(++ticks==90)BatClientMod.openWaypoints(client);
            if(ticks<120)return;
            if(worldFrames<10||BatClientMod.cachedOverlay()==null||client.currentScreen==null)throw new AssertionError("Fabric world/HUD/waypoint menu missing");
            if(client.options.getRawMouseInput().getValue()!=rawMouse||client.options.getMaxFps().getValue()!=frameLimit)throw new AssertionError("Saved input/FPS preference changed");
            finish(client,"BAT_FABRIC_1206_INTERNAL_WORLD_OK: "+worldFrames+" frames; world/HUD/waypoint menu; NOT release certification");
        }catch(Throwable error){error.printStackTrace();finish(client,"FAILED: "+error);}
    }
    private void finish(MinecraftClient client,String result){
        done=true;System.out.println(result);
        try{java.nio.file.Files.writeString(client.runDirectory.toPath().resolve("result.txt"),result);}catch(Exception error){error.printStackTrace();}
        client.scheduleStop();
    }
}
