package com.batclient.platform.mc1215;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.util.Identifier;

import java.util.concurrent.atomic.AtomicLong;

/** Submits the premultiplied web surface to Minecraft's queued GUI renderer. */
public final class GpuWebQuad implements AutoCloseable {
    public static final com.mojang.blaze3d.pipeline.RenderPipeline PIPELINE =
        RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA;
    private static final AtomicLong IDS = new AtomicLong();
    private final Identifier id = Identifier.of("batclient", "dynamic/web_" + IDS.incrementAndGet());
    private int textureWidth;
    private int textureHeight;

    private static final class Surface extends AbstractTexture {
        Surface(GpuTexture texture, GpuTextureView view) {
            glTexture = texture;
            glTextureView = view;
        }
    }

    public int resize(int width, int height) {
        var device = RenderSystem.getDevice();
        var texture = device.createTexture("Bat web surface", GpuTexture.USAGE_TEXTURE_BINDING,
            TextureFormat.RGBA8, width, height, 1, 1);
        if (!(texture instanceof GlTexture gl)) {
            texture.close();
            throw new IllegalStateException("Ultralight PBO upload requires the OpenGL backend");
        }
        Surface surface = null;
        try {
            texture.setAddressMode(AddressMode.CLAMP_TO_EDGE);
            texture.setTextureFilter(FilterMode.LINEAR, false);
            surface = new Surface(texture, device.createTextureView(texture));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, surface);
        } catch (RuntimeException failure) {
            if (surface == null) texture.close();
            else surface.close();
            throw failure;
        }
        textureWidth = width;
        textureHeight = height;
        return gl.getGlId();
    }

    public void draw(DrawContext context, float left, float top, float width, float height,
                     float u1, float v1, float u2, float v2) {
        if (textureWidth == 0) return;
        int regionWidth = Math.round((u2 - u1) * textureWidth);
        int regionHeight = Math.round((v2 - v1) * textureHeight);
        if (regionWidth <= 0 || regionHeight <= 0) return;
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(left, top);
            matrices.scale(width / regionWidth, height / regionHeight);
            context.drawTexture(PIPELINE, id, 0, 0, u1 * textureWidth, v1 * textureHeight,
                regionWidth, regionHeight, textureWidth, textureHeight);
        } finally {
            matrices.popMatrix();
        }
    }

    @Override public void close() {
        if (textureWidth == 0) return;
        MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
        textureWidth = 0;
        textureHeight = 0;
    }
}
