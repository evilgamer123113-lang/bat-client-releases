package com.batclient.mixin;

import com.batclient.platform.InventoryNametagState;
import net.minecraft.client.gui.screen.ingame.SurvivalInventoryScreen;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SurvivalInventoryScreen.class)
public abstract class InventoryNametagMixin {
    @Inject(method = "renderEntity(IIIFFLnet/minecraft/entity/LivingEntity;)V", at = @At("HEAD"))
    private static void bat$enterInventory(int x, int y, int size, float mx, float my, LivingEntity entity, CallbackInfo ci) {
        InventoryNametagState.inInventory = true;
    }

    @Inject(method = "renderEntity(IIIFFLnet/minecraft/entity/LivingEntity;)V", at = @At("RETURN"))
    private static void bat$exitInventory(int x, int y, int size, float mx, float my, LivingEntity entity, CallbackInfo ci) {
        InventoryNametagState.inInventory = false;
    }
}
