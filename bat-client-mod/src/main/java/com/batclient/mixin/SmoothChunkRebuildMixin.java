package com.batclient.mixin;

import com.batclient.ChunkSmoothing;
import com.batclient.hud.FrameWorkBudget;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class SmoothChunkRebuildMixin {
    @Unique private FrameWorkBudget bat$budget;
    @Unique private boolean bat$enabled;
    @Inject(method="updateChunks",at=@At("HEAD"))
    private void bat$begin(Camera camera,CallbackInfo ci){
        bat$enabled=ChunkSmoothing.enabled();
        if(bat$budget==null)bat$budget=new FrameWorkBudget();
        bat$budget.begin(System.nanoTime(),ChunkSmoothing.rebuildBudget());
    }
    @Redirect(method="updateChunks",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/chunk/ChunkBuilder;rebuild(Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;Lnet/minecraft/client/render/chunk/ChunkRendererRegionBuilder;)V"))
    private void bat$rebuild(ChunkBuilder builder,ChunkBuilder.BuiltChunk chunk,ChunkRendererRegionBuilder region){
        if(!bat$enabled || chunk.needsImportantRebuild()&&bat$budget.take(System.nanoTime())){
            long start=System.nanoTime();
            try{builder.rebuild(chunk,region);}
            finally{
                long duration=System.nanoTime()-start;
                bat$budget.completed(duration);
                com.batclient.performance.BuiltinPerformance.chunkWork(duration);
            }
        }
        else chunk.scheduleRebuild(builder,region);
    }
}
