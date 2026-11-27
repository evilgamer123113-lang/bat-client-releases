package com.batclient.platform.mc1206;

import net.minecraft.util.DyeColor;

/** 1.20.6 exposes normalized RGB components rather than the later packed color getter. */
public final class DyeColors {
    private DyeColors() {}
    public static int rgb(DyeColor dye){
        float[] color=dye.getColorComponents();
        return (Math.round(color[0]*255)<<16)|(Math.round(color[1]*255)<<8)|Math.round(color[2]*255);
    }
}
