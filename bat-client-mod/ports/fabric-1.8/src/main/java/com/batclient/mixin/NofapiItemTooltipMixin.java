package com.batclient.mixin;

import com.batclient.platform.fabric.FabricClientHooks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class NofapiItemTooltipMixin {
    @Inject(method = "getTooltip", at = @At("RETURN"))
    private void bat$onItemTooltip(PlayerEntity player, boolean advanced, CallbackInfoReturnable<List<String>> cir) {
        List<String> raw = cir.getReturnValue();
        if (raw == null) return;
        List<Text> wrapped = new ArrayList<>(raw.size());
        for (String line : raw) wrapped.add(new LiteralText(line));
        int before = wrapped.size();
        FabricClientHooks.onItemTooltip((ItemStack) (Object) this, wrapped);
        for (int i = before; i < wrapped.size(); i++) {
            raw.add(wrapped.get(i).asFormattedString());
        }
    }
}
