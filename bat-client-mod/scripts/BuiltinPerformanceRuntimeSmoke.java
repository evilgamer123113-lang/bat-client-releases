import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Protected native fixture: disposable world, cosmetics agent, custom GC, real mixin application. */
public final class BuiltinPerformanceRuntimeSmoke implements ClientModInitializer {
    private boolean started,done;
    private int wait,ticks,checks,frameLimit;
    private long created,previous;
    private boolean rawInput;
    private int adaptedWorldFrames;
    private Throwable worldAdapterFailure;
    private final List<Double> frames=new ArrayList<>();
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private Class<?> protectedClass(String name)throws Exception{
        String line=Files.readAllLines(Path.of(System.getProperty("bat.smoke.mapping"))).stream().filter(s->s.startsWith(name+" -> ")).findFirst().orElseThrow();
        return Class.forName(line.substring(line.indexOf(" -> ")+4,line.length()-1));
    }
    private Method method(String owner,String name,Class<?>...args)throws Exception{
        List<String> lines=Files.readAllLines(Path.of(System.getProperty("bat.smoke.mapping")));
        boolean inside=false;
        for(String line:lines){
            if(!line.startsWith(" ")){if(inside)break;inside=line.startsWith(owner+" -> ");continue;}
            if(inside&&line.contains(" "+name+"(")){Method result=protectedClass(owner).getDeclaredMethod(line.substring(line.indexOf(" -> ")+4).trim(),args);result.setAccessible(true);return result;}
        }
        throw new NoSuchMethodException(owner+"."+name);
    }
    @Override public void onInitializeClient(){
        try{
            Class<?> contract=Class.forName("com.batclient.platform.ClientHooks");
            Class<?> frame=Class.forName("com.batclient.platform.WorldFrame");
            Object hooks=Class.forName("com.batclient.platform.ClientHookServices").getMethod("get").invoke(null);
            contract.getMethod("afterEntities",java.util.function.Consumer.class).invoke(hooks,(java.util.function.Consumer<Object>)view->{
                if(!started||done)return;
                try{
                    if(frame.getMethod("world").invoke(view)!=MinecraftClient.getInstance().world
                        ||frame.getMethod("matrixStack").invoke(view)==null
                        ||frame.getMethod("camera").invoke(view)==null
                        ||frame.getMethod("consumers").invoke(view)==null
                        ||!Float.isFinite((float)frame.getMethod("tickDelta").invoke(view)))throw new AssertionError("Invalid shared render context");
                    adaptedWorldFrames++;
                }catch(Throwable error){worldAdapterFailure=error;}
            });
        }catch(Exception error){throw new IllegalStateException("Cannot install world adapter fixture",error);}
        WorldRenderEvents.END.register(context->{if(started&&!done&&MinecraftClient.getInstance().world!=null){long now=System.nanoTime();if(previous!=0&&frames.size()<20000)frames.add((now-previous)/1_000_000d);previous=now;}});
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(done)return;
            try{
                if(!started){
                    if(client.currentScreen==null||client.getOverlay()!=null||++wait<40)return;
                    Path game=FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
                    check(game.getFileName().toString().startsWith("builtin-performance-native"),"isolated game directory");
                    Class<?> environment=Class.forName("com.batclient.platform.PlatformEnvironment");
                    Object platform=Class.forName("com.batclient.platform.Platforms").getMethod("get").invoke(null);
                    check(environment.getMethod("getConfigDir").invoke(platform).equals(FabricLoader.getInstance().getConfigDir()),"platform preserves config directory");
                    check(Class.forName("com.batclient.cosmetics.AgentMain",false,ClassLoader.getSystemClassLoader())!=null,"actual cosmetics agent present on JVM system classloader");
                    check(java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().noneMatch(gc->gc.getName().contains("G1")),"custom SerialGC remains selected");
                    rawInput=client.options.getRawMouseInput().getValue();frameLimit=client.options.getMaxFps().getValue();
                    client.options.pauseOnLostFocus=false;client.options.getViewDistance().setValue(5);
                    started=true;created=System.nanoTime();
                    var info=new net.minecraft.world.level.LevelInfo("Builtin Performance Test",net.minecraft.world.GameMode.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.GameRules(),net.minecraft.resource.DataConfiguration.SAFE_MODE);
                    client.createIntegratedServerLoader().createAndStart("BatPerf-"+System.currentTimeMillis(),info,new net.minecraft.world.gen.GeneratorOptions(12345L,false,false),registry->registry.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).get(net.minecraft.world.gen.WorldPresets.FLAT).createDimensionsRegistryHolder(),client.currentScreen);
                    return;
                }
                if(client.player==null||client.world==null){if(System.nanoTime()-created>180_000_000_000L)throw new AssertionError("world load timeout");return;}
                ticks++;if(ticks<110&&client.currentScreen!=null)client.setScreen(null);
                if(ticks==60){
                    check("mod".equals(System.getProperty("batclient.performance.owner")),"protected mod claims performance across classloaders");
                    Class<?> tuner=Class.forName("com.batclient.cosmetics.PerformanceTuner");
                    // Agent "named" is Mojang named, while Loom here is Yarn named.
                    // Exercise the actual premain-loaded entrypoint explicitly; this
                    // fixture cannot establish production intermediary hook coverage.
                    tuner.getMethod("onTick").invoke(null);
                    Field delegated=tuner.getDeclaredField("delegated");delegated.setAccessible(true);
                    check(delegated.getBoolean(null),"premain-loaded tuner entrypoint delegates to protected mod");
                    check(((Integer)tuner.getMethod("fps").invoke(null))==0,"legacy tick cadence cannot throttle cosmetic uploads");
                    client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
                    boolean sodium=FabricLoader.getInstance().isModLoaded("sodium");
                    check(Arrays.stream(net.minecraft.client.render.WorldRenderer.class.getDeclaredFields()).anyMatch(f->f.getName().contains("bat$budget"))!=sodium,"chunk rebuild hook yields to Sodium");
                    check(Arrays.stream(net.minecraft.client.render.chunk.ChunkBuilder.class.getDeclaredFields()).anyMatch(f->f.getName().contains("bat$budget"))!=sodium,"chunk upload hook yields to Sodium");
                    check(Class.forName("com.batclient.BatClientMod").getMethod("cachedOverlay").invoke(null)==null,"no hidden menu created while chunks load");
                    method("com.batclient.HudConfig","updateModule",String.class,String.class,boolean.class).invoke(null,"smoothchunks","{\"en\":false,\"set\":{\"uploadBudget\":8}}",false);
                    check(Boolean.TRUE.equals(method("com.batclient.ChunkSmoothing","enabled").invoke(null)),"legacy module disable cannot disable built-in scheduler");
                    method("com.batclient.performance.BuiltinPerformance","combat").invoke(null);
                    float upload=(float)method("com.batclient.performance.BuiltinPerformance","uploadBudget").invoke(null);
                    check(upload<=.75f&&upload>=.35f,"combat budget is bounded in actual native runtime");
                    check(client.options.getRawMouseInput().getValue()==rawInput&&client.options.getMaxFps().getValue()==frameLimit,"input and FPS preferences preserved");
                    // Force newly observed chunks in this disposable world only.
                    var server=client.getServer();var id=client.player.getUuid();server.execute(()->{var player=server.getPlayerManager().getPlayer(id);if(player!=null)player.requestTeleport(128,80,128);});
                }
                if(ticks==110)Class.forName("com.batclient.BatClientMod").getMethod("openWaypoints",MinecraftClient.class).invoke(null,client);
                if(ticks==140){check(client.currentScreen!=null&&Class.forName("com.batclient.BatClientMod").getMethod("cachedOverlay").invoke(null)!=null,"menu initializes lazily on explicit opening");client.setScreen(null);}
                if(ticks>=180){
                    check(worldAdapterFailure==null&&adaptedWorldFrames>10,"shared world adapter supplies live world, matrices, buffers, camera and tick delta");
                    Class<?> resources=Class.forName("com.batclient.platform.ClientResources");
                    check(ServiceLoader.load(resources).stream().count()==1,"one protected resource adapter");
                    check(frames.size()>10,"world frames rendered through chunk loading and combat budget");
                    double[] sorted=frames.stream().mapToDouble(Double::doubleValue).sorted().toArray();
                    String result=String.format(Locale.ROOT,"BUILTIN_PERFORMANCE_NATIVE_OK: %d checks; renderer=%s; %d frames, median %.2fms, p95 %.2fms, max %.2fms. Diagnostic includes world entry/teleport/menu opening; no baseline or FPS gain claim.",checks,FabricLoader.getInstance().isModLoaded("sodium")?"sodium":"vanilla",sorted.length,sorted[sorted.length/2],sorted[(int)(sorted.length*.95)],sorted[sorted.length-1]);
                    finish(client,result);
                }
            }catch(Throwable error){error.printStackTrace();finish(client,"FAILED: "+error);}
        });
    }
    private void finish(MinecraftClient client,String result){done=true;try{Files.writeString(FabricLoader.getInstance().getGameDir().resolve("result.txt"),result);}catch(Exception error){error.printStackTrace();}System.out.println(result);client.scheduleStop();}
}
