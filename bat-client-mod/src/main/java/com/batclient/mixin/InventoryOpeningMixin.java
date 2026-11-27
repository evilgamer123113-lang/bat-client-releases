package com.batclient.mixin;

import com.batclient.InventoryOpeningAnimation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({InventoryScreen.class,CreativeInventoryScreen.class})
public class InventoryOpeningMixin {
    @Inject(method="init",at=@At("RETURN"))
    private void bat$opened(CallbackInfo ci){InventoryOpeningAnimation.begin((Screen)(Object)this);}
    @Inject(method="render",at=@At("HEAD"))
    private void bat$render(DrawContext ctx,int mouseX,int mouseY,float delta,CallbackInfo ci){
        InventoryOpeningAnimation.drawing((Screen)(Object)this);
        ctx.getMatrices().push();ctx.getMatrices().translate(0,InventoryOpeningAnimation.drawingOffset(),0);
    }
    @ModifyVariable(method="render",at=@At("HEAD"),argsOnly=true,ordinal=1)
    private int bat$hoverY(int mouseY){return Math.round(mouseY-InventoryOpeningAnimation.offset((Screen)(Object)this));}
    @Inject(method="render",at=@At("RETURN"))
    private void bat$renderEnd(DrawContext ctx,int mouseX,int mouseY,float delta,CallbackInfo ci){
        ctx.getMatrices().pop();InventoryOpeningAnimation.finished();
    }
    @ModifyVariable(method={"mouseClicked","mouseReleased"},at=@At("HEAD"),argsOnly=true,ordinal=1)
    private double bat$inputY(double y){return y-InventoryOpeningAnimation.inputOffset();}
    // Creative overrides these; survival's inherited drag hook is handled separately.
    @ModifyVariable(method={"mouseDragged","mouseScrolled"},at=@At("HEAD"),argsOnly=true,ordinal=1,require=0)
    private double bat$creativeY(double y){return y-InventoryOpeningAnimation.inputOffset();}
}
