package com.batclient.web;

import com.batclient.platform.mc1194.DrawContext;
import com.batclient.platform.mc189.GLFW;
import com.mojang.blaze3d.platform.GlStateManager;
import io.github.nilsen84.ultralight.KeyEvent;
import io.github.nilsen84.ultralight.UltralightRenderer;
import io.github.nilsen84.ultralight.UltralightView;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

/**
 * Wraps a single Ultralight view for 1.8 / 1.8.9 (LWJGL 2 + immediate Tessellator).
 */
public class WebWindow implements AutoCloseable {
    private static final java.util.Set<WebWindow> LIVE = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private boolean closed;
    public static void closeAll() { for (WebWindow window : com.batclient.platform.java8.J8.listCopyOf(LIVE)) window.close(); }
    private static final Logger LOGGER = LogManager.getLogger("batclient-web");

    private final UltralightView view;
    private int texture;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int pbo;
    private ByteBuffer staging;
    private int stagingCap;
    private volatile boolean debugLogged;
    private volatile boolean painted;
    private int uploadDiag;

    private double lastMouseX;
    private double lastMouseY;
    private float xScale = 1.0f;
    private float yScale = 1.0f;

    public WebWindow(UltralightRenderer renderer, int width, int height) {
        this.lastWidth = Math.max(1, width);
        this.lastHeight = Math.max(1, height);
        this.view = renderer.createView(this.lastWidth, this.lastHeight);
        this.texture = GL11.glGenTextures();
        this.view.focus();
        resizeTexture(this.lastWidth, this.lastHeight);
        LIVE.add(this);
    }

    public UltralightView view() {
        if (closed) throw new IllegalStateException("Native web view is closed");
        return view;
    }

    public boolean isClosed() { return closed; }

    public void loadUrl(String url) {
        if (closed) return;
        LOGGER.info("Loading {}", url);
        view.loadUrl(url);
    }

    public void windowContentScaleCallback(float xScale, float yScale) {
        this.xScale = xScale;
        this.yScale = yScale;
    }

    public boolean needsResize() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.width != lastWidth || mc.height != lastHeight;
    }

    public void prepareFrame() {
        if (closed) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int width = Math.max(1, mc.width);
        int height = Math.max(1, mc.height);
        if (width != lastWidth || height != lastHeight) {
            lastWidth = width;
            lastHeight = height;
            view.resize(width, height);
            resizeTexture(width, height);
        }
    }

    public void renderAndUpload() {
        if (closed) return;
        io.github.nilsen84.ultralight.UltralightSurface surface = view.surface();
        io.github.nilsen84.ultralight.IntRect dirty = surface.dirtyBounds();
        if (!dirty.isValid() || view.isLoading()) return;
        int sw = view.width();
        int sh = view.height();
        if (sw <= 0 || sh <= 0) return;

        int left = Math.max(0, dirty.getLeft());
        int top = Math.max(0, dirty.getTop());
        int w = Math.min(dirty.getWidth(), sw - left);
        int h = Math.min(dirty.getHeight(), sh - top);
        if (w <= 0 || h <= 0) return;

        int rowBytes = surface.rowBytes();
        int dirtyBytes = w * 4 * h;
        if (stagingCap < dirtyBytes + 4096) {
            stagingCap = Math.max(dirtyBytes + 4096, stagingCap * 2);
            staging = ByteBuffer.allocateDirect(stagingCap).order(java.nio.ByteOrder.nativeOrder());
        }
        staging.clear();

        try (io.github.nilsen84.ultralight.UltralightBuffer buffer = surface.lockPixels()) {
            ByteBuffer pixels = buffer.asByteBuffer();
            int capacity = pixels.capacity();
            if (!debugLogged) {
                debugLogged = true;
                int cornerAlpha = pixels.get(3) & 0xFF;
                LOGGER.warn("upload first-frame: view={}x{} alpha0={} dirty=({},{},{},{}) rowBytes={} loading={}",
                    sw, sh, cornerAlpha, dirty.getLeft(), dirty.getTop(), dirty.getRight(), dirty.getButtom(),
                    rowBytes, view.isLoading());
            }
            if (left == 0 && w * 4 == rowBytes) {
                pixels.position(top * rowBytes);
                pixels.limit(top * rowBytes + dirtyBytes);
                staging.put(pixels);
            } else {
                for (int y = top; y < top + h; y++) {
                    pixels.limit(capacity);
                    pixels.position(y * rowBytes + left * 4);
                    pixels.limit((y * rowBytes + left * 4) + w * 4);
                    staging.put(pixels);
                }
            }
        }

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousPbo = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int previousAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int previousRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int previousSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int previousSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);

            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo);
            staging.flip();
            GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER, dirtyBytes, GL15.GL_STREAM_DRAW);
            boolean diag = uploadDiag < 5;
            int eBufData = diag ? GL11.glGetError() : 0;
            GL15.glBufferSubData(GL21.GL_PIXEL_UNPACK_BUFFER, 0, staging);
            int eSubData = diag ? GL11.glGetError() : 0;
            GL11.glTexSubImage2D(
                GL11.GL_TEXTURE_2D, 0,
                left, top, w, h,
                GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE,
                0L
            );
            if (diag) {
                int eTex = GL11.glGetError();
                int rowLen = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
                int pboBind = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
                int pboSize = pboBind != 0 ? GL15.glGetBufferParameter(GL21.GL_PIXEL_UNPACK_BUFFER, GL15.GL_BUFFER_SIZE) : -1;
                LOGGER.warn("upload diag #{}: bufData=0x{} subData=0x{} texSub=0x{} unpackRowLength={} pbo={} pboSize={} need={}",
                    uploadDiag, Integer.toHexString(eBufData), Integer.toHexString(eSubData), Integer.toHexString(eTex),
                    rowLen, pbo, pboSize, dirtyBytes);
                uploadDiag++;
            } else {
                while (GL11.glGetError() != 0) {}
            }
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, previousPbo);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousAlignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, previousRowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, previousSkipPixels);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, previousSkipRows);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            staging.clear();
        }

        surface.clearDirtyBounds();
        painted = true;
    }

    public void draw(DrawContext context) {
        Window win = new Window(MinecraftClient.getInstance());
        draw(context, 0, 0, (float) win.getScaledWidth(), (float) win.getScaledHeight());
    }

    public void draw(DrawContext context, float left, float top, float width, float height) {
        drawRegion(context, left, top, width, height, 0, 0, 1, 1);
    }

    public void drawRegion(DrawContext context, float left, float top, float width, float height, float u1, float v1, float u2, float v2) {
        if (closed) return;

        int previousShaderTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        int previousSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int previousDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int previousSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int previousDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try {
            GlStateManager.enableTexture();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableDepthTest();
            GlStateManager.depthMask(false);
            GlStateManager.disableCull();
            GlStateManager.activeTexture(GL13.GL_TEXTURE0);
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GlStateManager.bindTexture(texture);
            GlStateManager.disableAlphaTest();

            float x1 = left, y1 = top;
            float x2 = left + width, y2 = top + height;
            float z = 0;

            BufferBuilder builder = Tessellator.getInstance().getBuffer();
            builder.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE);
            builder.vertex(x1, y1, z).texture(u1, v1).next();
            builder.vertex(x1, y2, z).texture(u1, v2).next();
            builder.vertex(x2, y2, z).texture(u2, v2).next();
            builder.vertex(x2, y1, z).texture(u2, v1).next();
            Tessellator.getInstance().draw();
        } finally {
            GlStateManager.bindTexture(previousShaderTexture);
            GlStateManager.enableAlphaTest();
            GlStateManager.blendFuncSeparate(previousSrcRgb, previousDstRgb, previousSrcAlpha, previousDstAlpha);
            if (!previousBlend) GlStateManager.disableBlend();
        }
    }

    public void onCursorPos(double x, double y) {
        if (closed) return;
        double nextX = x * xScale;
        double nextY = y * yScale;
        if ((int) nextX != (int) this.lastMouseX || (int) nextY != (int) this.lastMouseY) {
            WebController.INSTANCE.requestUiFrame();
        }
        this.lastMouseX = nextX;
        this.lastMouseY = nextY;
        int button = pressedButton();
        if (button != 3) WebController.INSTANCE.requestUiFrame();
        view.fireMouseMoveEvent(button, (int) lastMouseX, (int) lastMouseY);
    }

    public void onMouseButton(int glfwButton, int action, int x, int y) {
        if (closed) return;
        WebController.INSTANCE.requestUiFrame();
        int button;
        switch (glfwButton) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT: button = 0; break;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT: button = 1; break;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE: button = 2; break;
            default: button = 3; break;
        }
        boolean down = action == GLFW.GLFW_PRESS;
        view.fireMouseButtonEvent(button, down, (int) (x * xScale), (int) (y * yScale));
    }

    public void onScroll(double horizontalAmount, double verticalAmount) {
        if (closed) return;
        WebController.INSTANCE.requestUiFrame();
        int deltaY = (int) (verticalAmount * 50);
        if (deltaY != 0) view.fireScrollEvent(deltaY);
    }

    public void onCharMods(int codepoint, int mods) {
        if (closed) return;
        WebController.INSTANCE.requestUiFrame();
        String text = new String(Character.toChars(codepoint));
        view.fireKeyEvent(new KeyEvent(KeyEvent.Type.Char, modifiers(mods), 0, 0, text, text, false, false, false));
    }

    public void onKey(int lwjglKey, int scanCode, int action, int mods) {
        if (closed) return;
        WebController.INSTANCE.requestUiFrame();
        KeyEvent.Type type = action == GLFW.GLFW_PRESS ? KeyEvent.Type.KeyDown
            : (action == GLFW.GLFW_RELEASE ? KeyEvent.Type.KeyUp : null);
        if (type == null) return;

        boolean synthesizeChar = type == KeyEvent.Type.KeyDown
            && (lwjglKey == Keyboard.KEY_RETURN || lwjglKey == Keyboard.KEY_TAB);
        int virtualKey = lwjglToVirtualKey(lwjglKey);
        view.fireKeyEvent(new KeyEvent(type, modifiers(mods), virtualKey, scanCode, "", "", false, false, false));

        if (synthesizeChar) {
            String text = lwjglKey == Keyboard.KEY_RETURN ? "\n" : "\t";
            view.fireKeyEvent(new KeyEvent(KeyEvent.Type.Char, modifiers(mods), 0, 0, text, text, false, false, false));
        }
    }

    private static int lwjglToVirtualKey(int key) {
        switch (key) {
            case Keyboard.KEY_BACK: return 8;
            case Keyboard.KEY_TAB: return 9;
            case Keyboard.KEY_RETURN: return 13;
            case Keyboard.KEY_LSHIFT:
            case Keyboard.KEY_RSHIFT: return 16;
            case Keyboard.KEY_LCONTROL:
            case Keyboard.KEY_RCONTROL: return 17;
            case Keyboard.KEY_LMENU:
            case Keyboard.KEY_RMENU: return 18;
            case Keyboard.KEY_ESCAPE: return 27;
            case Keyboard.KEY_SPACE: return 32;
            case Keyboard.KEY_PRIOR: return 33;
            case Keyboard.KEY_NEXT: return 34;
            case Keyboard.KEY_END: return 35;
            case Keyboard.KEY_HOME: return 36;
            case Keyboard.KEY_LEFT: return 37;
            case Keyboard.KEY_UP: return 38;
            case Keyboard.KEY_RIGHT: return 39;
            case Keyboard.KEY_DOWN: return 40;
            case Keyboard.KEY_DELETE: return 46;
            case Keyboard.KEY_0: return 48;
            case Keyboard.KEY_1: return 49;
            case Keyboard.KEY_2: return 50;
            case Keyboard.KEY_3: return 51;
            case Keyboard.KEY_4: return 52;
            case Keyboard.KEY_5: return 53;
            case Keyboard.KEY_6: return 54;
            case Keyboard.KEY_7: return 55;
            case Keyboard.KEY_8: return 56;
            case Keyboard.KEY_9: return 57;
            case Keyboard.KEY_A: return 65;
            case Keyboard.KEY_B: return 66;
            case Keyboard.KEY_C: return 67;
            case Keyboard.KEY_D: return 68;
            case Keyboard.KEY_E: return 69;
            case Keyboard.KEY_F: return 70;
            case Keyboard.KEY_G: return 71;
            case Keyboard.KEY_H: return 72;
            case Keyboard.KEY_I: return 73;
            case Keyboard.KEY_J: return 74;
            case Keyboard.KEY_K: return 75;
            case Keyboard.KEY_L: return 76;
            case Keyboard.KEY_M: return 77;
            case Keyboard.KEY_N: return 78;
            case Keyboard.KEY_O: return 79;
            case Keyboard.KEY_P: return 80;
            case Keyboard.KEY_Q: return 81;
            case Keyboard.KEY_R: return 82;
            case Keyboard.KEY_S: return 83;
            case Keyboard.KEY_T: return 84;
            case Keyboard.KEY_U: return 85;
            case Keyboard.KEY_V: return 86;
            case Keyboard.KEY_W: return 87;
            case Keyboard.KEY_X: return 88;
            case Keyboard.KEY_Y: return 89;
            case Keyboard.KEY_Z: return 90;
            default: return 0;
        }
    }

    private static EnumSet<KeyEvent.Modifier> modifiers(int mods) {
        EnumSet<KeyEvent.Modifier> set = EnumSet.noneOf(KeyEvent.Modifier.class);
        if ((mods & GLFW.GLFW_MOD_SHIFT) != 0) set.add(KeyEvent.Modifier.Shift);
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) set.add(KeyEvent.Modifier.Ctrl);
        if ((mods & GLFW.GLFW_MOD_ALT) != 0) set.add(KeyEvent.Modifier.Alt);
        if ((mods & GLFW.GLFW_MOD_SUPER) != 0) set.add(KeyEvent.Modifier.Meta);
        return set;
    }

    private int pressedButton() {
        if (GLFW.glfwGetMouseButton(0L, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) return 0;
        if (GLFW.glfwGetMouseButton(0L, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS) return 1;
        if (GLFW.glfwGetMouseButton(0L, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS) return 2;
        return 3;
    }

    private void resizeTexture(int width, int height) {
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousPbo = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, previousPbo);
        }
        if (pbo == 0) pbo = GL15.glGenBuffers();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        LIVE.remove(this);
        try {
            view.setLoadListener(null);
            view.setViewListener(null);
            view.close();
        } catch (Throwable ignored) {}
        if (pbo != 0) {
            GL15.glDeleteBuffers(pbo);
            pbo = 0;
        }
        if (texture != 0) {
            GlStateManager.deleteTexture(texture);
            texture = 0;
        }
    }

    public boolean hasPainted() {
        return painted;
    }
}
