package com.batclient;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

/** GPU-only temporal accumulation, after the world/hand and before HUD or menus. */
public final class MotionBlur {
    private static int texture, width, height;
    private static boolean valid, failed;
    private static long lastFrame;
    private static ClientWorld world;

    private MotionBlur() { }

    private static boolean active(MinecraftClient client) {
        JsonObject mod = HudConfig.module(HudConfig.snapshot(), "motionblur");
        return !failed && HudConfig.enabled(mod) && client.world != null && client.player != null
            && client.currentScreen == null && !client.isPaused() && Display.isActive();
    }

    public static void prepare() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!active(client)) {
            if (texture != 0) { GL11.glDeleteTextures(texture); texture = 0; }
            valid = false;
            world = null;
            lastFrame = 0;
        }
    }

    public static void render() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!active(client)) return;
        Framebuffer target = client.getFramebuffer();
        int w = target.textureWidth, h = target.textureHeight;
        if (w <= 0 || h <= 0) return;
        long now = System.nanoTime();
        double seconds = (now - lastFrame) / 1_000_000_000.0;
        if (world != client.world || seconds > .25 || seconds <= 0) valid = false;
        world = client.world;
        lastFrame = now;
        float strength = HudConfig.setNumber(HudConfig.module(HudConfig.snapshot(), "motionblur"), "strength", 40);
        float alpha = historyWeight(strength, seconds);
        if (alpha <= 0) { valid = false; return; }

        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean tex2d = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        try {
            if (texture == 0 || width != w || height != h) {
                if (texture != 0) GL11.glDeleteTextures(texture);
                texture = GL11.glGenTextures();
                width = w;
                height = h;
                valid = false;
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            if (valid) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPushMatrix();
                GL11.glLoadIdentity();
                GL11.glOrtho(0, 1, 0, 1, -1, 1);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPushMatrix();
                GL11.glLoadIdentity();

                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4f(1f, 1f, 1f, alpha);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glTexCoord2f(0f, 0f); GL11.glVertex2f(0f, 0f);
                GL11.glTexCoord2f(1f, 0f); GL11.glVertex2f(1f, 0f);
                GL11.glTexCoord2f(1f, 1f); GL11.glVertex2f(1f, 1f);
                GL11.glTexCoord2f(0f, 1f); GL11.glVertex2f(0f, 1f);
                GL11.glEnd();
                GL11.glColor4f(1f, 1f, 1f, 1f);

                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
            }
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            valid = true;
        } catch (RuntimeException e) {
            failed = true;
            valid = false;
            BatClientMod.LOGGER.warn("[motionblur] Effect disabled after GPU initialization failure: {}", e.toString());
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture);
            if (tex2d) GL11.glEnable(GL11.GL_TEXTURE_2D); else GL11.glDisable(GL11.GL_TEXTURE_2D);
            if (blend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (alphaTest) GL11.glEnable(GL11.GL_ALPHA_TEST); else GL11.glDisable(GL11.GL_ALPHA_TEST);
        }
    }

    /** Equal decay over elapsed time, independent of rendering FPS. */
    public static float historyWeight(float strength, double seconds) {
        if (!Float.isFinite(strength) || !Double.isFinite(seconds) || seconds <= 0 || seconds > .25) return 0;
        return (float) Math.pow(MathHelper.clamp(strength / 100.0, 0, .85), seconds * 60);
    }

    public static void close() {
        if (texture != 0) GL11.glDeleteTextures(texture);
        texture = 0;
        valid = false;
        world = null;
        lastFrame = 0;
    }
}
