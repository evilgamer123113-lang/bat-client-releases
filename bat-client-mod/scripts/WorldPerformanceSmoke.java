import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import java.util.*;
import java.nio.file.*;

/** Isolated disposable flat-world diagnostic; never opens run/saves or changes user's options. */
public final class WorldPerformanceSmoke {
    private boolean started,done;
    private int waiting,ticks;
    private long previous,created;
    private final List<Double> frames=new ArrayList<>();
    public void start(){
        WorldRenderEvents.END.register(context->{if(started&&!done&&MinecraftClient.getInstance().world!=null){long now=System.nanoTime();if(previous>0&&frames.size()<20000)frames.add((now-previous)/1_000_000d);previous=now;}});
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(done)return;
            if(!started){if(c.currentScreen==null||c.getOverlay()!=null||++waiting<40)return;started=true;created=System.nanoTime();
                var info=new net.minecraft.world.level.LevelInfo("Bat Performance Test",net.minecraft.world.GameMode.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.GameRules(),net.minecraft.resource.DataConfiguration.SAFE_MODE);
                c.options.getViewDistance().setValue(8);c.options.pauseOnLostFocus=false;
                c.createIntegratedServerLoader().createAndStart("BatPerf-"+System.currentTimeMillis(),info,new net.minecraft.world.gen.GeneratorOptions(12345L,false,false),registry->registry.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).get(net.minecraft.world.gen.WorldPresets.FLAT).createDimensionsRegistryHolder(),c.currentScreen);return;
            }
            if(c.player==null||c.world==null){if(System.nanoTime()-created>180_000_000_000L)finish(c,"FAILED: world did not load within 180s");return;}
            if(c.currentScreen!=null)c.setScreen(null);
            ticks++;
            if(ticks==100||ticks==220){var server=c.getServer();var uuid=c.player.getUuid();int target=ticks==100?256:512;server.execute(()->{var player=server.getPlayerManager().getPlayer(uuid);if(player!=null)player.requestTeleport(target,80,target);});}
            if(ticks>=360){var sorted=frames.stream().mapToDouble(Double::doubleValue).sorted().toArray();if(sorted.length<10){finish(c,"FAILED: insufficient world frames");return;}
                finish(c,String.format(Locale.ROOT,"WORLD_CHUNK_DIAGNOSTIC: %d frames, median %.2fms, p95 %.2fms, max %.2fms; 8-chunk view, new flat world, two 256-block teleports. Hardware-specific diagnostic, no comparison baseline.",sorted.length,sorted[sorted.length/2],sorted[(int)(sorted.length*.95)],sorted[sorted.length-1]));}
        });
    }
    private void finish(MinecraftClient c,String result){done=true;try{Files.writeString(Path.of(System.getProperty("bat.chat.smoke.out")).resolveSibling("world-performance.txt"),result);}catch(Exception e){e.printStackTrace();}System.out.println(result);c.scheduleStop();}
}
