package com.batclient.mixin;

import com.batclient.performance.MeshWorkExecutor;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(ChunkBuilder.class)
public class ChunkMeshExecutorMixin {
    @Shadow @Final private Executor executor;
    @Unique private MeshWorkExecutor bat$mesh;
    @Inject(method="<init>",at=@At("RETURN"))
    private void bat$init(CallbackInfo ci){bat$mesh=new MeshWorkExecutor(executor);}
    @Redirect(method="scheduleRunTasks",at=@At(value="INVOKE",target="Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private <T> CompletableFuture<T> bat$compile(Supplier<T> task,Executor original){
        return CompletableFuture.supplyAsync(task,bat$mesh==null?original:bat$mesh);
    }
    @Inject(method="stop",at=@At("RETURN"))
    private void bat$stop(CallbackInfo ci){if(bat$mesh!=null)bat$mesh.close();}
}
