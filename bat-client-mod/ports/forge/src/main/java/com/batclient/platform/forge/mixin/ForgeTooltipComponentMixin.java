package com.batclient.platform.forge.mixin;

import com.batclient.platform.forge.ForgeClientHooks;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.item.tooltip.TooltipData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TooltipComponent.class)
public interface ForgeTooltipComponentMixin {
    @Inject(method="of(Lnet/minecraft/item/tooltip/TooltipData;)Lnet/minecraft/client/gui/tooltip/TooltipComponent;",at=@At("HEAD"),cancellable=true)
    private static void bat$custom(TooltipData data,CallbackInfoReturnable<TooltipComponent> result){
        TooltipComponent component=ForgeClientHooks.createTooltip(data);
        if(component!=null)result.setReturnValue(component);
    }
}
