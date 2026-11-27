package com.batclient.mixin;
import com.batclient.CombatHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPlayNetworkHandler.class)
public class CombatDamageMixin {
    @Inject(method="onEntityDamage",at=@At("TAIL"))
    private void bat$damage(EntityDamageS2CPacket packet,CallbackInfo ci){CombatHud.damage(packet);}
}
