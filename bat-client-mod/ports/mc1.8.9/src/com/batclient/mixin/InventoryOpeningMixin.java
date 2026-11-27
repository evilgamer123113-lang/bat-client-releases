package com.batclient.mixin;

import com.batclient.InventoryOpeningAnimation;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({InventoryScreen.class, CreativeInventoryScreen.class})
public class InventoryOpeningMixin {
    @Inject(method = "init()V", at = @At("RETURN"))
    private void bat$opened(CallbackInfo ci) { InventoryOpeningAnimation.begin((Screen) (Object) this); }

    @Inject(method = "render(IIF)V", at = @At("HEAD"))
    private void bat$render(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        InventoryOpeningAnimation.drawing((Screen) (Object) this);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, InventoryOpeningAnimation.drawingOffset(), 0);
    }

    @ModifyVariable(method = "render(IIF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int bat$hoverY(int mouseY) {
        return Math.round(mouseY - InventoryOpeningAnimation.offset((Screen) (Object) this));
    }

    @Inject(method = "render(IIF)V", at = @At("RETURN"))
    private void bat$renderEnd(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        GlStateManager.popMatrix();
        InventoryOpeningAnimation.finished();
    }

    @ModifyVariable(method = {"mouseClicked(III)V", "mouseReleased(III)V"}, at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private int bat$inputY(int y) {
        return Math.round(y - InventoryOpeningAnimation.inputOffset());
    }
}
