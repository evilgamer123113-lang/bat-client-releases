package com.batclient.platform.mc189;

import com.mojang.blaze3d.platform.GlStateManager;

/** MatrixStack shim for 1.8 / 1.8.9 backed by GlStateManager. */
public class MatrixStack {
    public static final class Entry {
        private static final Entry INSTANCE = new Entry();
        public Object getModel() { return null; }
        public Object getPositionMatrix() { return null; }
        public Object getNormal() { return null; }
    }

    public void push() { GlStateManager.pushMatrix(); }
    public void pop() { GlStateManager.popMatrix(); }
    public void translate(double x, double y, double z) { GlStateManager.translate(x, y, z); }
    public void translate(float x, float y, float z) { GlStateManager.translate(x, y, z); }
    public void scale(float x, float y, float z) { GlStateManager.scale(x, y, z); }
    public void loadIdentity() { GlStateManager.loadIdentity(); }
    public Entry peek() { return Entry.INSTANCE; }
}
