package com.batclient;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.world.ClientWorld;
import static org.lwjgl.opengl.GL33.*;

/** GPU-only temporal accumulation, after the world/hand and before HUD or menus. */
public final class MotionBlur {
    private static int texture, program, vao, alphaUniform, width, height;
    private static boolean valid, failed;
    private static long lastFrame;
    private static ClientWorld world;
    private MotionBlur() { }

    private static boolean active(MinecraftClient client) {
        JsonObject mod = HudConfig.module(HudConfig.snapshot(), "motionblur");
        return !failed && HudConfig.enabled(mod) && client.world != null && client.player != null
                && client.currentScreen == null && !client.isPaused() && client.isWindowFocused();
    }

    public static void prepare() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!active(client)) {
            // No full-screen textures or draw calls while the module is disabled.
            if (texture != 0) { glDeleteTextures(texture); texture = 0; }
            valid = false; world = null; lastFrame = 0;
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
        world = client.world; lastFrame = now;
        float strength = HudConfig.setNumber(HudConfig.module(HudConfig.snapshot(), "motionblur"), "strength", 40);
        float alpha = historyWeight(strength, seconds);
        if (alpha <= 0) { valid = false; return; }

        // Restore raw GL state exactly so RenderSystem's cached state stays consistent.
        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM), oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int oldActive = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int oldUnpack = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        int oldRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING), oldDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int srcRgb = glGetInteger(GL_BLEND_SRC_RGB), dstRgb = glGetInteger(GL_BLEND_DST_RGB);
        int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        int eqRgb = glGetInteger(GL_BLEND_EQUATION_RGB), eqAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        boolean blend = glIsEnabled(GL_BLEND), depth = glIsEnabled(GL_DEPTH_TEST);
        boolean cull = glIsEnabled(GL_CULL_FACE), scissor = glIsEnabled(GL_SCISSOR_TEST);
        int[] viewport = new int[4]; glGetIntegerv(GL_VIEWPORT, viewport);
        try {
            if (program == 0) initializeShader();
            if (texture == 0 || width != w || height != h) {
                if (texture != 0) glDeleteTextures(texture);
                texture = glGenTextures(); width = w; height = h; valid = false;
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
            }
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target.fbo);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, target.fbo);
            glBindTexture(GL_TEXTURE_2D, texture);
            if (valid) {
                glViewport(0, 0, w, h);
                glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE); glDisable(GL_SCISSOR_TEST);
                glEnable(GL_BLEND); glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
                glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
                glUseProgram(program); glUniform1f(alphaUniform, alpha); glBindVertexArray(vao);
                glDrawArrays(GL_TRIANGLES, 0, 3);
            }
            // Store only world pixels, never the HUD, chat, crosshair or settings UI.
            glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            valid = true;
        } catch (RuntimeException e) {
            failed = true; valid = false;
            BatClientMod.LOGGER.warn("[motionblur] Effect disabled after GPU initialization failure: {}", e.toString());
        } finally {
            glUseProgram(oldProgram); glBindVertexArray(oldVao);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, oldUnpack);
            glBindTexture(GL_TEXTURE_2D, oldTexture); glActiveTexture(oldActive);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, oldRead); glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldDraw);
            glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha); glBlendEquationSeparate(eqRgb, eqAlpha);
            restore(GL_BLEND, blend); restore(GL_DEPTH_TEST, depth);
            restore(GL_CULL_FACE, cull); restore(GL_SCISSOR_TEST, scissor);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    /** Equal decay over elapsed time, independent of rendering FPS. */
    public static float historyWeight(float strength, double seconds) {
        if (!Float.isFinite(strength) || !Double.isFinite(seconds) || seconds <= 0 || seconds > .25) return 0;
        return (float) Math.pow(Math.clamp(strength / 100.0, 0, .85), seconds * 60);
    }

    private static void restore(int capability, boolean enabled) {
        if (enabled) glEnable(capability); else glDisable(capability);
    }

    private static int shader(int type, String source) {
        int id = glCreateShader(type);
        glShaderSource(id, source); glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(id); glDeleteShader(id); throw new IllegalStateException(log);
        }
        return id;
    }

    private static void initializeShader() {
        int vertex = 0, fragment = 0, linked = 0;
        try {
            vertex = shader(GL_VERTEX_SHADER, """
                #version 150
                out vec2 uv;
                void main() {
                    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                    uv = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                }
                """);
            fragment = shader(GL_FRAGMENT_SHADER, """
                #version 150
                uniform sampler2D history;
                uniform float weight;
                in vec2 uv;
                out vec4 color;
                void main() { color = vec4(texture(history, uv).rgb, weight); }
                """);
            linked = glCreateProgram(); glAttachShader(linked, vertex); glAttachShader(linked, fragment);
            glLinkProgram(linked);
            if (glGetProgrami(linked, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(linked));
            glUseProgram(linked); glUniform1i(glGetUniformLocation(linked, "history"), 0);
            alphaUniform = glGetUniformLocation(linked, "weight"); vao = glGenVertexArrays();
            program = linked; linked = 0;
        } finally {
            if (vertex != 0) glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
            if (linked != 0) glDeleteProgram(linked);
        }
    }

    public static void close() {
        if (texture != 0) glDeleteTextures(texture);
        if (program != 0) glDeleteProgram(program);
        if (vao != 0) glDeleteVertexArrays(vao);
        texture = program = vao = 0; valid = false; world = null; lastFrame = 0;
    }
}
