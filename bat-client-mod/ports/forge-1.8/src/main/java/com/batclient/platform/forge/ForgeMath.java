package com.batclient.platform.forge;

import net.minecraft.util.MathHelper;

public final class ForgeMath {
    private ForgeMath() {}

    public static int clamp(int value, int min, int max) {
        return MathHelper.clamp_int(value, min, max);
    }

    public static float clamp(float value, float min, float max) {
        return MathHelper.clamp_float(value, min, max);
    }

    public static double clamp(double value, double min, double max) {
        return MathHelper.clamp_double(value, min, max);
    }

    public static float wrapDegrees(float value) {
        return MathHelper.wrapAngleTo180_float(value);
    }

    public static double wrapDegrees(double value) {
        return MathHelper.wrapAngleTo180_double(value);
    }
}
