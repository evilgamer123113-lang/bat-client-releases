package com.batclient.mixin;

import com.batclient.ServerLocation;
import com.batclient.ServerPreferences;
import com.batclient.ServerListExtras;
import com.batclient.web.MenuBranding;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import java.util.List;

@Mixin(MultiplayerServerListWidget.ServerEntry.class)
public abstract class ServerEntryExtrasMixin {
    @Shadow @Final private ServerInfo server;
    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private MultiplayerScreen screen;
    @Shadow @Final private MultiplayerServerListWidget field_19117;
    @Unique private int bat$x, bat$y;

    @Inject(method = "render", at = @At("HEAD"))
    private void bat$before(DrawContext ctx, int index, int y, int x, int width, int height, int mx, int my, boolean hovered, float delta, CallbackInfo ci) {
        bat$x = x; bat$y = y;
        ctx.getMatrices().push(); ctx.getMatrices().translate(20, 0, 0);
    }
    @ModifyVariable(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ServerInfo;getStatus()Lnet/minecraft/client/network/ServerInfo$Status;", ordinal = 0), argsOnly = true, ordinal = 5)
    private int bat$hoverOffset(int mx) { return mx - 20; }
    @ModifyVariable(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ServerInfo;getStatus()Lnet/minecraft/client/network/ServerInfo$Status;", ordinal = 0), argsOnly = true, ordinal = 3)
    private int bat$contentWidth(int width) { return width - 20; }

    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)I", ordinal = 0))
    private void bat$nameSpace(Args args) { args.set(2, (int)args.get(2) + 18); }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;wrapLines(Lnet/minecraft/text/StringVisitable;I)Ljava/util/List;"))
    private List<OrderedText> bat$motd(TextRenderer renderer, StringVisitable text, int width) {
        return ServerPreferences.flag(server.address, "collapsed") ? List.of() : renderer.wrapLines(text, width);
    }
    @Inject(method = "render", at = @At("TAIL"))
    private void bat$after(DrawContext ctx, int index, int y, int x, int width, int height, int mx, int my, boolean hovered, float delta, CallbackInfo ci) {
        var info = ServerLocation.get(server.address);
        if (info.flag() != null) {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            ctx.drawTexture(info.flag(), x + 35, y + 1, 0, 0, 14, 9, 14, 9);
            RenderSystem.disableBlend();
        } else {
            // Neutral flag placeholder while loading/unavailable; no country-code text.
            ctx.fill(x + 37, y + 2, x + 38, y + 10, 0xFFAAAAAA);
            ctx.fill(x + 38, y + 2, x + 46, y + 7, 0xFF777777);
        }
        ctx.getMatrices().pop(); mx += 20;
        // Draw controls last, above the row's background/selection decoration.
        MenuBranding.drawIcon(ctx, bat$x + 1, y, ServerPreferences.flag(server.address, "favorite") ? 1 : 0);
        MenuBranding.drawIcon(ctx, bat$x + 1, y + 17, ServerPreferences.flag(server.address, "collapsed") ? 3 : 2);
        if (mx >= bat$x && mx < bat$x + 18 && my >= y && my < y + 34) {
            String label = my < y + 16 ? (ServerPreferences.flag(server.address, "favorite") ? "Remove favorite" : "Favorite server")
                    : (ServerPreferences.flag(server.address, "collapsed") ? "Show MOTD" : "Hide MOTD");
            ctx.drawTooltip(client.textRenderer, Text.literal(label), mx, my);
        } else if (mx >= x + 55 && mx < x + 71 && my >= y && my < y + 12) {
            var lines = new java.util.ArrayList<Text>();
            lines.add(Text.literal((info.city().isEmpty() ? "" : info.city() + ", ") + info.country()));
            if (!info.isp().isEmpty()) lines.add(Text.literal("ISP: " + info.isp()));
            if (!info.code().equals("LAN") && !info.loading()) lines.add(Text.literal("Approximate hosting location"));
            ctx.drawTooltip(client.textRenderer, lines, mx, my);
        }
    }
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void bat$click(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || x < bat$x || x >= bat$x + 18 || y < bat$y || y >= bat$y + 34) return;
        ServerPreferences.toggle(server.address, y < bat$y + 16 ? "favorite" : "collapsed");
        client.send(() -> ((ServerListExtras)field_19117).bat$refresh()); cir.setReturnValue(true);
    }
    @ModifyVariable(method = "mouseClicked", at = @At("HEAD"), argsOnly = true, ordinal = 0, order = 1100)
    private double bat$clickOffset(double x) { return x - 20; }

    @Inject(method = "swapEntries", at = @At("HEAD"), cancellable = true)
    private void bat$reorder(int source, int target, CallbackInfo ci) {
        var entries = field_19117.children();
        if (target < 0 || target >= entries.size() || !(entries.get(target) instanceof MultiplayerServerListWidget.ServerEntry other)) { ci.cancel(); return; }
        int a = -1, b = -1;
        for (int i = 0; i < screen.getServerList().size(); i++) {
            if (screen.getServerList().get(i) == server) a = i;
            if (screen.getServerList().get(i) == other.getServer()) b = i;
        }
        if (a >= 0 && b >= 0) {
            screen.getServerList().swapEntries(a, b);
            screen.getServerList().saveFile();
            ((ServerListExtras)field_19117).bat$refresh(); field_19117.setSelected((MultiplayerServerListWidget.ServerEntry)(Object)this);
        }
        ci.cancel();
    }
}
