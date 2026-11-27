package com.batclient.web;

import com.batclient.BatClientMod;
import com.batclient.ServerListExtras;
import com.batclient.platform.Platforms;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL21;

/** Opt-in development regression probe; never runs for players. */
public final class MenuRegressionChecks {
    private static boolean finished;
    private static WebWindow probe;
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void tick(MinecraftClient client) {
        if (finished || !Boolean.getBoolean("batclient.test.menuNavigation") || !Platforms.get().isDevelopmentEnvironment()) return;
        if (!(client.currentScreen instanceof WebScreen parent) || parent.window() == null || !parent.window().hasPainted()
                || !WebController.INSTANCE.isReady() || BatClientShaders.bgraPositionTextureShader == null) return;
        try {
            int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int pbo = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
            if (probe == null) {
                probe = new WebWindow(WebController.INSTANCE.getRenderer(), 272, 56);
                check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == texture, "constructor changed texture binding");
                check(GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING) == pbo, "constructor changed PBO binding");
                probe.loadUrl(WebAssetManager.webRoot().resolve("brand.html").toUri().toString());
                return;
            }
            if (probe.view().isLoading() || !probe.view().surface().dirtyBounds().isValid()) return;
            int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
            int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
            probe.renderAndUpload();
            check(probe.hasPainted(), "probe did not upload");
            check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == texture, "upload changed texture binding");
            check(GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING) == pbo, "upload changed PBO binding");
            check(GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT) == alignment, "upload changed alignment");
            check(GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH) == rowLength, "upload changed row length");
            var ctx = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
            var shader = com.mojang.blaze3d.systems.RenderSystem.getShader();
            int shaderTexture = com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(0);
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
            probe.draw(ctx, 0, 0, 8, 8);
            check(com.mojang.blaze3d.systems.RenderSystem.getShader() == shader, "draw changed shader");
            check(com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(0) == shaderTexture, "draw changed shader texture");
            check(GL11.glIsEnabled(GL11.GL_BLEND) == blend, "draw changed blend enable");
            finished = true;
            probe.close(); probe = null;
            for (int i = 0; i < 5; i++) {
                var multiplayer = new MultiplayerScreen(parent);
                client.setScreen(multiplayer);
                check(multiplayer.children().stream().filter(e -> e instanceof ButtonWidget).count() >= 7, "missing multiplayer buttons");
                var list = multiplayer.children().stream().filter(e -> e instanceof MultiplayerServerListWidget)
                    .map(e -> (MultiplayerServerListWidget)e).findFirst().orElseThrow();
                multiplayer.getServerList().add(new ServerInfo("zz-bat-menu-probe", "127.0.0.1:25565", ServerInfo.ServerType.OTHER), false);
                list.setServers(multiplayer.getServerList());
                ((ServerListExtras)list).bat$search("zz-bat-menu-probe");
                check(list.children().stream().filter(e -> e instanceof MultiplayerServerListWidget.ServerEntry).count() == 1, "search emptied native list");
                ((ServerListExtras)list).bat$search("");
                check(list.children().stream().anyMatch(e -> e instanceof MultiplayerServerListWidget.ServerEntry), "clearing search emptied list");
                multiplayer.render(ctx, 0, 0, 0);
                ctx.draw();
                multiplayer.close();
                check(client.currentScreen == parent, "Escape/back did not return directly to Bat menu");
            }
            client.setScreen(new TitleScreen());
            check(client.currentScreen instanceof WebScreen, "vanilla title survived setScreen/init");
            BatClientMod.LOGGER.info("[screen-smoke] MENU_NAVIGATION_AND_GL_STATE_OK reopen=5");
        } catch (Throwable failure) {
            finished = true;
            if (probe != null) { probe.close(); probe = null; }
            BatClientMod.LOGGER.error("[screen-smoke] Menu regression probe failed", failure);
        }
    }
}
