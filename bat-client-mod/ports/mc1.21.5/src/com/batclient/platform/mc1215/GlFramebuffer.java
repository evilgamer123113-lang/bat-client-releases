package com.batclient.platform.mc1215;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.texture.GlTexture;

/** Uses Minecraft's attachment-owned FBO cache instead of a removed Framebuffer field. */
public final class GlFramebuffer {
    private GlFramebuffer(){}
    public static int id(Framebuffer framebuffer){
        if(!(RenderSystem.getDevice() instanceof GlBackend backend)
            ||!(framebuffer.getColorAttachment() instanceof GlTexture color))
            throw new IllegalStateException("Bat motion blur requires the OpenGL backend");
        return color.getOrCreateFramebuffer(backend.getFramebufferManager(),framebuffer.getDepthAttachment());
    }
}
