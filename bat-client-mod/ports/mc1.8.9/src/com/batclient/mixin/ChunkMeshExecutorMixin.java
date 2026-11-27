package com.batclient.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;

/** 1.8.9 uses ChunkRenderDispatcher directly; no CompletableFuture ChunkBuilder executor. */
@Mixin(MinecraftClient.class)
public class ChunkMeshExecutorMixin {
}
