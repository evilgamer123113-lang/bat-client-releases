package com.batclient.hud;

import java.util.Locale;

/** Shared finite, bounded health formatting for HUD and world labels. */
public final class HealthValues {
    private HealthValues() { }
    public static float max(float value){return Float.isFinite(value)?Math.max(1,value):20;}
    public static float health(float value,float max){return Float.isFinite(value)?Math.max(0,Math.min(value,max(max))):0;}
    public static float absorption(float value){return Float.isFinite(value)?Math.max(0,Math.min(value,2048)):0;}
    public static float ratio(float value,float maximum){return health(value,maximum)/max(maximum);}
    public static int slots(float maximum){return Math.max(1,Math.min(30,(int)Math.ceil(max(maximum)/2)));}
    /** 0=empty, 1=half, 2=full; large health pools fit within three rows. */
    public static int fill(float value,float maximum,int slot){
        float units=health(value,maximum)/(Math.max(2,max(maximum)/slots(maximum))/2);
        return Math.max(0,Math.min(2,(int)Math.ceil(units)-slot*2));
    }
    public static String hearts(float health,float maximum,boolean showMax) {
        return "♥ "+String.format(Locale.ROOT,"%.1f",health(health,maximum)/2)+(showMax?" / "+String.format(Locale.ROOT,"%.1f",max(maximum)/2):"");
    }
    public static String row(float health,float maximum) {
        int total=Math.max(1,Math.min(10,(int)Math.ceil(max(maximum)/2)));
        int full=Math.max(0,Math.min(total,(int)Math.ceil(ratio(health,maximum)*total)));
        StringBuilder row=new StringBuilder(total);
        for(int i=0;i<total;i++)row.append(i<full?'♥':'♡');
        return row.toString();
    }
}
