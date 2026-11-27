package com.batclient.platform.forge;

import com.batclient.BatClientMod;
import com.batclient.platform.ClientHookServices;
import net.minecraft.client.MinecraftClient;

/** Test fixture in an internal project only; never included in the Fabric release. */
final class ForgeFullSmoke implements java.util.function.Consumer<MinecraftClient> {
    private int ticks,worldFrames;
    private boolean loading,done;
    private boolean rawMouse;
    private int frameLimit;
    private long began=System.nanoTime();
    static void install(){
        if(Boolean.getBoolean("batclient.compatAgent")){
            try{Class.forName("com.batclient.cosmetics.AgentMain",false,ClassLoader.getSystemClassLoader());}
            catch(ClassNotFoundException error){throw new AssertionError("Actual cosmetics premain absent",error);}
            if(java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().anyMatch(gc->gc.getName().contains("G1")))
                throw new AssertionError("Explicit custom SerialGC was replaced");
            System.out.println("BAT_FORGE_AGENT_CUSTOM_JVM_OK: actual premain and selected SerialGC");
        }
        if(Boolean.getBoolean("batclient.protectedSmoke")){
            String source=BatClientMod.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
            if(!source.contains("protected-runs"))throw new AssertionError("Readable classes masked protected Forge JAR: "+source);
            for(Class<?> dependency:new Class<?>[]{io.github.nilsen84.ultralight.Ultralight.class,kotlin.jvm.internal.Intrinsics.class}){
                String location=dependency.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
                if(location.contains("modules-2"))throw new AssertionError("Loose development dependency masked nested Forge package: "+location);
            }
            System.out.println("BAT_FORGE_PROTECTED_NESTED_RUNTIME_OK: obfuscated client and real JarJar dependencies");
        }
        ForgeFullSmoke smoke=new ForgeFullSmoke();
        smoke.rawMouse=MinecraftClient.getInstance().options.getRawMouseInput().getValue();
        smoke.frameLimit=MinecraftClient.getInstance().options.getMaxFps().getValue();
        ClientHookServices.get().afterEntities(frame->smoke.worldFrames++);
        ClientHookServices.get().endTick(smoke);
        ClientHookServices.get().stopping(client->{
            var screen=BatClientMod.cachedOverlay();
            if(screen==null||screen.window()==null)return;
            var window=screen.window();
            if(!window.isClosed())throw new AssertionError("Shutdown did not close cached web window");
            window.onScroll(0,1); // Late input must not call a released native view.
            try{window.view();throw new AssertionError("Released native pointer exposed");}
            catch(IllegalStateException expected){System.out.println("BAT_FORGE_INTERNAL_CLOSED_VIEW_GUARD_OK");}
        });
    }
    @Override public void accept(MinecraftClient client){
        if(done)return;
        try{
            if(System.nanoTime()-began>180_000_000_000L)throw new AssertionError("Internal Forge world/menu timeout");
            if(!loading){
                if(client.getOverlay()!=null||client.currentScreen==null)return;
                if(client.currentScreen.getClass().getSimpleName().contains("AccessibilityOnboarding")){
                    client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());ticks=0;return;
                }
                if(++ticks<60)return;
                if(BatClientMod.getActiveScreen()!=client.currentScreen||com.batclient.web.BatClientShaders.bgraPositionTextureShader==null)
                    throw new AssertionError("Bat Forge web menu/shader missing");
                System.out.println("BAT_FORGE_FULL_INTERNAL_MENU_OK: real client sources and encrypted web resources");
                client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(4);
                loading=true;ticks=0;began=System.nanoTime();
                var info=new net.minecraft.world.level.LevelInfo("Bat Forge Full Internal Test",net.minecraft.world.GameMode.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.GameRules(),net.minecraft.resource.DataConfiguration.SAFE_MODE);
                client.createIntegratedServerLoader().createAndStart("BatForgeFull-"+System.currentTimeMillis(),info,new net.minecraft.world.gen.GeneratorOptions(12345L,false,false),registry->registry.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).get(net.minecraft.world.gen.WorldPresets.FLAT).createDimensionsRegistryHolder(),client.currentScreen);
                return;
            }
            if(client.world==null||client.player==null)return;
            if(client.currentScreen!=null&&ticks<90)client.setScreen(null);
            if(++ticks==90)BatClientMod.openWaypoints(client);
            if(ticks<120)return;
            if(worldFrames<10||BatClientMod.cachedOverlay()==null||client.currentScreen==null)throw new AssertionError("Forge world/HUD/waypoint menu missing");
            if(client.options.getRawMouseInput().getValue()!=rawMouse||client.options.getMaxFps().getValue()!=frameLimit)throw new AssertionError("Saved input/FPS preference was overridden");
            finish(client,"BAT_FORGE_FULL_INTERNAL_WORLD_OK: "+worldFrames+" frames; world, HUD and explicit waypoint menu; NOT release certification");
        }catch(Throwable error){error.printStackTrace();finish(client,"FAILED: "+error);}
    }
    private void finish(MinecraftClient client,String result){
        done=true;System.out.println(result);
        try{java.nio.file.Files.writeString(client.runDirectory.toPath().resolve("result.txt"),result);}catch(Exception error){error.printStackTrace();}
        client.scheduleStop();
    }
}
