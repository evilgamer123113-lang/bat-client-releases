package com.batclient.performance;

/** Render-thread time allowances, not a frame cap or a gameplay/tick-rate modification. */
public final class AdaptiveChunkBudget {
    private double renderMillis=8;
    private double frameMillis=8;
    private long combatUntil;
    private long uploadBacklogSince;
    public void observe(long renderNanos) {
        if(renderNanos<=0)return;
        double millis=Math.min(250,renderNanos/1_000_000d);
        // Respond quickly to pressure; recover slowly rather than oscillating every frame.
        renderMillis+=(millis-renderMillis)*(millis>renderMillis?.35:.05);
    }
    public void combat(long now) {combatUntil=now+3_000_000_000L;}
    /** End-to-end frame pressure includes presentation/driver waits outside world rendering. */
    public void observeFrame(long frameNanos) {
        if(frameNanos<=0)return;
        double millis=Math.min(100,frameNanos/1_000_000d);
        // Brief backoff, fast recovery: an isolated long frame must not starve terrain.
        frameMillis+=(millis-frameMillis)*(millis>frameMillis?.5:.125);
    }
    public void clearFramePressure(){frameMillis=8;uploadBacklogSince=0;}
    /** Only actual deferred upload work creates pressure; chunk generation alone does not. */
    public void pendingUploads(long now,boolean pending){
        if(!pending)uploadBacklogSince=0;
        else if(uploadBacklogSince==0)uploadBacklogSince=now;
    }
    public boolean fighting(long now) {return combatUntil!=0&&now<combatUntil;}
    public float allowance(boolean upload,long now,int frameLimit) {
        int fps=frameLimit>0&&frameLimit<260?Math.max(30,Math.min(240,frameLimit)):60;
        double spare=1000d/fps-renderMillis-1;
        double total=Math.max(.5,Math.min(3,spare*.5));
        double renderHeadroom=total;
        double reference=1000d/fps;
        if(frameMillis>reference*1.5)total=Math.min(total,Math.max(.5,3*reference/frameMillis));
        // Presentation/scheduling waits must not leave a real upload queue permanently
        // under-served. Recover only existing render headroom, with no unbounded burst.
        if(upload&&uploadBacklogSince!=0&&now-uploadBacklogSince>=100_000_000L){
            double catchUp=Math.min(2,(now-uploadBacklogSince)/250_000_000d);
            total=Math.min(renderHeadroom,Math.max(total,catchUp));
        }
        if(fighting(now))total=Math.min(total,1);
        return (float)Math.max(upload?.35:.15,total*(upload?.75:.25));
    }
    public void reset(){renderMillis=frameMillis=8;combatUntil=uploadBacklogSince=0;}
}
