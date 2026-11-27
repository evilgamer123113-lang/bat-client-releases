package com.batclient.mixin;

import com.batclient.performance.NearestReadyBatch;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.network.ChunkDataSender;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Integrated local connection only; vanilla transport, acknowledgements and remote policy stay owned by Minecraft. */
@Mixin(ChunkDataSender.class)
public class LocalChunkBatchMixin {
    @Shadow @Final private boolean local;
    @Shadow @Final private LongSet chunks;
    @Shadow private float pending;
    @Inject(method="makeBatch",at=@At("HEAD"),cancellable=true)
    private void bat$readyBatch(ServerChunkLoadingManager manager,ChunkPos center,CallbackInfoReturnable<List<WorldChunk>> result){
        if(!local)return;
        int limit=Math.max(1,Math.min(16,(int)Math.floor(pending)));
        if(chunks.size()<=limit)return;
        NearestReadyBatch<WorldChunk> nearest=new NearestReadyBatch<>(limit);
        var iterator=chunks.iterator();
        while(iterator.hasNext()){
            WorldChunk chunk=manager.getPostProcessedChunk(iterator.nextLong());
            if(chunk!=null)nearest.offer(chunk,center.getSquaredDistance(chunk.getPos()));
        }
        List<WorldChunk> batch=nearest.result();
        // Readiness lookup is non-blocking. Missing/still-generating chunks remain queued.
        for(WorldChunk chunk:batch)chunks.remove(chunk.getPos().toLong());
        result.setReturnValue(batch);
    }
}
