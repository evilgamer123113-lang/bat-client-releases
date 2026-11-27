package com.batclient.hud;

/** Small deterministic rules shared by native chat and isolated checks. */
public final class ChatFlow {
    private ChatFlow() { }
    public static boolean repeats(Object text,Object previous,Object sender,Object previousSender,int ticks,int previousTicks,float seconds) {
        float window=Float.isFinite(seconds)?Math.clamp(seconds,1,60):10;
        return sender!=null && sender.equals(previousSender) && text.equals(previous) && ticks>=previousTicks && (long)ticks-previousTicks<=window*20;
    }
    public static float progress(long elapsed,float duration) {
        float ms=Float.isFinite(duration)?Math.clamp(duration,100,500):200;
        float t=Math.clamp(elapsed/ms,0,1);
        return 1-(1-t)*(1-t)*(1-t);
    }
}
