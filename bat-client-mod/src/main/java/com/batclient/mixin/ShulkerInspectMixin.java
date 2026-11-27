package com.batclient.mixin;
import com.batclient.ItemPreviews;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HandledScreen.class)
public abstract class ShulkerInspectMixin {
    @Shadow protected Slot focusedSlot;
    @Inject(method="drawMouseoverTooltip",at=@At("HEAD"),cancellable=true)
    private void bat$inspect(DrawContext ctx,int x,int y,CallbackInfo ci){if(ItemPreviews.inspect(ctx,focusedSlot,x,y))ci.cancel();}
}
