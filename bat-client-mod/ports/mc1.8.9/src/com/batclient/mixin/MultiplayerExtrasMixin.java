package com.batclient.mixin;

import com.batclient.ServerListExtras;
import com.batclient.web.MenuBranding;
import com.batclient.platform.mc1194.DrawContext;
import com.batclient.platform.mc189.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerExtrasMixin extends Screen {
    @Shadow
    private MultiplayerServerListWidget serverListWidget;

    @Unique
    private TextFieldWidget bat$search;

    @Inject(method = "init()V", at = @At("TAIL"))
    private void bat$init(CallbackInfo ci) {
        String value = bat$search == null ? "" : bat$search.getText();
        bat$search = new TextFieldWidget(991, textRenderer, width / 2 - 100, 26, 200, 18);
        bat$search.setText(value);
        if (serverListWidget != null) {
            serverListWidget.updateBounds(width, height, 50, height - 64);
            ((ServerListExtras) serverListWidget).bat$search(value);
        }
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void bat$tick(CallbackInfo ci) {
        if (bat$search != null) {
            bat$search.tick();
        }
    }

    @Inject(method = "keyPressed(CI)V", at = @At("HEAD"), cancellable = true)
    private void bat$keyPressed(char chr, int keyCode, CallbackInfo ci) {
        if (bat$search != null && bat$search.isFocused()) {
            bat$search.keyPressed(chr, keyCode);
            if (serverListWidget != null) {
                ((ServerListExtras) serverListWidget).bat$search(bat$search.getText());
            }
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked(III)V", at = @At("HEAD"))
    private void bat$mouseClicked(int mx, int my, int button, CallbackInfo ci) {
        if (bat$search != null) {
            bat$search.mouseClicked(mx, my, button);
        }
    }

    @Inject(method = "render(IIF)V", at = @At("TAIL"))
    private void bat$brand(int mx, int my, float delta, CallbackInfo ci) {
        if (bat$search != null) {
            bat$search.render();
        }
        MenuBranding.draw(new DrawContext(new MatrixStack()));
    }

    @ModifyArgs(
        method = "render(IIF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerScreen;drawCenteredString(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V")
    )
    private void bat$titleAboveSearch(Args args) {
        args.set(3, 8);
    }
}
