package com.batclient.hud;

/** A soft time budget: allow one task for forward progress, then defer excess work. */
public final class FrameWorkBudget {
    private long started,limit,estimatedTaskNanos;
    private int tasks;
    public void begin(long now,float millis){started=now;tasks=0;limit=(long)((Float.isFinite(millis)?Math.max(.1f,Math.min(8,millis)):2)*1_000_000);}
    public boolean take(long now){
        long elapsed=now-started;
        if(tasks>0&&(elapsed>=limit||estimatedTaskNanos>limit-elapsed))return false;
        tasks++;return true;
    }
    /** Keep the estimate across batches; measurements never retain a job or its buffers. */
    public void completed(long duration){
        if(duration<=0)return;
        long bounded=Math.min(8_000_000L,duration);
        if(estimatedTaskNanos==0)estimatedTaskNanos=bounded;
        else estimatedTaskNanos+=(bounded-estimatedTaskNanos)/(bounded>estimatedTaskNanos?2:4);
    }
}
