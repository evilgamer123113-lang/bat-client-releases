package com.batclient.hud;
public final class PotionExpiry {
    private PotionExpiry() { }
    public static boolean warning(int ticks,boolean infinite,boolean enabled,float seconds) {
        float limit=Float.isFinite(seconds)?Math.max(1,Math.min(60,seconds)):10;
        return enabled&&!infinite&&ticks>0&&ticks<=limit*20;
    }
}
