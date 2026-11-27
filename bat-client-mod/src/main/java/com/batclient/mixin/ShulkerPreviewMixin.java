package com.batclient.mixin;
import com.batclient.ItemPreviews;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;
@Mixin(ItemStack.class)
public abstract class ShulkerPreviewMixin {
    @Inject(method="getTooltipData",at=@At("RETURN"),cancellable=true)
    private void bat$contents(CallbackInfoReturnable<Optional<TooltipData>> cir){if(cir.getReturnValue().isEmpty()){var stack=(ItemStack)(Object)this;var data=ItemPreviews.contents(stack);if(data.isEmpty())data=ItemPreviews.comparison(stack);if(data.isPresent())cir.setReturnValue(data);}}
}
