package com.batclient.mixin;

import com.batclient.ClientMods;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Particle Reduction (mod id "particlereduce"): probabilistically drops
 * particle spawns so the slider in the Bat Client mod menu has a real,
 * visible effect. Policy lives in ClientMods.dropParticle() (reads
 * config/batclient-hud.json, cached once per tick).
 */
@Mixin(ParticleManager.class)
public class ParticleThrottleMixin {
	@Inject(
			method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
			at = @At("HEAD"),
			cancellable = true
	)
	private void bat$throttle(ParticleEffect parameters, double x, double y, double z,
			double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<Particle> cir) {
		if (ClientMods.dropParticle()) {
			cir.setReturnValue(null);
		}
	}
}
