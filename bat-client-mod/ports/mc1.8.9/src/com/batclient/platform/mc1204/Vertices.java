package com.batclient.platform.mc1204;

import com.batclient.platform.mc189.MatrixStack;
import net.minecraft.client.render.BufferBuilder;

/** Version bridge for 1.8 / 1.8.9 BufferBuilder vertices. */
public final class Vertices {
    private Vertices() {}
    public static BufferBuilder vertex(BufferBuilder v, MatrixStack.Entry m, float x, float y, float z) {
        return v.vertex(x, y, z);
    }
    public static BufferBuilder vertex(BufferBuilder v, Object m, float x, float y, float z) {
        return v.vertex(x, y, z);
    }
    public static BufferBuilder normal(BufferBuilder v, MatrixStack.Entry m, float x, float y, float z) {
        return v.normal(x, y, z);
    }
    public static BufferBuilder normal(BufferBuilder v, Object m, float x, float y, float z) {
        return v.normal(x, y, z);
    }
    public static BufferBuilder color(BufferBuilder v, int argb) {
        return v.color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }
}
