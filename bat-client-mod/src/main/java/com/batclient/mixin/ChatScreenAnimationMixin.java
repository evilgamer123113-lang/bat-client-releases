package com.batclient.mixin;

import com.batclient.ChatOpeningAnimation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenAnimationMixin {
    @Inject(method="init",at=@At("RETURN"))
    private void bat$opened(CallbackInfo ci){ChatOpeningAnimation.begin((Screen)(Object)this);}
    @Inject(method="render",at=@At("HEAD"))
    private void bat$entrance(DrawContext ctx,int mouseX,int mouseY,float delta,CallbackInfo ci){ctx.getMatrices().push();ctx.getMatrices().translate(0,ChatOpeningAnimation.offset(),0);}
    @Inject(method="render",at=@At("RETURN"))
    private void bat$end(DrawContext ctx,int mouseX,int mouseY,float delta,CallbackInfo ci){ctx.getMatrices().pop();}
}
