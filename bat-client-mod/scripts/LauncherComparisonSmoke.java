import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import java.nio.file.*;
import java.util.*;

/** Isolated production benchmark. Never installed in the user's game directory. */
public final class LauncherComparisonSmoke implements ClientModInitializer {
    private static boolean meshExecutorActive()throws Exception{
        for(var method:net.minecraft.client.render.chunk.ChunkBuilder.class.getDeclaredMethods())for(var annotation:method.getDeclaredAnnotations()){
            if(annotation.annotationType().getName().endsWith(".MixinMerged")&&annotation.annotationType().getMethod("mixin").invoke(annotation).toString().endsWith(".ChunkMeshExecutorMixin"))return true;
        }
        return false;
    }
    private static boolean localBatchActive()throws Exception{
        for(var method:net.minecraft.server.network.ChunkDataSender.class.getDeclaredMethods())for(var annotation:method.getDeclaredAnnotations()){
            if(annotation.annotationType().getName().endsWith(".MixinMerged")&&annotation.annotationType().getMethod("mixin").invoke(annotation).toString().endsWith(".LocalChunkBatchMixin"))return true;
        }
        return false;
    }
    private final boolean backgroundDiagnostic=Boolean.getBoolean("bat.bench.backgroundDiagnostic");
    private final boolean requireFocus=!backgroundDiagnostic||Boolean.getBoolean("bat.bench.requireFocus");
    private final boolean terrainFlight=Boolean.getBoolean("bat.bench.terrainFlight");
    private long flightAt;
    private long lastChunkAt;
    private long focusedFrames,unfocusedFrames;
    private final java.lang.management.ThreadMXBean cpu=java.lang.management.ManagementFactory.getThreadMXBean();
    private long lastCpu=-1;
    private String pendingResult;
    private long routeFinishedAt;
    private long terrainSampleAt;
    private final com.google.gson.JsonArray terrainSamples=new com.google.gson.JsonArray();
    private void recordTerrain(MinecraftClient client,long now){
        if(now-terrainSampleAt<1_000_000_000L)return;
        terrainSampleAt=now;
        var sample=new com.google.gson.JsonObject();
        sample.addProperty("elapsedMs",(now-begin)/1e6);
        sample.addProperty("phase",pendingResult!=null?"settling":flightAt!=0?"flight":"warmup");
        sample.addProperty("loaded",client.world.getChunkManager().getLoadedChunkCount());
        sample.addProperty("received",deliveredChunks);
        sample.addProperty("renderComplete",client.worldRenderer.isTerrainRenderComplete());
        sample.addProperty("renderer",client.worldRenderer.getChunksDebugString());
        terrainSamples.add(sample);
    }
    private record Stall(long endEpochMillis,double wallMs,double cpuMs,boolean focused){}
    private final List<Stall> stalls=new ArrayList<>();
    private void writeStalls()throws Exception{
        StringJoiner entries=new StringJoiner(",","[","]");
        for(Stall s:stalls)entries.add(String.format(Locale.ROOT,"{\"endEpochMillis\":%d,\"wallMs\":%.3f,\"cpuMs\":%.3f,\"focused\":%s}",s.endEpochMillis(),s.wallMs(),s.cpuMs(),s.focused()));
        Files.writeString(FabricLoader.getInstance().getGameDir().resolve("frame-stalls.json"),entries.toString());
        Files.writeString(FabricLoader.getInstance().getGameDir().resolve("terrain-progress.json"),terrainSamples.toString());
    }
    private long progressAt,progressWritten;
    private double previousX,previousY;
    private int deliveredChunks, flightChunks, flightStart;
    private double measuredMillis;
    private final List<Double> stationaryFrames=new ArrayList<>();
    private boolean started,done,positioned;
    private int wait;
    private long begin,worldAt,last,measureAt,focusReadyAt;
    private final List<Double> frames=new ArrayList<>();
    @Override public void onInitializeClient() {
        begin=System.nanoTime();
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BEFORE_INIT.register((client,screen,width,height)->{
            if(!positioned||done||screen instanceof net.minecraft.client.gui.screen.DownloadingTerrainScreen||screen instanceof net.minecraft.client.gui.screen.world.LevelLoadingScreen)return;
            try{
                String trace="screen="+screen.getClass().getName()+" focused="+client.isWindowFocused()+" pauseOnLostFocus="+client.options.pauseOnLostFocus+"\n";
                for(StackTraceElement frame:Thread.currentThread().getStackTrace())trace+=frame+"\n";
                Files.writeString(FabricLoader.getInstance().getGameDir().resolve("screen-interruption-trace.txt"),trace,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            }catch(Exception ignored){}
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_LOAD.register((world,chunk)->{deliveredChunks++;lastChunkAt=System.nanoTime();});
        WorldRenderEvents.END.register(context->{
            var c=MinecraftClient.getInstance();long now=System.nanoTime();
            if(done||pendingResult!=null||measureAt==0||requireFocus&&!c.isWindowFocused()||c.currentScreen!=null){last=0;lastCpu=-1;return;}
            long cpuNow=cpu.isCurrentThreadCpuTimeSupported()?cpu.getCurrentThreadCpuTime():-1;
            if(c.isWindowFocused())focusedFrames++;else unfocusedFrames++;
            if(last!=0){double delta=(now-last)/1e6;frames.add(delta);measuredMillis+=delta;if(delta>50)stalls.add(new Stall(System.currentTimeMillis(),delta,lastCpu>=0&&cpuNow>=lastCpu?(cpuNow-lastCpu)/1e6:-1,c.isWindowFocused()));}last=now;lastCpu=cpuNow;
        });
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(done)return;
            try {
                long now=System.nanoTime();
                if(Boolean.getBoolean("bat.bench.rendererOptOut")){
                    if(c.currentScreen==null||c.getOverlay()!=null||++wait<40)return;
                    if(!FabricLoader.getInstance().isModLoaded("batclient")||FabricLoader.getInstance().isModLoaded("sodium"))throw new IllegalStateException("Renderer opt-out was not honored");
                    Files.writeString(FabricLoader.getInstance().getGameDir().resolve("renderer-choice-result.txt"),"RENDERER_OPT_OUT_NATIVE_OK: protected Bat client initialized; native menu reached; Sodium absent");
                    done=true;c.scheduleStop();return;
                }
                if(Boolean.getBoolean("bat.bench.renderChecks")){
                    if(c.currentScreen==null||c.getOverlay()!=null||++wait<40)return;
                    if(CustomRenderingNativeChecks.tick(c)){done=true;c.scheduleStop();}return;
                }
                // Cold protected startup and a fully settled warmup can consume most
                // of 240s. Keep the route's own 60s settle limit, but let it run.
                if(!Boolean.getBoolean("bat.bench.interactive")&&now-begin>360_000_000_000L)throw new IllegalStateException("benchmark timeout; no valid pass");
                if(!started){
                    if(c.currentScreen==null||c.getOverlay()!=null||++wait<40)return;
                    started=true;c.options.pauseOnLostFocus=false;
                    if(Files.isRegularFile(FabricLoader.getInstance().getGameDir().resolve("saves/Comparison/level.dat"))){
                        c.createIntegratedServerLoader().start("Comparison",()->c.scheduleStop());return;
                    }
                    var info=new net.minecraft.world.level.LevelInfo("Launcher Comparison",net.minecraft.world.GameMode.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.GameRules(),net.minecraft.resource.DataConfiguration.SAFE_MODE);
                    c.createIntegratedServerLoader().createAndStart("Comparison",info,new net.minecraft.world.gen.GeneratorOptions(12345L,false,false),registry->registry.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).get(net.minecraft.world.gen.WorldPresets.DEFAULT).createDimensionsRegistryHolder(),c.currentScreen);
                    return;
                }
                if(c.player==null||c.world==null)return;
                if(Boolean.getBoolean("bat.bench.interactive")&&positioned)return;
                recordTerrain(c,now);
                if(measureAt==0&&requireFocus&&!c.isWindowFocused())focusReadyAt=0;
                if(measureAt!=0&&requireFocus&&!c.isWindowFocused())throw new IllegalStateException("Focus lost during foreground benchmark; result invalid");
                if(c.currentScreen!=null){
                    boolean bootstrap=c.currentScreen instanceof net.minecraft.client.gui.screen.DownloadingTerrainScreen || c.currentScreen instanceof net.minecraft.client.gui.screen.world.LevelLoadingScreen;
                    if(positioned&&(!bootstrap||measureAt!=0))throw new IllegalStateException("Benchmark interrupted by "+c.currentScreen.getClass().getName()+"; result invalid");
                    c.setScreen(null);
                }
                if(!positioned){
                    int testPriority=Integer.getInteger("bat.bench.renderPriority",0);
                    if(testPriority!=0){if(testPriority!=7)throw new IllegalArgumentException("private test only permits priority7");Thread.currentThread().setPriority(testPriority);}
                    positioned=true;worldAt=now;
                    var server=c.getServer();var uuid=c.player.getUuid();
                    server.execute(()->{server.getOverworld().setTimeOfDay(6000);var p=server.getPlayerManager().getPlayer(uuid);if(p!=null){p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.sendAbilitiesUpdate();int surface=server.getOverworld().getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE,0,0);p.requestTeleport(0,surface+24,0);p.setYaw(-90);p.setPitch(20);}});
                }
                // Explicit private interactive session: open the copied world once,
                // then leave movement, camera, menus and lifetime to the player.
                if(Boolean.getBoolean("bat.bench.interactive"))return;
                if(pendingResult!=null){
                    c.player.setVelocity(0,0,0);
                    if(now-routeFinishedAt>60_000_000_000L)throw new IllegalStateException("Terrain did not settle after route; throughput comparison incomplete");
                    if(c.world.getChunkManager().getLoadedChunkCount()<flightStart||!c.worldRenderer.isTerrainRenderComplete()||now-lastChunkAt<2_000_000_000L)return;
                    var settled=com.google.gson.JsonParser.parseString(pendingResult).getAsJsonObject();
                    settled.addProperty("terrainSettleMs",(now-routeFinishedAt)/1e6);
                    settled.addProperty("loadedAfterSettle",c.world.getChunkManager().getLoadedChunkCount());
                    settled.addProperty("terrainRenderCompleteAfterSettle",true);
                    writeStalls();Files.writeString(FabricLoader.getInstance().getGameDir().resolve("comparison-result.json"),settled.toString());
                    System.out.println("LAUNCHER_COMPARISON_OK "+settled);done=true;c.scheduleStop();return;
                }
                double flightX=c.player.getX();
                boolean loaded=true;int highest=c.world.getBottomY();
                if(flightAt!=0){
                    int x=net.minecraft.util.math.MathHelper.floor(c.player.getX()),z=net.minecraft.util.math.MathHelper.floor(c.player.getZ());
                    for(int ahead=0;ahead<=32;ahead+=ahead<8?1:8)for(int side=-1;side<=1;side++){
                        if(!c.world.getChunkManager().isChunkLoaded((x+ahead)>>4,(z+side)>>4)){loaded=false;continue;}
                        highest=Math.max(highest,c.world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE,x+ahead,z+side));
                    }
                    var movement=FlightRouteController.next(c.player.getY(),highest,loaded);
                    c.player.setVelocity(movement.forward(),movement.vertical(),0);
                    if(Math.abs(c.player.getX()-previousX)+Math.abs(c.player.getY()-previousY)>.05)progressAt=now;
                    previousX=c.player.getX();previousY=c.player.getY();
                    if(now-progressAt>(loaded?15:30)*1_000_000_000L)throw new IllegalStateException(loaded?"Route obstructed; benchmark invalid":"Chunk delivery stalled; benchmark incomplete");
                    if(now-progressWritten>1_000_000_000L){progressWritten=now;Files.writeString(FabricLoader.getInstance().getGameDir().resolve("route-progress.json"),"{\"x\":"+flightX+",\"y\":"+c.player.getY()+",\"highestAhead\":"+highest+",\"aheadLoaded\":"+loaded+"}");}
                }else c.player.setVelocity(0,0,0);
                c.player.setYaw(terrainFlight?-90:45);c.player.setPitch(terrainFlight?60:20);
                if(now-worldAt<25_000_000_000L)return;
                if(measureAt==0){
                    // A fixed delay alone starts runs with different amounts of terrain still pending.
                    // Require the inner view area, completed render work and a quiet delivery window.
                    int radius=Math.max(2,c.options.getViewDistance().getValue()-2);
                    int centerX=c.player.getBlockX()>>4,centerZ=c.player.getBlockZ()>>4;
                    boolean ready=true;
                    for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                        if(dx*dx+dz*dz<=radius*radius&&!c.world.getChunkManager().isChunkLoaded(centerX+dx,centerZ+dz))ready=false;
                    }
                    if(!ready||!c.worldRenderer.isTerrainRenderComplete()||now-lastChunkAt<5_000_000_000L)return;
                    Files.writeString(FabricLoader.getInstance().getGameDir().resolve("warmup-ready.json"),"{\"innerRadius\":"+radius+",\"loadedChunks\":"+c.world.getChunkManager().getLoadedChunkCount()+",\"terrainRenderComplete\":true,\"quietDeliverySeconds\":"+((now-lastChunkAt)/1e9)+"}");
                }
                if(Boolean.getBoolean("bat.bench.prepareWorld")){
                    boolean hook=localBatchActive();
                    boolean mesh=meshExecutorActive();
                    if(Boolean.getBoolean("bat.bench.expectLocalBatch")&&!hook)throw new IllegalStateException("Local batch hook was not applied to actual integrated server");
                    if(Boolean.getBoolean("bat.bench.expectMeshExecutor")&&!mesh)throw new IllegalStateException("Mesh executor hook was not applied");
                    Files.writeString(FabricLoader.getInstance().getGameDir().resolve("comparison-result.json"),"{\"templateOnly\":true,\"worldSeed\":12345,\"flightGenerated\":false,\"localBatchHook\":"+hook+",\"meshExecutorHook\":"+mesh+",\"loadedChunks\":"+c.world.getChunkManager().getLoadedChunkCount()+",\"terrainRenderComplete\":"+c.worldRenderer.isTerrainRenderComplete()+"}");
                    done=true;c.scheduleStop();return;
                }
                if(measureAt==0){
                    if(requireFocus){
                        if(!c.isWindowFocused()){focusReadyAt=0;return;}
                        if(focusReadyAt==0)focusReadyAt=now;
                        if(now-focusReadyAt<10_000_000_000L)return;
                    }
                    Files.writeString(FabricLoader.getInstance().getGameDir().resolve("benchmark-phase.txt"),requireFocus?"focused diagnostic":"background diagnostic");
                    measureAt=now;
                }
                // Accumulate actual focused frame time, not wall time spent in another app.
                if(terrainFlight&&flightAt==0&&measuredMillis>=20_000){
                    stationaryFrames.addAll(frames);frames.clear();stalls.clear();focusedFrames=unfocusedFrames=0;measuredMillis=0;last=0;lastCpu=-1;flightAt=now;progressAt=now;previousX=c.player.getX();previousY=c.player.getY();flightChunks=deliveredChunks;flightStart=c.world.getChunkManager().getLoadedChunkCount();
                    Files.writeString(FabricLoader.getInstance().getGameDir().resolve("benchmark-phase.txt"),"new-terrain flight");return;
                }
                if(terrainFlight&&flightX<480)return;
                if(measuredMillis<(terrainFlight?30_000:20_000))return;
                if(frames.size()<150)throw new IllegalStateException("too few focused gameplay frames: "+frames.size());
                var sorted=frames.stream().mapToDouble(Double::doubleValue).sorted().toArray();
                double mean=frames.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
                String result=String.format(Locale.ROOT,"{\"batMod\":%s,\"frames\":%d,\"meanFps\":%.2f,\"medianMs\":%.3f,\"p95Ms\":%.3f,\"width\":%d,\"height\":%d,\"renderDistance\":%d,\"simulationDistance\":%d,\"cap\":%d,\"owner\":\"%s\"}",FabricLoader.getInstance().isModLoaded("batclient"),sorted.length,1000/mean,sorted[sorted.length/2],sorted[(int)(sorted.length*.95)],c.getWindow().getFramebufferWidth(),c.getWindow().getFramebufferHeight(),c.options.getViewDistance().getValue(),c.options.getSimulationDistance().getValue(),c.options.getMaxFps().getValue(),System.getProperty("batclient.performance.owner","agent"));
                int selectedLimit=c.options.getMaxFps().getValue();
                if(selectedLimit>=260)result=result.replace("\"cap\":"+selectedLimit,"\"cap\":null");
                result=result.substring(0,result.length()-1)+",\"fpsLimitMode\":\""+(selectedLimit>=260?"unlimited":"limited")+"\",\"backgroundDiagnostic\":"+backgroundDiagnostic+"}";
                if(terrainFlight){
                    double[] stationary=stationaryFrames.stream().mapToDouble(Double::doubleValue).sorted().toArray();
                    result=result.substring(0,result.length()-1)+String.format(Locale.ROOT,",\"terrainFlight\":true,\"p99Ms\":%.3f,\"maxMs\":%.3f,\"over50ms\":%d,\"deliveredFlightChunks\":%d,\"loadedStart\":%d,\"loadedEnd\":%d,\"distanceBlocks\":%.2f,\"wallSeconds\":%.2f,\"stationaryFrames\":%d,\"stationaryP95Ms\":%.3f}",sorted[(int)(sorted.length*.99)],sorted[sorted.length-1],frames.stream().filter(t->t>50).count(),deliveredChunks-flightChunks,flightStart,c.world.getChunkManager().getLoadedChunkCount(),flightX,(now-flightAt)/1e9,stationary.length,stationary[(int)(stationary.length*.95)]);
                }
                boolean biomeCacheHook=false;
                for(var annotation:net.minecraft.world.biome.source.BiomeAccess.class.getDeclaredMethod("method_38106",long.class,int.class,int.class,int.class,double.class,double.class,double.class).getDeclaredAnnotations()){
                    if(annotation.annotationType().getName().endsWith(".MixinMerged"))biomeCacheHook=annotation.annotationType().getMethod("mixin").invoke(annotation).toString().endsWith(".BiomeBiasCacheMixin");
                }
                result=result.substring(0,result.length()-1)+",\"biomeCacheHook\":"+biomeCacheHook+",\"renderThreadPriority\":"+Thread.currentThread().getPriority()+"}";
                boolean gameplayYieldHook=false;
                for(var method:MinecraftClient.class.getDeclaredMethods())for(var annotation:method.getDeclaredAnnotations()){
                    if(annotation.annotationType().getName().endsWith(".MixinMerged")&&annotation.annotationType().getMethod("mixin").invoke(annotation).toString().endsWith(".GameLoopYieldMixin"))gameplayYieldHook=true;
                }
                result=result.substring(0,result.length()-1)+",\"gameplayYieldHook\":"+gameplayYieldHook+",\"strictFocusRequired\":"+requireFocus+",\"focusedFrames\":"+focusedFrames+",\"unfocusedFrames\":"+unfocusedFrames+"}";
                boolean localBatchHook=localBatchActive();
                result=result.substring(0,result.length()-1)+",\"localBatchHook\":"+localBatchHook+"}";
                result=result.substring(0,result.length()-1)+",\"meshExecutorHook\":"+meshExecutorActive()+"}";
                if(terrainFlight){
                    var finished=com.google.gson.JsonParser.parseString(result).getAsJsonObject();
                    finished.addProperty("terrainRenderCompleteAtFinish",c.worldRenderer.isTerrainRenderComplete());
                    pendingResult=finished.toString();routeFinishedAt=now;c.player.setVelocity(0,0,0);
                    writeStalls();Files.writeString(FabricLoader.getInstance().getGameDir().resolve("flight-result.json"),pendingResult);return;
                }
                writeStalls();Files.writeString(FabricLoader.getInstance().getGameDir().resolve("comparison-result.json"),result);System.out.println("LAUNCHER_COMPARISON_OK "+result);done=true;c.scheduleStop();
            }catch(Throwable error){error.printStackTrace();done=true;try{writeStalls();Files.writeString(FabricLoader.getInstance().getGameDir().resolve("comparison-failure.txt"),error.toString());}catch(Exception ignored){}c.scheduleStop();}
        });
    }
}
