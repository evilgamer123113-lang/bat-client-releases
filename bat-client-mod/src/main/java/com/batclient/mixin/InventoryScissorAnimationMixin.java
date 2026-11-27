package com.batclient.mixin;
import com.batclient.InventoryOpeningAnimation;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 1.21.1 scissors ignore DrawContext matrices; move the portrait/text clip with the UI. */
@Mixin(DrawContext.class)
public class InventoryScissorAnimationMixin {
    @ModifyVariable(method="enableScissor",at=@At("HEAD"),argsOnly=true,ordinal=1)
    private int bat$clipTop(int y){return y+Math.round(InventoryOpeningAnimation.drawingOffset());}
    @ModifyVariable(method="enableScissor",at=@At("HEAD"),argsOnly=true,ordinal=3)
    private int bat$clipBottom(int y){return y+Math.round(InventoryOpeningAnimation.drawingOffset());}
}
