package com.batclient.mixin;

import com.batclient.ChunkSmoothing;
import com.batclient.hud.FrameWorkBudget;
import com.google.common.util.concurrent.ListenableFutureTask;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Queue;

@Mixin(ChunkBuilder.class)
public class SmoothChunkUploadMixin {
    @Unique private boolean bat$stopped;
    @Unique private FrameWorkBudget bat$budget;
    @Unique private boolean bat$enabled;
    @Unique private long bat$started;
    @Unique private boolean bat$pending;

    @Inject(method = "stop()V", at = @At("HEAD"))
    private void bat$stopping(CallbackInfo ci) {
        bat$stopped = true;
    }

    @Inject(method = "upload(J)Z", at = @At("HEAD"))
    private void bat$begin(long finishTime, CallbackInfoReturnable<Boolean> ci) {
        bat$enabled = ChunkSmoothing.enabled() && !bat$stopped;
        bat$pending = false;
        if (bat$budget == null) bat$budget = new FrameWorkBudget();
        bat$started = System.nanoTime();
        bat$budget.begin(bat$started, ChunkSmoothing.uploadBudget());
    }

    @Inject(method = "upload(J)Z", at = @At("RETURN"))
    private void bat$end(long finishTime, CallbackInfoReturnable<Boolean> ci) {
        com.batclient.performance.BuiltinPerformance.chunkWork(System.nanoTime() - bat$started);
        com.batclient.performance.BuiltinPerformance.pendingUploads(bat$enabled && bat$pending);
    }

    @Redirect(method = "upload(J)Z", at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;"))
    private Object bat$nextUpload(Queue<?> queue) {
        if (!bat$enabled || bat$budget.take(System.nanoTime())) return queue.poll();
        bat$pending = !queue.isEmpty();
        return null;
    }

    @Redirect(method = "upload(J)Z", at = @At(value = "INVOKE", target = "Lcom/google/common/util/concurrent/ListenableFutureTask;run()V"))
    private void bat$uploadTask(ListenableFutureTask<?> task) {
        if (!bat$enabled) {
            task.run();
            return;
        }
        long start = System.nanoTime();
        try {
            task.run();
        } finally {
            bat$budget.completed(System.nanoTime() - start);
        }
    }
}
