package net.minecraft.client;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.world.ClientWorld;
/** GPU harness only. Never packaged. */
public class MinecraftClient {
    private static final MinecraftClient INSTANCE = new MinecraftClient();
    public ClientWorld world = new ClientWorld();
    public Object player = new Object(), currentScreen;
    public boolean focused = true, paused;
    public Framebuffer target = new Framebuffer();
    public static MinecraftClient getInstance() { return INSTANCE; }
    public Framebuffer getFramebuffer() { return target; }
    public boolean isWindowFocused() { return focused; }
    public boolean isPaused() { return paused; }
}
