import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import java.nio.file.*;
import java.util.*;

/** Identical disposable normal-terrain flight fixture for old/new protected JARs. */
public final class FlightRuntimeSmoke implements ClientModInitializer {
    private boolean started,done,measuring;
    private int wait,ticks,loadedStart;
    private long launch=System.nanoTime(),created,previous;
    private final List<Double> frames=new ArrayList<>();
    @Override public void onInitializeClient(){
        WorldRenderEvents.END.register(context->{if(measuring&&!done){long now=System.nanoTime();if(previous!=0&&frames.size()<30000)frames.add((now-previous)/1_000_000d);previous=now;}});
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(done)return;
            try{
                if(!started){
                    if(System.nanoTime()-launch>180_000_000_000L)throw new AssertionError("startup timed out");
                    if(client.currentScreen==null||client.getOverlay()!=null||++wait<30)return;
                    if(!FabricLoader.getInstance().getGameDir().getFileName().toString().startsWith("flight-native-"))throw new AssertionError("not isolated");
                    client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(8);
                    client.options.getEnableVsync().setValue(false);client.options.getMaxFps().setValue(120);
                    started=true;created=System.nanoTime();
                    var info=new net.minecraft.world.level.LevelInfo("Bat Flight Test",net.minecraft.world.GameMode.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.GameRules(),net.minecraft.resource.DataConfiguration.SAFE_MODE);
                    client.createIntegratedServerLoader().createAndStart("BatFlight",info,new net.minecraft.world.gen.GeneratorOptions(12345L,false,false),registry->registry.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).get(net.minecraft.world.gen.WorldPresets.DEFAULT).createDimensionsRegistryHolder(),client.currentScreen);return;
                }
                if(client.player==null||client.world==null){if(System.nanoTime()-created>180_000_000_000L)throw new AssertionError("world timed out");return;}
                if(client.currentScreen!=null)client.setScreen(null);
                ticks++;
                if(ticks==1){client.player.getAbilities().allowFlying=true;client.player.getAbilities().flying=true;client.player.sendAbilitiesUpdate();client.player.setPosition(client.player.getX(),150,client.player.getZ());client.player.setYaw(-90);client.player.setPitch(20);}
                if(ticks>=80){
                    if(!measuring){measuring=true;loadedStart=client.world.getChunkManager().getLoadedChunkCount();}
                    client.player.setVelocity(.85,0,0);
                }
                if(ticks>=280){
                    if(frames.size()<10)throw new AssertionError("not enough frames");
                    double[] sorted=frames.stream().mapToDouble(Double::doubleValue).sorted().toArray();
                    int workers=net.minecraft.util.Util.getMainWorkerExecutor() instanceof java.util.concurrent.ForkJoinPool pool?pool.getParallelism():-1;
                    String result=String.format(Locale.ROOT,"FLIGHT_NATIVE_OK: variant=%s frames=%d medianMs=%.3f p95Ms=%.3f p99Ms=%.3f maxMs=%.3f workers=%d loadedStart=%d loadedEnd=%d x=%.1f z=%.1f; same seed, 8 chunks, cap120, VSync off, 10s eastward flight; single diagnostic pair only",System.getProperty("bat.flight.variant"),sorted.length,sorted[sorted.length/2],sorted[(int)(sorted.length*.95)],sorted[(int)(sorted.length*.99)],sorted[sorted.length-1],workers,loadedStart,client.world.getChunkManager().getLoadedChunkCount(),client.player.getX(),client.player.getZ());
                    finish(client,result);
                }
            }catch(Throwable error){error.printStackTrace();finish(client,"FAILED: "+error);}
        });
    }
    private void finish(MinecraftClient client,String result){done=true;try{Files.writeString(FabricLoader.getInstance().getGameDir().resolve("result.txt"),result);}catch(Exception error){error.printStackTrace();}System.out.println(result);client.scheduleStop();}
}
