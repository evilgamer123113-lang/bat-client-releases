package com.batclient.mixin;

import com.batclient.InventoryOpeningAnimation;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.SurvivalInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(HandledScreen.class)
public class InventoryDragAnimationMixin {
    @ModifyVariable(method = "mouseDragged(IIIJ)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int bat$survivalDragY(int y) {
        return (Object) this instanceof SurvivalInventoryScreen ? y - (int) Math.round(InventoryOpeningAnimation.inputOffset()) : y;
    }
}
