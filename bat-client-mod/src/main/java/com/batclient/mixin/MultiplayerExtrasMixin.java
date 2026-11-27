package com.batclient.mixin;

import com.batclient.web.MenuBranding;
import com.batclient.ServerListExtras;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerExtrasMixin extends Screen {
    @Shadow protected MultiplayerServerListWidget serverListWidget;
    @Unique private TextFieldWidget bat$search;
    protected MultiplayerExtrasMixin(Text title) { super(title); }
    @Inject(method = "init", at = @At("TAIL"))
    private void bat$init(CallbackInfo ci) {
        String value = bat$search == null ? "" : bat$search.getText();
        bat$search = new TextFieldWidget(textRenderer, width / 2 - 100, 26, 200, 18, Text.literal("Search servers"));
        bat$search.setPlaceholder(Text.literal("Search servers"));
        bat$search.setText(value);
        bat$search.setChangedListener(query -> ((ServerListExtras)serverListWidget).bat$search(query));
        addDrawableChild(bat$search);
        serverListWidget.setDimensionsAndPosition(width, Math.max(36, height - 64 - 50), 0, 50);
        ((ServerListExtras)serverListWidget).bat$search(value);
    }
    @Inject(method = "render", at = @At("TAIL"))
    private void bat$brand(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) { MenuBranding.draw(ctx); }
    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"))
    private void bat$titleAboveSearch(Args args) { args.set(3, 8); }
}
