package com.batclient.mixin;

import com.batclient.platform.fabric.FabricClientHooks;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class NofapiItemTooltipMixin {
    @Inject(method = "getTooltip", at = @At("RETURN"))
    private void bat$nofapiTooltip(PlayerEntity player, TooltipContext context, CallbackInfoReturnable<List<Text>> cir) {
        FabricClientHooks.onItemTooltip((ItemStack) (Object) this, cir.getReturnValue());
    }
}
