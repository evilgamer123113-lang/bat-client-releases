package com.batclient.mixin;
import com.batclient.InventoryOpeningAnimation;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(HandledScreen.class)
public class InventoryDragAnimationMixin {
    @ModifyVariable(method="mouseDragged",at=@At("HEAD"),argsOnly=true,ordinal=1)
    private double bat$survivalDragY(double y){return (Object)this instanceof InventoryScreen?y-InventoryOpeningAnimation.inputOffset():y;}
}
