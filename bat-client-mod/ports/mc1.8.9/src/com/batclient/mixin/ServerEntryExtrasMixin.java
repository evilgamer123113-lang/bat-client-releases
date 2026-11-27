package com.batclient.mixin;

import com.batclient.ServerListExtras;
import com.batclient.ServerLocation;
import com.batclient.ServerPreferences;
import com.batclient.web.MenuBranding;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import com.batclient.platform.mc1194.DrawContext;
import com.batclient.platform.mc189.MatrixStack;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ServerEntry;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerEntry.class)
public abstract class ServerEntryExtrasMixin {
    @Shadow
    @Final
    private ServerInfo serverInfo;

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private MultiplayerScreen parent;

    @Unique
    private int bat$x, bat$y;

    @Inject(method = "render(IIIIIIIZ)V", at = @At("HEAD"))
    private void bat$before(int index, int x, int y, int width, int height, int mx, int my, boolean hovered, CallbackInfo ci) {
        bat$x = x;
        bat$y = y;
        GlStateManager.pushMatrix();
        GlStateManager.translate(20, 0, 0);
    }

    @ModifyVariable(method = "render(IIIIIIIZ)V", at = @At("HEAD"), argsOnly = true, ordinal = 5)
    private int bat$hoverOffset(int mx) {
        return mx - 20;
    }

    @ModifyVariable(method = "render(IIIIIIIZ)V", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private int bat$contentWidth(int width) {
        return width - 20;
    }

    @Inject(method = "render(IIIIIIIZ)V", at = @At("TAIL"))
    private void bat$after(int index, int x, int y, int width, int height, int mx, int my, boolean hovered, CallbackInfo ci) {
        DrawContext ctx = new DrawContext(new MatrixStack());
        ServerLocation.Info info = ServerLocation.get(serverInfo.address);
        if (info.flag() != null) {
            GlStateManager.enableBlend();
            client.getTextureManager().bindTexture(info.flag());
            DrawableHelper.drawTexture(x + 35, y + 1, 0, 0, 14, 9, 14, 9);
            GlStateManager.disableBlend();
        } else {
            DrawableHelper.fill(x + 37, y + 2, x + 38, y + 10, 0xFFAAAAAA);
            DrawableHelper.fill(x + 38, y + 2, x + 46, y + 7, 0xFF777777);
        }
        GlStateManager.popMatrix();
        MenuBranding.drawIcon(ctx, bat$x + 1, y, ServerPreferences.flag(serverInfo.address, "favorite") ? 1 : 0);
        MenuBranding.drawIcon(ctx, bat$x + 1, y + 17, ServerPreferences.flag(serverInfo.address, "collapsed") ? 3 : 2);
    }

    @Inject(method = "mouseClicked(IIIIII)Z", at = @At("HEAD"), cancellable = true)
    private void bat$click(int index, int mx, int my, int button, int relX, int relY, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || mx < bat$x || mx >= bat$x + 18 || my < bat$y || my >= bat$y + 34) return;
        ServerPreferences.toggle(serverInfo.address, my < bat$y + 16 ? "favorite" : "collapsed");
        cir.setReturnValue(true);
    }

    @ModifyVariable(method = "mouseClicked(IIIIII)Z", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int bat$clickOffset(int mx) {
        return mx - 20;
    }
}
