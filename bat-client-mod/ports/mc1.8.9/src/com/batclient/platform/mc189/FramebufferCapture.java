package com.batclient.platform.mc189;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;

/** Captures the active GL framebuffer to a PNG file on 1.8 / 1.8.9. */
public final class FramebufferCapture {
    private FramebufferCapture() {}

    public static void save(MinecraftClient client, Path file) {
        try {
            int w = Math.max(1, client.width);
            int h = Math.max(1, client.height);
            ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                int srcY = h - 1 - y;
                for (int x = 0; x < w; x++) {
                    int idx = (srcY * w + x) * 4;
                    int r = buf.get(idx) & 0xFF;
                    int g = buf.get(idx + 1) & 0xFF;
                    int b = buf.get(idx + 2) & 0xFF;
                    int a = buf.get(idx + 3) & 0xFF;
                    img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            ImageIO.write(img, "PNG", file.toFile());
        } catch (Throwable t) {
            throw new RuntimeException("Failed saving screenshot " + file, t);
        }
    }
}
