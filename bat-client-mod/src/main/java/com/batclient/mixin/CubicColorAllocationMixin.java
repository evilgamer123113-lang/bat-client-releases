package com.batclient.mixin;

import com.batclient.performance.FastCubicSampler;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CubicSampler.class)
public class CubicColorAllocationMixin {
    @Inject(method="sampleColor",at=@At("HEAD"),cancellable=true)
    private static void bat$sample(Vec3d position,CubicSampler.RgbFetcher fetcher,CallbackInfoReturnable<Vec3d> result){
        result.setReturnValue(FastCubicSampler.sample(position,fetcher));
    }
}
