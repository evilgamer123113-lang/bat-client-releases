package com.batclient.platform.mc1206;

import net.minecraft.util.DyeColor;

public final class DyeColors {
    private DyeColors() { }
    public static float[] rgb(DyeColor dye) {
        if (dye == null) return new float[]{1f, 1f, 1f};
        int c = dye.getMaterialColor().color;
        return new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }
}
