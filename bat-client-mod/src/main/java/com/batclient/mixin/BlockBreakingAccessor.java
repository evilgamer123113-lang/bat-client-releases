package com.batclient.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerInteractionManager.class)
public interface BlockBreakingAccessor {
    @Accessor("currentBreakingProgress") float bat$getBreakingProgress();
    @Accessor("currentBreakingPos") BlockPos bat$getBreakingPos();
}
