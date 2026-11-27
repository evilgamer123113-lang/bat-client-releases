package com.batclient;

import com.batclient.hud.SkinAlphaMask;
import com.batclient.platform.ClientHookServices;
import com.batclient.platform.ClientResourceServices;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.texture.Texture;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.WeakHashMap;

/** Cached native extrusion of visible skin pixels. No skin/cape/account changes. */
public final class SkinLayers3D {
    private static final class Quad {
        final float[] points;
        final float nx, ny, nz, u, v;
        Quad(float[] points, float nx, float ny, float nz, float u, float v) {
            this.points = points; this.nx = nx; this.ny = ny; this.nz = nz; this.u = u; this.v = v;
        }
    }

    private static final class Mesh {
        final Object texture;
        final int gl;
        final boolean slim;
        final float depth;
        final List<List<Quad>> parts;
        Mesh(Object texture, int gl, boolean slim, float depth, List<List<Quad>> parts) {
            this.texture = texture; this.gl = gl; this.slim = slim; this.depth = depth; this.parts = parts;
        }
    }

    private static final LinkedHashMap<Identifier, Mesh> CACHE = new LinkedHashMap<>(32, .75f, true);
    private static final WeakHashMap<Object, SkinAlphaMask> PIXELS = new WeakHashMap<>();
    private static final IntBuffer TEX_BUF = BufferUtils.createIntBuffer(64 * 64);
    private static long nextRead;
    private static boolean initialized, externalLayers;
    private static final PlayerModelPart[] FLAGS = {
        PlayerModelPart.HAT, PlayerModelPart.JACKET,
        PlayerModelPart.RIGHT_SLEEVE, PlayerModelPart.LEFT_SLEEVE,
        PlayerModelPart.RIGHT_PANTS_LEG, PlayerModelPart.LEFT_PANTS_LEG
    };

    private SkinLayers3D() { }

    public static void init() {
        initialized = true;
        externalLayers = com.batclient.platform.Platforms.get().isModLoaded("skinlayers3d");
        ClientHookServices.get().disconnected(SkinLayers3D::clear);
        ClientResourceServices.get().reload(new Identifier("batclient", "skin_layers"),
            () -> MinecraftClient.getInstance().submit(SkinLayers3D::clear));
        ClientResourceServices.get().playerLayers((player, add) -> add.accept(new Layer(player)));
    }

    public static void clear() {
        CACHE.clear();
        PIXELS.clear();
        nextRead = 0;
    }

    public static void capturePixels(Object texture, BufferedImage image) {
        if (!initialized || externalLayers) return;
        if (image == null || image.getWidth() != 64 || image.getHeight() != 64 || !HudConfig.enabled(VisualModules.settings("skinlayers3d"))) {
            CACHE.values().removeIf(mesh -> mesh.texture == texture);
            PIXELS.remove(texture);
            return;
        }
        SkinAlphaMask previous = PIXELS.get(texture);
        SkinAlphaMask pixels = new SkinAlphaMask((x, y) -> image.getRGB(x, y));
        if (!pixels.sameOpacity(previous)) {
            CACHE.values().removeIf(mesh -> mesh.texture == texture);
            remember(texture, pixels);
        }
    }

    public static void refreshDynamicPixels(Object texture, BufferedImage image) {
        if (!initialized || externalLayers) return;
        boolean known = PIXELS.containsKey(texture);
        if (!known) for (Mesh mesh : CACHE.values()) if (mesh.texture == texture) { known = true; break; }
        if (known) capturePixels(texture, image);
    }

    private static void remember(Object texture, SkinAlphaMask mask) {
        PIXELS.put(texture, mask);
        while (PIXELS.size() > 64) PIXELS.remove(PIXELS.keySet().iterator().next());
    }

    private static final class Layer implements FeatureRenderer<AbstractClientPlayerEntity> {
        private final PlayerEntityRenderer renderer;

        Layer(PlayerEntityRenderer renderer) {
            this.renderer = renderer;
        }

        @Override
        public void render(AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float delta, float animation, float yaw, float pitch, float scale) {
            JsonObject mod = VisualModules.settings("skinlayers3d");
            MinecraftClient c = MinecraftClient.getInstance();
            PlayerEntityModel model = (PlayerEntityModel) renderer.getModel();
            if (!HudConfig.enabled(mod) || player.isInvisible() || model.child || c.player == null || externalLayers) return;
            float range = VisualModules.number(mod, "range", 12, 4, 32);
            if (c.player.squaredDistanceTo(player) > range * range) return;
            Identifier skin = player.getSkinId();
            boolean slim = "slim".equals(player.getModel());
            Mesh mesh = mesh(c, skin, slim, VisualModules.number(mod, "thickness", .45f, .2f, .8f));
            if (mesh == null) return;
            c.getTextureManager().bindTexture(skin);
            for (int i = 0; i < 6; i++) {
                ModelPart part = i == 0 ? model.hat : i == 1 ? model.jacket : i == 2 ? model.rightSleeve : i == 3 ? model.leftSleeve : i == 4 ? model.rightPants : model.leftPants;
                if ((i == 2 || i == 3) && !HudConfig.bool(mod, "sleeves", true) || i >= 4 && !HudConfig.bool(mod, "pants", true)) continue;
                if (!part.visible || !player.isPartVisible(FLAGS[i]) || (i == 0 ? !HudConfig.bool(mod, "head", true) : !HudConfig.bool(mod, "body", true))) continue;
                GlStateManager.pushMatrix();
                try {
                    part.preRender(0.0625f);
                    Tessellator tess = Tessellator.getInstance();
                    BufferBuilder buf = tess.getBuffer();
                    buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_NORMAL);
                    for (Quad q : mesh.parts.get(i)) draw(buf, q);
                    tess.draw();
                } finally {
                    GlStateManager.popMatrix();
                }
            }
        }

        @Override
        public boolean combineTextures() {
            return false;
        }
    }

    private static Mesh mesh(MinecraftClient c, Identifier skin, boolean slim, float depth) {
        Texture texture = c.getTextureManager().getTexture(skin);
        if (texture == null) return null;
        int gl = texture.getGlId();
        long now = MinecraftClient.getTime();
        Mesh cached = CACHE.get(skin);
        if (cached != null && cached.texture == texture && cached.gl == gl && cached.slim == slim && cached.depth == depth) return cached;
        SkinAlphaMask pixels = PIXELS.get(texture);
        if (pixels != null) return cacheMesh(skin, texture, gl, slim, depth, pixels);
        if (now < nextRead) return null;
        nextRead = now + 100;
        int old = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager.bindTexture(gl);
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (width != 64 || height != 64) return null;
            TEX_BUF.clear();
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, TEX_BUF);
            final int[] rgba = new int[64 * 64];
            TEX_BUF.get(rgba);
            pixels = new SkinAlphaMask((x, y) -> rgba[y * 64 + x]);
            remember(texture, pixels);
            return cacheMesh(skin, texture, gl, slim, depth, pixels);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, old);
        }
    }

    private static Mesh cacheMesh(Identifier skin, Object texture, int gl, boolean slim, float depth, SkinAlphaMask pixels) {
        List<List<Quad>> parts = new ArrayList<>();
        parts.add(buildMask(pixels, 32, 0, -4, -8, -4, 8, 8, 8, .5f, depth));
        parts.add(buildMask(pixels, 16, 32, -4, 0, -2, 8, 12, 4, .25f, depth));
        parts.add(buildMask(pixels, 40, 32, slim ? -2 : -3, -2, -2, slim ? 3 : 4, 12, 4, .25f, depth));
        parts.add(buildMask(pixels, 48, 48, -1, -2, -2, slim ? 3 : 4, 12, 4, .25f, depth));
        parts.add(buildMask(pixels, 0, 32, -2, 0, -2, 4, 12, 4, .25f, depth));
        parts.add(buildMask(pixels, 0, 48, -2, 0, -2, 4, 12, 4, .25f, depth));
        Mesh result = new Mesh(texture, gl, slim, depth, parts);
        CACHE.put(skin, result);
        while (CACHE.size() > 24) CACHE.remove(CACHE.keySet().iterator().next());
        return result;
    }

    private static List<Quad> buildMask(SkinAlphaMask image, int u, int v, float x, float y, float z, int w, int h, int d, float inflate, float depth) {
        List<Quad> result = new ArrayList<>();
        float x0 = x - inflate, x1 = x + w + inflate, y0 = y - inflate, y1 = y + h + inflate, z0 = z - inflate, z1 = z + d + inflate;
        surface(result, image, u + d, v + d, w, h, x0, y0, z0, (x1 - x0) / w, 0, 0, 0, (y1 - y0) / h, 0, 0, 0, -1, depth);
        surface(result, image, u + d + w + d, v + d, w, h, x1, y0, z1, -(x1 - x0) / w, 0, 0, 0, (y1 - y0) / h, 0, 0, 0, 1, depth);
        surface(result, image, u, v + d, d, h, x0, y0, z1, 0, 0, -(z1 - z0) / d, 0, (y1 - y0) / h, 0, -1, 0, 0, depth);
        surface(result, image, u + d + w, v + d, d, h, x1, y0, z0, 0, 0, (z1 - z0) / d, 0, (y1 - y0) / h, 0, 1, 0, 0, depth);
        surface(result, image, u + d, v, w, d, x0, y0, z1, (x1 - x0) / w, 0, 0, 0, 0, -(z1 - z0) / d, 0, -1, 0, depth);
        surface(result, image, u + d + w, v, w, d, x0, y1, z0, (x1 - x0) / w, 0, 0, 0, 0, (z1 - z0) / d, 0, 1, 0, depth);
        return result;
    }

    private static boolean solid(SkinAlphaMask image, int x, int y) { return image.opaque(x, y); }

    private static void surface(List<Quad> out, SkinAlphaMask image, int u, int v, int w, int h, float x, float y, float z,
                                float ax, float ay, float az, float bx, float by, float bz, float nx, float ny, float nz, float depth) {
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            if (!solid(image, u + col, v + row)) continue;
            float px = x + col * ax + row * bx, py = y + col * ay + row * by, pz = z + col * az + row * bz;
            float[] a = {px, py, pz}, b = {px + ax, py + ay, pz + az}, cc = {px + ax + bx, py + ay + by, pz + az + bz}, d = {px + bx, py + by, pz + bz};
            float[] aa = lift(a, nx, ny, nz, depth), bb = lift(b, nx, ny, nz, depth), dd = lift(d, nx, ny, nz, depth), ee = lift(cc, nx, ny, nz, depth);
            float tu = (u + col + .5f) / 64, tv = (v + row + .5f) / 64;
            quad(out, aa, bb, ee, dd, nx, ny, nz, tu, tv);
            if (row == 0 || !solid(image, u + col, v + row - 1)) quad(out, a, b, bb, aa, -bx, -by, -bz, tu, tv);
            if (row == h - 1 || !solid(image, u + col, v + row + 1)) quad(out, d, dd, ee, cc, bx, by, bz, tu, tv);
            if (col == 0 || !solid(image, u + col - 1, v + row)) quad(out, a, aa, dd, d, -ax, -ay, -az, tu, tv);
            if (col == w - 1 || !solid(image, u + col + 1, v + row)) quad(out, b, cc, ee, bb, ax, ay, az, tu, tv);
        }
    }

    private static float[] lift(float[] p, float nx, float ny, float nz, float depth) {
        return new float[]{p[0] + nx * depth, p[1] + ny * depth, p[2] + nz * depth};
    }

    private static void quad(List<Quad> out, float[] a, float[] b, float[] c, float[] d, float nx, float ny, float nz, float u, float v) {
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        out.add(new Quad(new float[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2]},
            nx / length, ny / length, nz / length, u, v));
    }

    private static void draw(BufferBuilder buffer, Quad quad) {
        for (int i = 0; i < 12; i += 3) {
            buffer.vertex(quad.points[i] / 16, quad.points[i + 1] / 16, quad.points[i + 2] / 16)
                .texture(quad.u, quad.v)
                .normal(quad.nx, quad.ny, quad.nz)
                .next();
        }
    }
}
