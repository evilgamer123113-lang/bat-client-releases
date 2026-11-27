package com.batclient.mixin;

import com.batclient.platform.fabric.FabricClientHooks;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.item.TooltipData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TooltipComponent.class)
public interface NofapiTooltipComponentMixin {
    @Inject(method = "of(Lnet/minecraft/client/item/TooltipData;)Lnet/minecraft/client/gui/tooltip/TooltipComponent;", at = @At("HEAD"), cancellable = true)
    private static void bat$nofapiTooltipComponent(TooltipData data, CallbackInfoReturnable<TooltipComponent> cir) {
        TooltipComponent custom = FabricClientHooks.onTooltipComponent(data);
        if (custom != null) cir.setReturnValue(custom);
    }
}
