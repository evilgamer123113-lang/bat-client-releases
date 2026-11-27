package com.batclient.performance;

import com.batclient.platform.ClientHookServices;


import net.minecraft.client.MinecraftClient;

/** Always active: observes real world-render work without changing JVM/graphics/input options. */
public final class BuiltinPerformance {
    private static final AdaptiveChunkBudget BUDGET=new AdaptiveChunkBudget();
    private static long renderStarted,chunkNanos,previousFrame;
    private static int frameLimit=120;
    private static Object world;
    private BuiltinPerformance() { }
    public static void init(){
        PerformanceOwnership.claim();
        ClientHookServices.get().endTick(client->{
            if(world!=client.world){world=client.world;BUDGET.reset();renderStarted=chunkNanos=previousFrame=0;}
            if(client.currentScreen!=null||!client.isWindowFocused()){
                previousFrame=0;BUDGET.clearFramePressure();
            }
            frameLimit=client.options.getMaxFps().getValue();
            if(client.player!=null&&client.player.hurtTime>0)combat();
        });
        ClientHookServices.get().worldStart(()->{
            long now=System.nanoTime();
            MinecraftClient client=MinecraftClient.getInstance();
            if(client.currentScreen==null&&client.isWindowFocused()){
                if(previousFrame!=0)BUDGET.observeFrame(now-previousFrame);
                previousFrame=now;
            }else{
                previousFrame=0;BUDGET.clearFramePressure();
            }
            renderStarted=now;chunkNanos=0;
        });
        ClientHookServices.get().worldEnd(()->{
            if(renderStarted!=0)BUDGET.observe(Math.max(1,System.nanoTime()-renderStarted-chunkNanos));
            renderStarted=0;
        });
    }
    public static void combat(){BUDGET.combat(System.nanoTime());}
    public static float rebuildBudget(){return BUDGET.allowance(false,System.nanoTime(),frameLimit);}
    public static float uploadBudget(){return BUDGET.allowance(true,System.nanoTime(),frameLimit);}
    public static void pendingUploads(boolean pending){BUDGET.pendingUploads(System.nanoTime(),pending);}
    public static void chunkWork(long duration){if(renderStarted!=0&&duration>0)chunkNanos+=duration;}
}
