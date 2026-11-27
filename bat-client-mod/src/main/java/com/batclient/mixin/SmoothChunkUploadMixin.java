package com.batclient.mixin;

import com.batclient.ChunkSmoothing;
import com.batclient.hud.FrameWorkBudget;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Queue;

@Mixin(ChunkBuilder.class)
public class SmoothChunkUploadMixin {
    @Shadow private boolean stopped;
    @Unique private FrameWorkBudget bat$budget;
    @Unique private boolean bat$enabled;
    @Unique private long bat$started;
    @Unique private boolean bat$pending;
    @Inject(method="upload",at=@At("HEAD"))
    private void bat$begin(CallbackInfo ci){
        bat$enabled=ChunkSmoothing.enabled()&&!stopped;
        bat$pending=false;
        if(bat$budget==null)bat$budget=new FrameWorkBudget();
        bat$started=System.nanoTime();
        bat$budget.begin(bat$started,ChunkSmoothing.uploadBudget());
    }
    @Inject(method="upload",at=@At("RETURN"))
    private void bat$end(CallbackInfo ci){
        com.batclient.performance.BuiltinPerformance.chunkWork(System.nanoTime()-bat$started);
        com.batclient.performance.BuiltinPerformance.pendingUploads(bat$enabled&&bat$pending);
    }
    @Redirect(method="upload",at=@At(value="INVOKE",target="Ljava/util/Queue;poll()Ljava/lang/Object;"))
    private Object bat$nextUpload(Queue<?> queue){
        if(!bat$enabled||bat$budget.take(System.nanoTime()))return queue.poll();
        // isEmpty is constant-time for vanilla's concurrent queue; never traverse size().
        bat$pending=!queue.isEmpty();
        return null;
    }
    @Redirect(method="upload",at=@At(value="INVOKE",target="Ljava/lang/Runnable;run()V"))
    private void bat$uploadTask(Runnable task){
        if(!bat$enabled){task.run();return;}
        long start=System.nanoTime();
        try{task.run();}
        finally{bat$budget.completed(System.nanoTime()-start);}
    }
}
