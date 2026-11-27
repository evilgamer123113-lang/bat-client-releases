package com.batclient.mixin;

import com.batclient.ChunkSmoothing;
import com.batclient.hud.FrameWorkBudget;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class SmoothChunkRebuildMixin {
    @Unique private FrameWorkBudget bat$budget;

    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void bat$begin(long finishTimeNano, CallbackInfo ci) {
        if (bat$budget == null) bat$budget = new FrameWorkBudget();
        bat$budget.begin(System.nanoTime(), ChunkSmoothing.rebuildBudget());
    }
}
