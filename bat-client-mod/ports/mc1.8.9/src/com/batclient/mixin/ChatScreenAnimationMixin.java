package com.batclient.mixin;

import com.batclient.ChatOpeningAnimation;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenAnimationMixin {
    @Inject(method = "init()V", at = @At("RETURN"))
    private void bat$opened(CallbackInfo ci) { ChatOpeningAnimation.begin((Screen) (Object) this); }

    @Inject(method = "render(IIF)V", at = @At("HEAD"))
    private void bat$entrance(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, ChatOpeningAnimation.offset(), 0);
    }

    @Inject(method = "render(IIF)V", at = @At("RETURN"))
    private void bat$end(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        GlStateManager.popMatrix();
    }
}
